package dev.querylab.engine.jpa;

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
 * JPA mirror of the {@code transactions} table, kept strictly inside this module.
 * The domain type stays a framework-free record ({@link Transaction}); this class
 * exists because JPA needs a mutable, no-arg-constructible, identity-tracked
 * entity — three things a domain record should never be forced to become.
 */
@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    private UUID id;

    // EAGER so toDomain() can read the joined data after the query returns,
    // even with open-in-view disabled. Both are many-to-one to a PK, so they
    // become plain joins and don't interfere with pagination.
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
