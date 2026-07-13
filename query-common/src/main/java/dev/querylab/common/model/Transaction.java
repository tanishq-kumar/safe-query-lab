package dev.querylab.common.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The shared, framework-free domain type. Every engine maps its native
 * representation (JPA entity, jOOQ record, MyBatis row, ResultSet) into this
 * record, so the port contract never leaks a persistence library.
 *
 * <p>The last four components are <em>joined</em>, read-only projections, not
 * columns of the {@code transactions} table: {@code accountRiskRating} comes
 * from the mandatory {@code account} relationship (INNER JOIN), and the three
 * {@code merchant*} fields from the optional {@code merchant} relationship
 * (LEFT JOIN) — so they are null for transactions with no merchant. Every
 * engine must reproduce these projections identically, which is what makes the
 * conformance suite a real test of each technique's join story.
 *
 * @param counterparty      nullable — deliberately, so every engine has to prove
 *                          correct null handling in the conformance suite
 * @param createdAt         stored as {@code timestamptz}; always UTC-normalized
 * @param accountRiskRating from account (INNER JOIN); never null
 * @param merchantName      from merchant (LEFT JOIN); null when no merchant
 * @param merchantCategory  from merchant (LEFT JOIN); null when no merchant
 * @param merchantCountry   from merchant (LEFT JOIN); null when no merchant
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
        Instant createdAt,
        String accountRiskRating,
        String merchantName,
        String merchantCategory,
        String merchantCountry
) {
}
