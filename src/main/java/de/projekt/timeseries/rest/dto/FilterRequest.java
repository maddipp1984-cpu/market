package de.projekt.timeseries.rest.dto;

import java.util.List;

public class FilterRequest {
    private List<FilterCondition> conditions;

    public List<FilterCondition> getConditions() { return conditions; }
    public void setConditions(List<FilterCondition> conditions) { this.conditions = conditions; }
}
