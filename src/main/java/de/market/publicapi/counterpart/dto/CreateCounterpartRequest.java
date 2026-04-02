package de.market.publicapi.counterpart.dto;

public class CreateCounterpartRequest {
    private String shortName;
    private String name;

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
