package de.market.timeseries.rest.dto;

public class AggregateResponse {

    private TimeSeriesHeaderResponse header;
    private TimeSeriesValuesResponse values;

    public AggregateResponse(TimeSeriesHeaderResponse header, TimeSeriesValuesResponse values) {
        this.header = header;
        this.values = values;
    }

    public TimeSeriesHeaderResponse getHeader() { return header; }
    public TimeSeriesValuesResponse getValues() { return values; }
}
