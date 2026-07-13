package dev.querylab.engine.mybatis;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.search.LikeEscaper;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.SortKey;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;
import org.mybatis.dynamic.sql.SortSpecification;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.where.WhereApplier;

import java.util.List;
import java.util.Locale;

import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.accountId;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.amount;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.counterparty;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.createdAt;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.currency;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.description;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.id;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.status;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.transactions;
import static dev.querylab.engine.mybatis.TransactionDynamicSqlSupport.type;
import static org.mybatis.dynamic.sql.SqlBuilder.countFrom;
import static org.mybatis.dynamic.sql.SqlBuilder.isGreaterThanOrEqualToWhenPresent;
import static org.mybatis.dynamic.sql.SqlBuilder.isInWhenPresent;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanOrEqualToWhenPresent;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanWhenPresent;
import static org.mybatis.dynamic.sql.SqlBuilder.isLikeWhenPresent;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualToWhenPresent;
import static org.mybatis.dynamic.sql.SqlBuilder.lower;
import static org.mybatis.dynamic.sql.SqlBuilder.select;

/**
 * Technique 4: MyBatis Dynamic SQL. The {@code ...WhenPresent} condition family
 * is the whole value proposition — a condition whose value is null simply does
 * not render, so the dynamic WHERE clause needs no if-statements at all.
 *
 * <p>The shared {@link WhereApplier} is this library's answer to "the count
 * query must use exactly the same predicates": one lambda applied to both the
 * select and the count builders.
 */
public class MybatisTransactionSearchAdapter implements TransactionSearchPort {

    private final TransactionMapper mapper;

    public MybatisTransactionSearchAdapter(TransactionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SearchResult<Transaction> search(TransactionSearchCriteria criteria) {
        WhereApplier filters = filters(criteria);

        SelectStatementProvider countStatement = countFrom(transactions)
                .applyWhere(filters)
                // With every condition being ...WhenPresent, empty criteria render
                // no WHERE at all — and the library then throws by default, forcing
                // an explicit opt-in to full-table statements. A safety feature none
                // of the other four engines has.
                .configureStatement(c -> c.setNonRenderingWhereClauseAllowed(true))
                .build()
                .render(RenderingStrategies.MYBATIS3);
        long total = mapper.count(countStatement);

        List<Transaction> content = mapper.selectMany(selectStatement(criteria, filters)).stream()
                .map(TransactionRow::toDomain)
                .toList();

        return new SearchResult<>(content, total, criteria.page(), criteria.size());
    }

    @Override
    public String engineName() {
        return "mybatis";
    }

    /** Renders the data query without executing it — the /explain endpoint. */
    public String renderSql(TransactionSearchCriteria criteria) {
        return selectStatement(criteria, filters(criteria)).getSelectStatement();
    }

    private SelectStatementProvider selectStatement(TransactionSearchCriteria criteria,
                                                    WhereApplier filters) {
        return select(id, accountId, amount, currency, status, type, description,
                counterparty, createdAt)
                .from(transactions)
                .applyWhere(filters)
                .configureStatement(c -> c.setNonRenderingWhereClauseAllowed(true))
                .orderBy(orderBy(criteria))
                .limit(criteria.size())
                .offset(criteria.offset())
                .build()
                .render(RenderingStrategies.MYBATIS3);
    }

    private static WhereApplier filters(TransactionSearchCriteria criteria) {
        List<String> statusNames = criteria.statuses().isEmpty()
                ? null
                : criteria.statuses().stream().map(TransactionStatus::name).toList();
        // lower(col) LIKE lower-cased pattern instead of the library's
        // isLikeCaseInsensitive, which upper-cases the value in Java — Java and
        // Postgres disagree about non-ASCII case, and lower() matches what the
        // other engines render. The pattern is pre-escaped; Postgres's default
        // LIKE escape is already backslash, so no ESCAPE clause is needed.
        String pattern = criteria.descriptionContains() == null
                ? null
                : LikeEscaper.containsPattern(criteria.descriptionContains()).toLowerCase(Locale.ROOT);

        return where -> where
                .and(status, isInWhenPresent(statusNames))
                .and(type, isEqualToWhenPresent(criteria.type() == null ? null : criteria.type().name()))
                .and(accountId, isEqualToWhenPresent(criteria.accountId()))
                .and(currency, isEqualToWhenPresent(criteria.currency()))
                .and(amount, isGreaterThanOrEqualToWhenPresent(criteria.minAmount()))
                .and(amount, isLessThanOrEqualToWhenPresent(criteria.maxAmount()))
                .and(createdAt, isGreaterThanOrEqualToWhenPresent(criteria.createdFrom()))
                .and(createdAt, isLessThanWhenPresent(criteria.createdTo())) // half-open range
                .and(lower(description), isLikeWhenPresent(pattern));
    }

    private static List<SortSpecification> orderBy(TransactionSearchCriteria criteria) {
        SqlColumn<?> column = switch (criteria.sortBy()) {
            case CREATED_AT -> createdAt;
            case AMOUNT -> amount;
            case ID -> id;
        };
        SortSpecification primary = switch (criteria.sortDirection()) {
            case ASC -> column;
            case DESC -> column.descending();
        };
        return criteria.sortBy() == SortKey.ID
                ? List.of(primary)
                : List.of(primary, id);
    }
}
