package dev.querylab.common.search;

import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The dynamic-filter contract: every field is optional (null or empty set means
 * "no filter"), and any combination may be present. This one object is what all
 * five engines turn into a WHERE clause.
 *
 * <p>Range semantics: {@code minAmount}/{@code maxAmount} are inclusive;
 * {@code createdFrom} is inclusive and {@code createdTo} exclusive (half-open,
 * so adjacent ranges tile without overlap — the idiomatic time-range contract).
 *
 * <p>The last three filters cross relationships: {@code accountRiskRating}
 * filters on the joined {@code account}, and {@code merchantCategory}/
 * {@code merchantCountry} on the joined {@code merchant}. A merchant filter
 * naturally excludes transactions with no merchant (the join has no match).
 *
 * @param descriptionContains case-insensitive substring; LIKE wildcards in the
 *                            user's text must match literally (see {@link LikeEscaper})
 * @param accountRiskRating   exact match on account.risk_rating (join filter)
 * @param merchantCategory    exact match on merchant.category (join filter)
 * @param merchantCountry     exact match on merchant.country (join filter)
 */
public record TransactionSearchCriteria(
        Set<TransactionStatus> statuses,
        TransactionType type,
        UUID accountId,
        String currency,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Instant createdFrom,
        Instant createdTo,
        String descriptionContains,
        String accountRiskRating,
        String merchantCategory,
        String merchantCountry,
        int page,
        int size,
        SortKey sortBy,
        SortDirection sortDirection
) {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 50;

    public TransactionSearchCriteria {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        currency = blankToNull(currency);
        descriptionContains = blankToNull(descriptionContains);
        accountRiskRating = blankToNull(accountRiskRating);
        merchantCategory = blankToNull(merchantCategory);
        merchantCountry = blankToNull(merchantCountry);
        sortBy = sortBy == null ? SortKey.CREATED_AT : sortBy;
        sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;

        if (page < 0) {
            throw new InvalidCriteriaException("page must be >= 0, got " + page);
        }
        size = Math.clamp(size, 1, MAX_PAGE_SIZE);
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new InvalidCriteriaException(
                    "minAmount " + minAmount + " is greater than maxAmount " + maxAmount);
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidCriteriaException(
                    "createdFrom " + createdFrom + " is after createdTo " + createdTo);
        }
    }

    /** Row offset for OFFSET-style pagination; every engine computes it identically. */
    public int offset() {
        return page * size;
    }

    /** No filters, first page, default sort — matches everything. */
    public static TransactionSearchCriteria unfiltered() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Hand-rolled builder: 13 mostly-optional components make positional construction unreadable. */
    public static final class Builder {
        private Set<TransactionStatus> statuses;
        private TransactionType type;
        private UUID accountId;
        private String currency;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private Instant createdFrom;
        private Instant createdTo;
        private String descriptionContains;
        private String accountRiskRating;
        private String merchantCategory;
        private String merchantCountry;
        private int page = 0;
        private int size = DEFAULT_PAGE_SIZE;
        private SortKey sortBy = SortKey.CREATED_AT;
        private SortDirection sortDirection = SortDirection.DESC;

        private Builder() {
        }

        public Builder statuses(Set<TransactionStatus> statuses) {
            this.statuses = statuses;
            return this;
        }

        public Builder statuses(TransactionStatus... statuses) {
            this.statuses = Set.of(statuses);
            return this;
        }

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder accountId(UUID accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder minAmount(BigDecimal minAmount) {
            this.minAmount = minAmount;
            return this;
        }

        public Builder maxAmount(BigDecimal maxAmount) {
            this.maxAmount = maxAmount;
            return this;
        }

        public Builder createdFrom(Instant createdFrom) {
            this.createdFrom = createdFrom;
            return this;
        }

        public Builder createdTo(Instant createdTo) {
            this.createdTo = createdTo;
            return this;
        }

        public Builder descriptionContains(String descriptionContains) {
            this.descriptionContains = descriptionContains;
            return this;
        }

        public Builder accountRiskRating(String accountRiskRating) {
            this.accountRiskRating = accountRiskRating;
            return this;
        }

        public Builder merchantCategory(String merchantCategory) {
            this.merchantCategory = merchantCategory;
            return this;
        }

        public Builder merchantCountry(String merchantCountry) {
            this.merchantCountry = merchantCountry;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder sortBy(SortKey sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        public Builder sortDirection(SortDirection sortDirection) {
            this.sortDirection = sortDirection;
            return this;
        }

        public TransactionSearchCriteria build() {
            return new TransactionSearchCriteria(statuses, type, accountId, currency,
                    minAmount, maxAmount, createdFrom, createdTo, descriptionContains,
                    accountRiskRating, merchantCategory, merchantCountry,
                    page, size, sortBy, sortDirection);
        }
    }
}
