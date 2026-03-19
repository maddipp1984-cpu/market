package de.market.timeseries.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AggregateRequest {

    private List<Long> tsIds;
    private LocalDateTime start;
    private LocalDateTime end;

    public List<Long> getTsIds() { return tsIds; }
    public void setTsIds(List<Long> tsIds) { this.tsIds = tsIds; }

    public LocalDateTime getStart() { return start; }
    public void setStart(LocalDateTime start) { this.start = start; }

    public LocalDateTime getEnd() { return end; }
    public void setEnd(LocalDateTime end) { this.end = end; }
}
