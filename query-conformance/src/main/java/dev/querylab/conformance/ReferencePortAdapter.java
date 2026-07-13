package dev.querylab.conformance;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.search.SearchResult;
import dev.querylab.common.search.SortDirection;
import dev.querylab.common.search.TransactionSearchCriteria;
import dev.querylab.common.search.TransactionSearchPort;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The executable specification: an in-memory Streams implementation of the port
 * over {@link SeedData#EXPECTED}. The conformance suite compares every engine
 * against this adapter, and the adapter itself is validated by the suite's
 * absolute assertions (exact named-row membership) — so the suite was proven
 * before the first line of SQL existed.
 *
 * <p>Notable semantics encoded here:
 * <ul>
 *   <li>Text search is plain case-insensitive substring containment — which is,
 *       by definition, "LIKE with the user's wildcards treated literally".</li>
 *   <li>IDs are compared as hex strings: identical to Postgres's unsigned
 *       byte-order for uuid, unlike {@code UUID.compareTo}'s signed longs.</li>
 *   <li>Every sort appends id ASC as the final tiebreak (port contract).</li>
 * </ul>
 */
public final class ReferencePortAdapter implements TransactionSearchPort {

    private static final Comparator<Transaction> ID_ASC =
            Comparator.comparing(t -> t.id().toString());

    @Override
    public SearchResult<Transaction> search(TransactionSearchCriteria criteria) {
        List<Transaction> matches = SeedData.EXPECTED.stream()
                .filter(t -> criteria.statuses().isEmpty() || criteria.statuses().contains(t.status()))
                .filter(t -> criteria.type() == null || t.type() == criteria.type())
                .filter(t -> criteria.accountId() == null || t.accountId().equals(criteria.accountId()))
                .filter(t -> criteria.currency() == null || t.currency().equals(criteria.currency()))
                .filter(t -> criteria.minAmount() == null || t.amount().compareTo(criteria.minAmount()) >= 0)
                .filter(t -> criteria.maxAmount() == null || t.amount().compareTo(criteria.maxAmount()) <= 0)
                .filter(t -> criteria.createdFrom() == null || !t.createdAt().isBefore(criteria.createdFrom()))
                .filter(t -> criteria.createdTo() == null || t.createdAt().isBefore(criteria.createdTo()))
                .filter(t -> criteria.descriptionContains() == null || containsIgnoreCase(t.description(), criteria.descriptionContains()))
                // Join filters. accountRiskRating is never null (INNER JOIN); the
                // merchant filters use equals(), which is false for null-merchant
                // rows — exactly the LEFT-JOIN-with-a-WHERE semantics.
                .filter(t -> criteria.accountRiskRating() == null || criteria.accountRiskRating().equals(t.accountRiskRating()))
                .filter(t -> criteria.merchantCategory() == null || criteria.merchantCategory().equals(t.merchantCategory()))
                .filter(t -> criteria.merchantCountry() == null || criteria.merchantCountry().equals(t.merchantCountry()))
                .sorted(comparator(criteria))
                .toList();

        List<Transaction> page = matches.stream()
                .skip(criteria.offset())
                .limit(criteria.size())
                .toList();

        return new SearchResult<>(page, matches.size(), criteria.page(), criteria.size());
    }

    @Override
    public String engineName() {
        return "reference";
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        // ASCII-only lowering, matching ILIKE under the container's C locale.
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static Comparator<Transaction> comparator(TransactionSearchCriteria criteria) {
        Comparator<Transaction> primary = switch (criteria.sortBy()) {
            case CREATED_AT -> Comparator.comparing(Transaction::createdAt);
            case AMOUNT -> Comparator.comparing(Transaction::amount); // compareTo: scale-insensitive, like SQL
            case ID -> ID_ASC;
        };
        if (criteria.sortDirection() == SortDirection.DESC) {
            primary = primary.reversed();
        }
        return primary.thenComparing(ID_ASC);
    }
}
