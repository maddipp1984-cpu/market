package de.projekt.timeseries.rest.dto;

import de.projekt.timeseries.model.TimeSeriesHeader;

import java.time.OffsetDateTime;

public class TimeSeriesHeaderResponse {

    private long tsId;
    private String tsKey;
    private String dimension;
    private String unit;
    private String currency;
    private Long objectId;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static TimeSeriesHeaderResponse from(TimeSeriesHeader h) {
        TimeSeriesHeaderResponse r = new TimeSeriesHeaderResponse();
        r.tsId = h.getTsId();
        r.tsKey = h.getTsKey();
        r.dimension = h.getTimeDimension().name();
        r.unit = h.getUnit().name();
        r.currency = h.getCurrency() != null ? h.getCurrency().name() : null;
        r.objectId = h.getObjectId();
        r.description = h.getDescription();
        r.createdAt = h.getCreatedAt();
        r.updatedAt = h.getUpdatedAt();
        return r;
    }

    public long getTsId() { return tsId; }
    public String getTsKey() { return tsKey; }
    public String getDimension() { return dimension; }
    public String getUnit() { return unit; }
    public String getCurrency() { return currency; }
    public Long getObjectId() { return objectId; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
