package dev.querylab.engine.jdbc;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;
import dev.querylab.common.search.LikeEscaper;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Technique 5: raw JDBC with {@link PreparedStatement} — the fundamentals every
 * other engine abstracts away. 100% {@code java.sql}; no Spring, no pooling
 * opinions, nothing between you and the driver.
 *
 * <p>The safety rules on display:
 * <ol>
 *   <li><b>Values are always bound, never concatenated.</b> The SQL string only
 *       ever grows by fixed fragments and {@code ?} placeholders; user data
 *       travels exclusively through the parameter list alongside it.</li>
 *   <li><b>Identifiers can't be bound</b> — you cannot write {@code ORDER BY ?} —
 *       so the ORDER BY clause is assembled from {@code switch}es over enums
 *       that return compile-time string constants. The concatenation below is
 *       safe precisely because no user-controlled string can reach it.</li>
 *   <li><b>LIKE patterns are data, not structure.</b> The pattern is a bound
 *       parameter with the user's {@code %/_/\} escaped ({@link LikeEscaper})
 *       and an explicit {@code ESCAPE} clause.</li>
 * </ol>
 */
public final class JdbcTransactionSearchAdapter implements TransactionSearchPort {

    // Columns are table-qualified because id/name now appear in more than one
    // joined table (t.id vs a.id vs m.id would otherwise be ambiguous). Joined
    // columns are aliased so the ResultSet labels don't collide.
    private static final String SELECT_LIST =
            "t.id, t.account_id, t.amount, t.currency, t.status, t.type, t.description, "
            + "t.counterparty, t.created_at, a.risk_rating AS account_risk_rating, "
            + "m.name AS merchant_name, m.category AS merchant_category, m.country AS merchant_country";

    // account is mandatory (INNER JOIN); merchant is optional (LEFT JOIN), so
    // merchant-less rows survive with NULL merchant columns. Neither join
    // multiplies rows (both are many-to-one to a PK), so COUNT(*) stays correct.
    private static final String FROM =
            " FROM transactions t "
            + "JOIN account a ON t.account_id = a.id "
            + "LEFT JOIN merchant m ON t.merchant_id = m.id";

    private final DataSource dataSource;

    public JdbcTransactionSearchAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public SearchResult<Transaction> search(TransactionSearchCriteria criteria) {
        WhereFragment where = buildWhere(criteria);

        // Two round-trips sharing one predicate builder. The single-query
        // alternative — SELECT ..., COUNT(*) OVER () — saves a round-trip but
        // computes the window count per row and returns it redundantly; with an
        // indexed, selective WHERE the two-query form usually wins. Worth
        // knowing both.
        String countSql = "SELECT COUNT(*)" + FROM + where.sql();
        String dataSql = "SELECT " + SELECT_LIST + FROM + where.sql()
                + orderByClause(criteria) + " LIMIT ? OFFSET ?";

        try (Connection connection = dataSource.getConnection()) {
            long total = executeCount(connection, countSql, where.params());
            List<Transaction> content = executeData(connection, dataSql, where.params(), criteria);
            return new SearchResult<>(content, total, criteria.page(), criteria.size());
        } catch (SQLException e) {
            throw new IllegalStateException("Transaction search failed", e);
        }
    }

    @Override
    public String engineName() {
        return "jdbc";
    }

    /** Exposes the generated data-query SQL for the /explain endpoint. */
    public String renderSql(TransactionSearchCriteria criteria) {
        return "SELECT " + SELECT_LIST + FROM + buildWhere(criteria).sql()
                + orderByClause(criteria) + " LIMIT ? OFFSET ?";
    }

    /** SQL fragment plus its bind values, grown in lockstep. */
    private record WhereFragment(String sql, List<Object> params) {
    }

    private static WhereFragment buildWhere(TransactionSearchCriteria criteria) {
        // Collecting predicates and joining with AND reads cleaner than the
        // classic "WHERE 1=1" trick (which exists only so every predicate can
        // start with "AND"); both are equally safe.
        List<String> predicates = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (!criteria.statuses().isEmpty()) {
            // Plain JDBC cannot bind a collection to one '?': generate exactly
            // one placeholder per element. (Postgres-specific alternative:
            // "status = ANY(?)" with Connection.createArrayOf — one placeholder,
            // stable SQL text for any set size, better statement caching.)
            StringJoiner placeholders = new StringJoiner(", ", "t.status IN (", ")");
            for (TransactionStatus status : criteria.statuses()) {
                placeholders.add("?");
                params.add(status.name());
            }
            predicates.add(placeholders.toString());
        }
        if (criteria.type() != null) {
            predicates.add("t.type = ?");
            params.add(criteria.type().name());
        }
        if (criteria.accountId() != null) {
            predicates.add("t.account_id = ?");
            params.add(criteria.accountId());
        }
        if (criteria.currency() != null) {
            predicates.add("t.currency = ?");
            params.add(criteria.currency());
        }
        if (criteria.minAmount() != null) {
            predicates.add("t.amount >= ?");
            params.add(criteria.minAmount());
        }
        if (criteria.maxAmount() != null) {
            predicates.add("t.amount <= ?");
            params.add(criteria.maxAmount());
        }
        if (criteria.createdFrom() != null) {
            predicates.add("t.created_at >= ?");
            params.add(criteria.createdFrom());
        }
        if (criteria.createdTo() != null) {
            predicates.add("t.created_at < ?"); // half-open range, per port contract
            params.add(criteria.createdTo());
        }
        if (criteria.descriptionContains() != null) {
            // The whole pattern is ONE bound value; ESCAPE makes the backslash
            // escaping explicit instead of relying on Postgres's default.
            predicates.add("t.description ILIKE ? ESCAPE '\\'");
            params.add(LikeEscaper.containsPattern(criteria.descriptionContains()));
        }
        // Join filters: these reference the joined tables. A predicate on m.*
        // over the LEFT JOIN behaves as an inner filter (rows with no merchant
        // have NULL there and fail the equality), which is the intended semantics.
        if (criteria.accountRiskRating() != null) {
            predicates.add("a.risk_rating = ?");
            params.add(criteria.accountRiskRating());
        }
        if (criteria.merchantCategory() != null) {
            predicates.add("m.category = ?");
            params.add(criteria.merchantCategory());
        }
        if (criteria.merchantCountry() != null) {
            predicates.add("m.country = ?");
            params.add(criteria.merchantCountry());
        }

        return predicates.isEmpty()
                ? new WhereFragment("", List.of())
                : new WhereFragment(" WHERE " + String.join(" AND ", predicates), List.copyOf(params));
    }

    private static String orderByClause(TransactionSearchCriteria criteria) {
        // Both operands of this concatenation come from enum switches returning
        // constants — user input cannot reach the SQL text, which is the entire
        // ORDER-BY-injection defense. See SortKey for the parse-time whitelist.
        String column = switch (criteria.sortBy()) {
            case CREATED_AT -> "t.created_at";
            case AMOUNT -> "t.amount";
            case ID -> "t.id";
        };
        String direction = switch (criteria.sortDirection()) {
            case ASC -> "ASC";
            case DESC -> "DESC";
        };
        String tiebreak = criteria.sortBy() == dev.querylab.common.search.SortKey.ID
                ? "" // id is already the primary sort; a tiebreak on it is redundant
                : ", t.id ASC";
        return " ORDER BY " + column + " " + direction + tiebreak;
    }

    private static long executeCount(Connection connection, String sql, List<Object> params)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static List<Transaction> executeData(Connection connection, String sql,
                                                 List<Object> params,
                                                 TransactionSearchCriteria criteria)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = bind(statement, params);
            statement.setInt(next, criteria.size());
            statement.setInt(next + 1, criteria.offset());
            try (ResultSet rs = statement.executeQuery()) {
                List<Transaction> content = new ArrayList<>();
                while (rs.next()) {
                    content.add(mapRow(rs));
                }
                return content;
            }
        }
    }

    /** Binds all WHERE params; returns the next free 1-based parameter index. */
    private static int bind(PreparedStatement statement, List<Object> params) throws SQLException {
        int index = 1;
        for (Object param : params) {
            switch (param) {
                case java.time.Instant instant ->
                    // JDBC 4.2 maps OffsetDateTime (not Instant) to timestamptz.
                        statement.setObject(index++, OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
                case BigDecimal decimal -> statement.setBigDecimal(index++, decimal);
                case UUID uuid -> statement.setObject(index++, uuid);
                default -> statement.setString(index++, (String) param);
            }
        }
        return index;
    }

    private static Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getObject("id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                TransactionStatus.valueOf(rs.getString("status")),
                TransactionType.valueOf(rs.getString("type")),
                rs.getString("description"),
                rs.getString("counterparty"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("account_risk_rating"),
                rs.getString("merchant_name"),      // null for merchant-less rows
                rs.getString("merchant_category"),
                rs.getString("merchant_country"));
    }
}
