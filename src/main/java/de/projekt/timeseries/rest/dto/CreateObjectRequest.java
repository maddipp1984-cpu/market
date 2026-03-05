package de.projekt.timeseries.rest.dto;

public class CreateObjectRequest {

    private String type;
    private String key;
    private String description;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
