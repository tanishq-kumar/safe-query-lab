package dev.querylab.common.search;

import java.util.Arrays;
import java.util.List;

/**
 * The sort whitelist, made type-safe. Because sorting reaches the engines only
 * as this enum, and each engine switches over it to a compile-time-constant
 * column reference, ORDER BY injection is unrepresentable — not merely validated.
 */
public enum SortKey {
    CREATED_AT("createdAt"),
    AMOUNT("amount"),
    ID("id");

    private final String param;

    SortKey(String param) {
        this.param = param;
    }

    /** The public API parameter spelling (camelCase). */
    public String param() {
        return param;
    }

    /**
     * Parses a raw request parameter. Accepts the camelCase form ({@code createdAt})
     * and the snake_case column form ({@code created_at}), case-insensitively.
     *
     * @throws UnknownSortFieldException for anything not on the whitelist
     */
    public static SortKey fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return CREATED_AT;
        }
        String normalized = raw.replace("_", "").trim();
        for (SortKey key : values()) {
            if (key.param.replace("_", "").equalsIgnoreCase(normalized)) {
                return key;
            }
        }
        throw new UnknownSortFieldException(raw);
    }

    public static List<String> allowedParams() {
        return Arrays.stream(values()).map(SortKey::param).toList();
    }
}
