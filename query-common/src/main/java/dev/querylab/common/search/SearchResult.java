package dev.querylab.common.search;

import java.util.List;

/**
 * A page of results plus the total match count. Deliberately not Spring Data's
 * {@code Page}: the contract module stays framework-free, and only one of the
 * five engines even has Spring Data on its classpath.
 */
public record SearchResult<T>(List<T> content, long totalElements, int page, int size) {

    public SearchResult {
        content = List.copyOf(content);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
