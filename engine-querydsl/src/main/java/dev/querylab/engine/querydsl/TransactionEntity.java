package dev.querylab.engine.querydsl;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * This module's own copy of the JPA entity (~40 duplicated lines) rather than a
 * dependency on engine-jpa-specifications: the five engines stay siblings, not
 * a chain, so each can be read — and deleted — independently.
 *
 * <p>The explicit {@code @Entity(name = ...)} matters: the API app loads this
 * class AND the Specifications module's entity into one persistence unit, and
 * Hibernate entity names default to the simple class name — two entities named
 * "TransactionEntity" would fail bootstrap with a DuplicateMappingException.
 */
@Entity(name = "QuerydslTransactionEntity")
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String description;

    @Column
    private String counterparty;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransactionEntity() {
        // required by JPA
    }

    public Transaction toDomain() {
        return new Transaction(id, account.getId(), amount, currency, status, type,
                description, counterparty, createdAt,
                account.getRiskRating(),
                merchant == null ? null : merchant.getName(),
                merchant == null ? null : merchant.getCategory(),
                merchant == null ? null : merchant.getCountry());
    }
}
