package de.projekt.timeseries.model;

import java.time.OffsetDateTime;

public class TimeSeriesHeader {

    private long tsId;
    private String tsKey;
    private TimeDimension timeDimension;
    private String unit;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public TimeSeriesHeader() {
    }

    public TimeSeriesHeader(String tsKey, TimeDimension timeDimension, String unit) {
        this.tsKey = tsKey;
        this.timeDimension = timeDimension;
        this.unit = unit;
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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
        return "TimeSeriesHeader{tsId=" + tsId + ", tsKey='" + tsKey + "', dim=" + timeDimension + ", unit='" + unit + "'}";
    }
}
