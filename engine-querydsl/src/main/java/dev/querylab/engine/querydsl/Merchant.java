package dev.querylab.engine.querydsl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** QueryDSL module's own Merchant entity (optional FK → LEFT JOIN). */
@Entity(name = "QuerydslMerchant")
@Table(name = "merchant")
public class Merchant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 2)
    private String country;

    protected Merchant() {
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getCountry() {
        return country;
    }
}
