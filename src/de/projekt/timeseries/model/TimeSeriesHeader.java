package de.projekt.timeseries.model;

import java.time.OffsetDateTime;

public class TimeSeriesHeader {

    private long tsId;
    private String tsKey;
    private TimeDimension timeDimension;
    private Unit unit;
    private Currency currency;
    private String description;
    private Long objectId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public TimeSeriesHeader() {
    }

    public TimeSeriesHeader(String tsKey, TimeDimension timeDimension, Unit unit) {
        this.tsKey = tsKey;
        this.timeDimension = timeDimension;
        this.unit = unit;
    }

    public TimeSeriesHeader(String tsKey, TimeDimension timeDimension, Unit unit, Currency currency) {
        this.tsKey = tsKey;
        this.timeDimension = timeDimension;
        this.unit = unit;
        this.currency = currency;
    }

    public long getTsId() {
        return tsId;
    }

    public void setTsId(long tsId) {
        this.tsId = tsId;
    }

    public String getTsKey() {
        return tsKey;
    }

    public void setTsKey(String tsKey) {
        this.tsKey = tsKey;
    }

    public TimeDimension getTimeDimension() {
        return timeDimension;
    }

    public void setTimeDimension(TimeDimension timeDimension) {
        this.timeDimension = timeDimension;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        String s = "TimeSeriesHeader{tsId=" + tsId + ", tsKey='" + tsKey
                + "', dim=" + timeDimension + ", unit=" + unit;
        if (currency != null) {
            s += ", currency=" + currency;
        }
        if (objectId != null) {
            s += ", objectId=" + objectId;
        }
        return s + "}";
    }
}
