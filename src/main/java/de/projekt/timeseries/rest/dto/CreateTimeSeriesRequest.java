package de.projekt.timeseries.rest.dto;

public class CreateTimeSeriesRequest {

    private String key;
    private String dimension;
    private String unit;
    private String currency;
    private String description;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
