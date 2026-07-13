package dev.querylab.engine.querydsl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** QueryDSL module's own Account entity; distinct @Entity name avoids clashing
 *  with the JPA module's Account when both load into the API's persistence unit. */
@Entity(name = "QuerydslAccount")
@Table(name = "account")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "risk_rating", nullable = false)
    private String riskRating;

    protected Account() {
    }

    public UUID getId() {
        return id;
    }

    public String getRiskRating() {
        return riskRating;
    }
}
