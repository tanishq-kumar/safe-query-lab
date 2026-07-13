package dev.querylab.api;

import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;
import dev.querylab.common.search.SortDirection;
import dev.querylab.common.search.SortKey;
import dev.querylab.common.search.TransactionSearchCriteria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Query-string shape of the search API, bound by Spring's constructor binding.
 * Everything is optional; conversion to the domain criteria funnels the raw
 * {@code sortBy}/{@code sortDir} strings through the whitelist parsers — the
 * single choke point where unvalidated sort input dies.
 *
 * @param q free-text search over description (case-insensitive, literal wildcards)
 */
public record SearchRequest(
        Set<TransactionStatus> status,
        TransactionType type,
        UUID accountId,
        String currency,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Instant createdFrom,
        Instant createdTo,
        String q,
        Integer page,
        Integer size,
        String sortBy,
        String sortDir
) {

    public TransactionSearchCriteria toCriteria() {
        return TransactionSearchCriteria.builder()
                .statuses(status)
                .type(type)
                .accountId(accountId)
                .currency(currency)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .descriptionContains(q)
                .page(page == null ? 0 : page)
                .size(size == null ? TransactionSearchCriteria.DEFAULT_PAGE_SIZE : size)
                .sortBy(SortKey.fromParam(sortBy))
                .sortDirection(SortDirection.fromParam(sortDir))
                .build();
    }
}
