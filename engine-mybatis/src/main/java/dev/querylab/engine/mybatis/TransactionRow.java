package dev.querylab.engine.mybatis;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.model.TransactionStatus;
import dev.querylab.common.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mutable result-mapping target (MyBatis populates via setters), converted to the domain record. */
public class TransactionRow {

    private UUID id;
    private UUID accountId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String type;
    private String description;
    private String counterparty;
    private Instant createdAt;
    private String accountRiskRating;
    private String merchantName;
    private String merchantCategory;
    private String merchantCountry;

    public Transaction toDomain() {
        return new Transaction(id, accountId, amount, currency,
                TransactionStatus.valueOf(status), TransactionType.valueOf(type),
                description, counterparty, createdAt,
                accountRiskRating, merchantName, merchantCategory, merchantCountry);
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setAccountRiskRating(String accountRiskRating) {
        this.accountRiskRating = accountRiskRating;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public void setMerchantCategory(String merchantCategory) {
        this.merchantCategory = merchantCategory;
    }

    public void setMerchantCountry(String merchantCountry) {
        this.merchantCountry = merchantCountry;
    }
}
