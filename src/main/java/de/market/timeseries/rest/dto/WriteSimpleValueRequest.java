package de.market.timeseries.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class WriteSimpleValueRequest {

    @NotNull
    private LocalDate date;
    @NotNull
    private Double value;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
}
