package de.market.timeseries.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class WriteValuesRequest {

    @NotNull
    private LocalDate date;
    @NotNull
    private double[] values;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public double[] getValues() { return values; }
    public void setValues(double[] values) { this.values = values; }
}
