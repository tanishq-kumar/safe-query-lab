package dev.querylab.engine.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.querylab.common.model.Transaction;
import dev.querylab.common.search.LikeEscaper;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.SortKey;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;

import java.util.List;

/**
 * Technique 2: QueryDSL over JPA, using the raw {@link JPAQueryFactory} rather
 * than Spring Data's {@code QuerydslPredicateExecutor} so the technique itself
 * is visible. Predicates accumulate in a {@link BooleanBuilder}; the generated
 * {@code QTransactionEntity} makes every path and comparison compile-checked.
 *
 * <p>The count query is deliberately explicit: QueryDSL 5's
 * {@code fetchResults()}/{@code fetchCount()} are deprecated because they
 * naively wrap arbitrary queries in {@code count(*)} and silently break with
 * {@code groupBy}/{@code having}. Two explicit queries is the supported idiom.
 */
public class QuerydslTransactionSearchAdapter implements TransactionSearchPort {

    private static final QTransactionEntity tx = QTransactionEntity.transactionEntity;

    private final JPAQueryFactory queryFactory;

    public QuerydslTransactionSearchAdapter(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public SearchResult<Transaction> search(TransactionSearchCriteria criteria) {
        BooleanBuilder where = buildPredicate(criteria);

        Long total = queryFactory.select(tx.count())
                .from(tx)
                .where(where)
                .fetchOne();

        List<Transaction> content = queryFactory.selectFrom(tx)
                .where(where)
                .orderBy(orderSpecifiers(criteria))
                .offset(criteria.offset())
                .limit(criteria.size())
                .fetch()
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();

        return new SearchResult<>(content, total == null ? 0 : total,
                criteria.page(), criteria.size());
    }

    @Override
    public String engineName() {
        return "querydsl";
    }

    private static BooleanBuilder buildPredicate(TransactionSearchCriteria criteria) {
        // BooleanBuilder is a mutable conjunction accumulator; with no clauses it
        // renders nothing — the QueryDSL analogue of jOOQ's DSL.noCondition().
        BooleanBuilder where = new BooleanBuilder();
        if (!criteria.statuses().isEmpty()) {
            where.and(tx.status.in(criteria.statuses()));
        }
        if (criteria.type() != null) {
            where.and(tx.type.eq(criteria.type()));
        }
        if (criteria.accountId() != null) {
            where.and(tx.accountId.eq(criteria.accountId()));
        }
        if (criteria.currency() != null) {
            where.and(tx.currency.eq(criteria.currency()));
        }
        if (criteria.minAmount() != null) {
            where.and(tx.amount.goe(criteria.minAmount()));
        }
        if (criteria.maxAmount() != null) {
            where.and(tx.amount.loe(criteria.maxAmount()));
        }
        if (criteria.createdFrom() != null) {
            where.and(tx.createdAt.goe(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            where.and(tx.createdAt.lt(criteria.createdTo())); // half-open range
        }
        if (criteria.descriptionContains() != null) {
            // NOT containsIgnoreCase(): QueryDSL does not escape LIKE wildcards
            // (unlike jOOQ), so user text goes through LikeEscaper and the
            // escape char is passed explicitly.
            where.and(tx.description.likeIgnoreCase(
                    LikeEscaper.containsPattern(criteria.descriptionContains()),
                    LikeEscaper.ESCAPE_CHAR));
        }
        return where;
    }

    private static OrderSpecifier<?>[] orderSpecifiers(TransactionSearchCriteria criteria) {
        boolean asc = switch (criteria.sortDirection()) {
            case ASC -> true;
            case DESC -> false;
        };
        OrderSpecifier<?> primary = switch (criteria.sortBy()) {
            case CREATED_AT -> asc ? tx.createdAt.asc() : tx.createdAt.desc();
            case AMOUNT -> asc ? tx.amount.asc() : tx.amount.desc();
            case ID -> asc ? tx.id.asc() : tx.id.desc();
        };
        return criteria.sortBy() == SortKey.ID
                ? new OrderSpecifier<?>[]{primary}
                : new OrderSpecifier<?>[]{primary, tx.id.asc()};
    }
}
