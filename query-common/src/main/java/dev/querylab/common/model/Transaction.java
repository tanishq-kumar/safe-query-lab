package dev.querylab.common.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The shared, framework-free domain type. Every engine maps its native
 * representation (JPA entity, jOOQ record, MyBatis row, ResultSet) into this
 * record, so the port contract never leaks a persistence library.
 *
 * @param counterparty nullable — deliberately, so every engine has to prove
 *                     correct null handling in the conformance suite
 * @param createdAt    stored as {@code timestamptz}; always UTC-normalized
 */
public record Transaction(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        TransactionType type,
        String description,
        String counterparty,
        Instant createdAt
) {
}
