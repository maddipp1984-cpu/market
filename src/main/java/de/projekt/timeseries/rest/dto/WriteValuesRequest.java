package de.projekt.timeseries.rest.dto;

import java.time.LocalDate;

public class WriteValuesRequest {

    private LocalDate date;
    private double[] values;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public double[] getValues() { return values; }
    public void setValues(double[] values) { this.values = values; }
}
