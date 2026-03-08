package de.market.timeseries.rest.dto;

import de.market.timeseries.model.TimeSeriesSlice;

import java.time.LocalDateTime;

public class TimeSeriesValuesResponse {

    private LocalDateTime start;
    private LocalDateTime end;
    private String dimension;
    private int count;
    private double[] values;

    public static TimeSeriesValuesResponse from(TimeSeriesSlice slice) {
        TimeSeriesValuesResponse r = new TimeSeriesValuesResponse();
        r.start = slice.getStart();
        r.end = slice.getEnd();
        r.dimension = slice.getDimension().name();
        r.count = slice.size();
        r.values = slice.getValues();
        return r;
    }

    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }
    public String getDimension() { return dimension; }
    public int getCount() { return count; }
    public double[] getValues() { return values; }
}
