package de.market.currency.rest.dto;

public class CurrencyDto {
    private Short id;
    private String isoCode;
    private String description;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }

    public String getIsoCode() { return isoCode; }
    public void setIsoCode(String isoCode) { this.isoCode = isoCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
