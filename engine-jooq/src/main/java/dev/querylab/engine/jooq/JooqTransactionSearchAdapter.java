package dev.querylab.engine.jooq;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.SortKey;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectFromStep;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import java.time.ZoneOffset;
import java.util.List;

import static dev.querylab.engine.jooq.generated.Tables.ACCOUNT;
import static dev.querylab.engine.jooq.generated.Tables.MERCHANT;
import static dev.querylab.engine.jooq.generated.Tables.TRANSACTIONS;

/**
 * Technique 3: jOOQ. The generated {@code TRANSACTIONS}, {@code ACCOUNT} and
 * {@code MERCHANT} metamodels come from the real Flyway schema at build time, so
 * a column rename breaks the build, not production.
 *
 * <p>Two things worth noticing:
 * <ul>
 *   <li>{@link DSL#noCondition()} is the neutral element for predicate
 *       composition — unlike {@code trueCondition()} it renders nothing, so the
 *       no-filter query has no {@code 1 = 1} noise.</li>
 *   <li>The same {@link Condition} object — and the same join graph — is reused
 *       for the data and count queries, so the two can never drift.</li>
 * </ul>
 */
public class JooqTransactionSearchAdapter implements TransactionSearchPort {

    private final DSLContext dsl;

    public JooqTransactionSearchAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public SearchResult<Transaction> search(TransactionSearchCriteria criteria) {
        Condition condition = buildCondition(criteria);

        long total = joined(dsl.selectCount())
                .where(condition)
                .fetchOne(0, long.class);

        List<Transaction> content = joined(dsl.select(PROJECTION))
                .where(condition)
                .orderBy(orderFields(criteria))
                .limit(criteria.size())
                .offset(criteria.offset())
                .fetch(JooqTransactionSearchAdapter::toDomain);

        return new SearchResult<>(content, total, criteria.page(), criteria.size());
    }

    @Override
    public String engineName() {
        return "jooq";
    }

    /** Renders the data query's SQL without executing it — the /explain endpoint. */
    public String renderSql(TransactionSearchCriteria criteria) {
        return joined(dsl.select(PROJECTION))
                .where(buildCondition(criteria))
                .orderBy(orderFields(criteria))
                .limit(criteria.size())
                .offset(criteria.offset())
                .getSQL();
    }

    // The columns projected into the domain record: transaction columns plus the
    // joined account.risk_rating and the three (nullable) merchant columns.
    private static final List<org.jooq.Field<?>> PROJECTION = List.of(
            TRANSACTIONS.ID, TRANSACTIONS.ACCOUNT_ID, TRANSACTIONS.AMOUNT, TRANSACTIONS.CURRENCY,
            TRANSACTIONS.STATUS, TRANSACTIONS.TYPE, TRANSACTIONS.DESCRIPTION, TRANSACTIONS.COUNTERPARTY,
            TRANSACTIONS.CREATED_AT, ACCOUNT.RISK_RATING, MERCHANT.NAME, MERCHANT.CATEGORY, MERCHANT.COUNTRY);

    /** Attaches the account (INNER) and merchant (LEFT) joins to any select step. */
    private static <R extends Record> SelectJoinStep<R> joined(SelectFromStep<R> select) {
        return select
                .from(TRANSACTIONS)
                .join(ACCOUNT).on(TRANSACTIONS.ACCOUNT_ID.eq(ACCOUNT.ID))
                .leftJoin(MERCHANT).on(TRANSACTIONS.MERCHANT_ID.eq(MERCHANT.ID));
    }

    private static Condition buildCondition(TransactionSearchCriteria criteria) {
        Condition condition = DSL.noCondition();
        if (!criteria.statuses().isEmpty()) {
            condition = condition.and(TRANSACTIONS.STATUS.in(
                    criteria.statuses().stream().map(Enum::name).toList()));
        }
        if (criteria.type() != null) {
            condition = condition.and(TRANSACTIONS.TYPE.eq(criteria.type().name()));
        }
        if (criteria.accountId() != null) {
            condition = condition.and(TRANSACTIONS.ACCOUNT_ID.eq(criteria.accountId()));
        }
        if (criteria.currency() != null) {
            condition = condition.and(TRANSACTIONS.CURRENCY.eq(criteria.currency()));
        }
        if (criteria.minAmount() != null) {
            condition = condition.and(TRANSACTIONS.AMOUNT.ge(criteria.minAmount()));
        }
        if (criteria.maxAmount() != null) {
            condition = condition.and(TRANSACTIONS.AMOUNT.le(criteria.maxAmount()));
        }
        if (criteria.createdFrom() != null) {
            condition = condition.and(TRANSACTIONS.CREATED_AT.ge(
                    criteria.createdFrom().atOffset(ZoneOffset.UTC)));
        }
        if (criteria.createdTo() != null) {
            condition = condition.and(TRANSACTIONS.CREATED_AT.lt(
                    criteria.createdTo().atOffset(ZoneOffset.UTC)));
        }
        if (criteria.descriptionContains() != null) {
            // The one engine where LikeEscaper is NOT needed: jOOQ's contains*
            // escapes %/_ internally (with '!'). The conformance wildcard tests
            // prove this path and the manual-escape paths are equivalent.
            condition = condition.and(
                    TRANSACTIONS.DESCRIPTION.containsIgnoreCase(criteria.descriptionContains()));
        }
        // Join filters over the joined tables. A predicate on MERCHANT.* turns
        // the LEFT JOIN into an effective inner filter (null merchant fails eq).
        if (criteria.accountRiskRating() != null) {
            condition = condition.and(ACCOUNT.RISK_RATING.eq(criteria.accountRiskRating()));
        }
        if (criteria.merchantCategory() != null) {
            condition = condition.and(MERCHANT.CATEGORY.eq(criteria.merchantCategory()));
        }
        if (criteria.merchantCountry() != null) {
            condition = condition.and(MERCHANT.COUNTRY.eq(criteria.merchantCountry()));
        }
        return condition;
    }

    private static List<SortField<?>> orderFields(TransactionSearchCriteria criteria) {
        boolean asc = switch (criteria.sortDirection()) {
            case ASC -> true;
            case DESC -> false;
        };
        SortField<?> primary = switch (criteria.sortBy()) {
            case CREATED_AT -> asc ? TRANSACTIONS.CREATED_AT.asc() : TRANSACTIONS.CREATED_AT.desc();
            case AMOUNT -> asc ? TRANSACTIONS.AMOUNT.asc() : TRANSACTIONS.AMOUNT.desc();
            case ID -> asc ? TRANSACTIONS.ID.asc() : TRANSACTIONS.ID.desc();
        };
        return criteria.sortBy() == SortKey.ID
                ? List.of(primary)
                : List.of(primary, TRANSACTIONS.ID.asc());
    }

    private static Transaction toDomain(Record record) {
        return new Transaction(
                record.get(TRANSACTIONS.ID),
                record.get(TRANSACTIONS.ACCOUNT_ID),
                record.get(TRANSACTIONS.AMOUNT),
                record.get(TRANSACTIONS.CURRENCY),
                TransactionStatus.valueOf(record.get(TRANSACTIONS.STATUS)),
                TransactionType.valueOf(record.get(TRANSACTIONS.TYPE)),
                record.get(TRANSACTIONS.DESCRIPTION),
                record.get(TRANSACTIONS.COUNTERPARTY),
                record.get(TRANSACTIONS.CREATED_AT).toInstant(),
                record.get(ACCOUNT.RISK_RATING),
                record.get(MERCHANT.NAME),      // null for merchant-less rows
                record.get(MERCHANT.CATEGORY),
                record.get(MERCHANT.COUNTRY));
    }
}
