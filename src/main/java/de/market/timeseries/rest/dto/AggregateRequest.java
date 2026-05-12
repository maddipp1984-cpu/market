package de.market.timeseries.rest.dto;

import de.market.timeseries.client.AggregationFunction;

import java.time.LocalDateTime;
import java.util.List;

public class AggregateRequest {

    private List<Long> tsIds;
    private LocalDateTime start;
    private LocalDateTime end;
    private String function;

    public List<Long> getTsIds() { return tsIds; }
    public void setTsIds(List<Long> tsIds) { this.tsIds = tsIds; }

    public LocalDateTime getStart() { return start; }
    public void setStart(LocalDateTime start) { this.start = start; }

    public LocalDateTime getEnd() { return end; }
    public void setEnd(LocalDateTime end) { this.end = end; }

    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }

    public AggregationFunction getFunctionOrDefault() {
        if (function == null || function.isBlank()) return AggregationFunction.SUM;
        return AggregationFunction.valueOf(function.toUpperCase());
    }
}
