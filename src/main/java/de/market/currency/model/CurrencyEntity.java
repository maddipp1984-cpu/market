package de.market.currency.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ts_currency")
public class CurrencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "currency_id")
    private Short id;

    @Column(name = "iso_code", nullable = false, unique = true, length = 3)
    private String isoCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }

    public String getIsoCode() { return isoCode; }
    public void setIsoCode(String isoCode) { this.isoCode = isoCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
