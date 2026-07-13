package dev.querylab.common.search;

/**
 * Thrown when a raw sort parameter does not map to a whitelisted {@link SortKey}.
 * Raw sort strings never travel further than this parse step — that is the
 * whole ORDER-BY-injection defense.
 */
public class UnknownSortFieldException extends IllegalArgumentException {

    public UnknownSortFieldException(String rawValue) {
        super("Unknown sort field: '" + rawValue + "'. Allowed: " + SortKey.allowedParams());
    }
}
