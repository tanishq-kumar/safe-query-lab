package dev.querylab.common.search;

import dev.querylab.common.model.Transaction;

/**
 * The single contract all five engines implement.
 *
 * <p>Contract details every implementation must honor (enforced by the
 * conformance suite in {@code query-conformance}):
 * <ul>
 *   <li>Null/empty criteria fields mean "no filter on this dimension".</li>
 *   <li>Amount bounds are inclusive; the date range is half-open:
 *       {@code createdFrom} inclusive, {@code createdTo} exclusive.</li>
 *   <li>Text search is case-insensitive containment with LIKE wildcards
 *       matched literally.</li>
 *   <li>Sorting always appends {@code id ASC} as the final tiebreak so
 *       pagination is deterministic even when the sort key has duplicates.</li>
 *   <li>{@code totalElements} is the count of all matches, independent of the
 *       requested page.</li>
 * </ul>
 */
public interface TransactionSearchPort {

    SearchResult<Transaction> search(TransactionSearchCriteria criteria);

    /** Stable engine identifier: jdbc, jpa, querydsl, jooq or mybatis. */
    String engineName();
}
