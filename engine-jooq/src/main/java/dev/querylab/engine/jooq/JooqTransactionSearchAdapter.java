package dev.querylab.engine.jooq;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.SortKey;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;
import dev.querylab.engine.jooq.generated.tables.records.TransactionsRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import java.time.ZoneOffset;
import java.util.List;

import static dev.querylab.engine.jooq.generated.Tables.TRANSACTIONS;

/**
 * Technique 3: jOOQ. The generated {@code TRANSACTIONS} metamodel comes from the
 * real Flyway schema at build time, so a column rename breaks the build, not
 * production.
 *
 * <p>Two things worth noticing:
 * <ul>
 *   <li>{@link DSL#noCondition()} is the neutral element for predicate
 *       composition — unlike {@code trueCondition()} it renders nothing, so the
 *       no-filter query has no {@code 1 = 1} noise.</li>
 *   <li>The same {@link Condition} object is literally reused for the data and
 *       count queries — the cleanest count story of all five engines.</li>
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

        long total = dsl.selectCount()
                .from(TRANSACTIONS)
                .where(condition)
                .fetchOne(0, long.class);

        List<Transaction> content = dsl.selectFrom(TRANSACTIONS)
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
        return dsl.selectFrom(TRANSACTIONS)
                .where(buildCondition(criteria))
                .orderBy(orderFields(criteria))
                .limit(criteria.size())
                .offset(criteria.offset())
                .getSQL();
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

    private static Transaction toDomain(TransactionsRecord record) {
        return new Transaction(
                record.getId(),
                record.getAccountId(),
                record.getAmount(),
                record.getCurrency(),
                TransactionStatus.valueOf(record.getStatus()),
                TransactionType.valueOf(record.getType()),
                record.getDescription(),
                record.getCounterparty(),
                record.getCreatedAt().toInstant());
    }
}
