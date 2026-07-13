package dev.querylab.engine.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** The optional {@code merchant} side of a transaction (nullable FK → LEFT JOIN). */
@Entity(name = "JpaMerchant")
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
        // required by JPA
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
