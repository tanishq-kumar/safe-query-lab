package dev.querylab.api;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.search.SearchResult;

import java.util.List;

/**
 * Response envelope. {@code tookMillis} makes engine comparison visceral in
 * live demos — it is a single wall-clock sample, NOT a benchmark; the
 * query-benchmarks module exists for real numbers.
 */
public record SearchResponse(
        String engine,
        long tookMillis,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<Transaction> content
) {

    static SearchResponse of(String engine, long tookMillis, SearchResult<Transaction> result) {
        return new SearchResponse(engine, tookMillis, result.page(), result.size(),
                result.totalElements(), result.totalPages(), result.content());
    }
}
