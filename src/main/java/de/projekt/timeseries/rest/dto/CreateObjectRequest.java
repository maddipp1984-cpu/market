package de.projekt.timeseries.rest.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateObjectRequest {

    @NotBlank
    private String type;
    @NotBlank
    private String key;
    private String description;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
