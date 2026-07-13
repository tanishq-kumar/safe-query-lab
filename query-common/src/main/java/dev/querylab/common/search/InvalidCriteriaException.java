package dev.querylab.common.search;

/** Thrown when a criteria combination is contradictory (e.g. minAmount &gt; maxAmount). */
public class InvalidCriteriaException extends IllegalArgumentException {

    public InvalidCriteriaException(String message) {
        super(message);
    }
}
