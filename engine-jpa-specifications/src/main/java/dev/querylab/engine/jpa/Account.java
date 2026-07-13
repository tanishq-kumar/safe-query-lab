package dev.querylab.engine.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * The mandatory {@code account} side of a transaction. The explicit entity name
 * avoids a clash with the QueryDSL module's own Account entity when both engines
 * load into one persistence unit in the API app (Hibernate keys entities by
 * simple class name unless told otherwise).
 */
@Entity(name = "JpaAccount")
@Table(name = "account")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "risk_rating", nullable = false)
    private String riskRating;

    protected Account() {
        // required by JPA
    }

    public UUID getId() {
        return id;
    }

    public String getRiskRating() {
        return riskRating;
    }
}
