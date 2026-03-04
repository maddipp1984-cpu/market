package de.projekt.timeseries.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public enum TimeDimension {

    QUARTER_HOUR(1, "ts_values_15min", "ts_time", 96),
    HOUR(2, "ts_values_1h", "ts_time", 24),
    DAY(3, "ts_values_day", "ts_date", 365),
    MONTH(4, "ts_values_month", "ts_date", 12),
    YEAR(5, "ts_values_year", "ts_year", 1);

    private final int code;
    private final String tableName;
    private final String timeColumn;
    private final int valuesPerYear;

    TimeDimension(int code, String tableName, String timeColumn, int valuesPerYear) {
        this.code = code;
        this.tableName = tableName;
        this.timeColumn = timeColumn;
        this.valuesPerYear = valuesPerYear;
    }

    public int getCode() {
        return code;
    }

    public String getTableName() {
        return tableName;
    }

    public String getTimeColumn() {
        return timeColumn;
    }

    public int getValuesPerYear() {
        return valuesPerYear;
    }

    public boolean useTimestamptz() {
        return this == QUARTER_HOUR || this == HOUR;
    }

    public int getLevel() {
        return code;
    }

    public boolean canAggregateTo(TimeDimension target) {
        return target.code > this.code;
    }

    public boolean canDisaggregateTo(TimeDimension target) {
        return target.code < this.code;
    }

    /**
     * Berechnet die Anzahl Intervalle für einen Tag unter Berücksichtigung von DST.
     * Nur für QUARTER_HOUR und HOUR sinnvoll.
     */
    public int intervalsPerDay(LocalDate date) {
        if (!useTimestamptz()) {
            throw new UnsupportedOperationException("intervalsPerDay nur für subdaily: " + this);
        }
        ZonedDateTime startOfDay = date.atStartOfDay(TimeSeriesSlice.ZONE);
        ZonedDateTime startOfNext = date.plusDays(1).atStartOfDay(TimeSeriesSlice.ZONE);
        long daySeconds = Duration.between(startOfDay, startOfNext).getSeconds();
        long intervalSeconds = this == QUARTER_HOUR ? 900 : 3600;
        return (int) (daySeconds / intervalSeconds);
    }

    public static TimeDimension fromCode(int code) {
        for (TimeDimension dim : values()) {
            if (dim.code == code) {
                return dim;
            }
        }
        throw new IllegalArgumentException("Unbekannte Zeitdimension: " + code);
    }
}
