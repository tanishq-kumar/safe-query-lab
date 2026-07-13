package dev.querylab.engine.jpa;

import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;
import dev.querylab.common.search.LikeEscaper;
import dev.querylab.common.search.TransactionSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static dev.querylab.engine.jpa.TransactionColumns.AMOUNT;
import static dev.querylab.engine.jpa.TransactionColumns.CREATED_AT;
import static dev.querylab.engine.jpa.TransactionColumns.CURRENCY;
import static dev.querylab.engine.jpa.TransactionColumns.DESCRIPTION;
import static dev.querylab.engine.jpa.TransactionColumns.STATUS;
import static dev.querylab.engine.jpa.TransactionColumns.TYPE;

/**
 * One {@link Specification} per filter; absent filters return {@code null},
 * which {@link Specification#allOf} treats as "matches everything". The
 * composition is where this technique shines: specifications are first-class
 * values you can unit-test, reuse and combine — the dynamic WHERE clause is
 * just {@code allOf(...)} over whatever happens to be present.
 */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<TransactionEntity> fromCriteria(TransactionSearchCriteria criteria) {
        return Specification.allOf(
                statusIn(criteria.statuses()),
                typeIs(criteria.type()),
                accountIs(criteria.accountId()),
                currencyIs(criteria.currency()),
                amountAtLeast(criteria.minAmount()),
                amountAtMost(criteria.maxAmount()),
                createdAtOrAfter(criteria.createdFrom()),
                createdBefore(criteria.createdTo()),
                descriptionContains(criteria.descriptionContains()),
                accountRiskRatingIs(criteria.accountRiskRating()),
                merchantCategoryIs(criteria.merchantCategory()),
                merchantCountryIs(criteria.merchantCountry()));
    }

    private static Specification<TransactionEntity> statusIn(Set<TransactionStatus> statuses) {
        return statuses.isEmpty() ? null
                : (root, query, cb) -> root.get(STATUS).in(statuses);
    }

    private static Specification<TransactionEntity> typeIs(TransactionType type) {
        return type == null ? null
                : (root, query, cb) -> cb.equal(root.get(TYPE), type);
    }

    private static Specification<TransactionEntity> accountIs(UUID accountId) {
        // Traverses the account association: root.get("account").get("id").
        return accountId == null ? null
                : (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
    }

    private static Specification<TransactionEntity> currencyIs(String currency) {
        return currency == null ? null
                : (root, query, cb) -> cb.equal(root.get(CURRENCY), currency);
    }

    private static Specification<TransactionEntity> amountAtLeast(BigDecimal min) {
        return min == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(AMOUNT), min);
    }

    private static Specification<TransactionEntity> amountAtMost(BigDecimal max) {
        return max == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get(AMOUNT), max);
    }

    private static Specification<TransactionEntity> createdAtOrAfter(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(CREATED_AT), from);
    }

    private static Specification<TransactionEntity> createdBefore(Instant to) {
        return to == null ? null // exclusive: half-open range per the port contract
                : (root, query, cb) -> cb.lessThan(root.get(CREATED_AT), to);
    }

    private static Specification<TransactionEntity> descriptionContains(String text) {
        if (text == null) {
            return null;
        }
        // The Criteria API has no ILIKE, so lower() both sides. The pattern is a
        // bound literal with user wildcards escaped; the escape char is the third
        // argument — the Criteria API's equivalent of "ESCAPE '\'".
        String pattern = LikeEscaper.containsPattern(text).toLowerCase(Locale.ROOT);
        return (root, query, cb) ->
                cb.like(cb.lower(root.get(DESCRIPTION)), pattern, LikeEscaper.ESCAPE_CHAR);
    }

    private static Specification<TransactionEntity> accountRiskRatingIs(String riskRating) {
        // Filters across the (mandatory) account join.
        return riskRating == null ? null
                : (root, query, cb) -> cb.equal(root.get("account").get("riskRating"), riskRating);
    }

    private static Specification<TransactionEntity> merchantCategoryIs(String category) {
        // root.get("merchant") is an inner join, so this also excludes rows with
        // no merchant — the intended positive-filter semantics.
        return category == null ? null
                : (root, query, cb) -> cb.equal(root.get("merchant").get("category"), category);
    }

    private static Specification<TransactionEntity> merchantCountryIs(String country) {
        return country == null ? null
                : (root, query, cb) -> cb.equal(root.get("merchant").get("country"), country);
    }
}
