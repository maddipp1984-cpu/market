package de.projekt.timeseries.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Repräsentiert eine Zeitreihe als flaches double[]-Array mit einem Startpunkt.
 * Timestamps werden nur bei Bedarf berechnet (lazy, DST-aware).
 *
 * Speicherverbrauch: ~8 Bytes pro Wert (nur double[]).
 * Ein Jahr 15min ≈ 274 KB statt 4,3 MB mit einzelnen DataPoint-Objekten.
 */
public class TimeSeriesSlice {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final LocalDateTime start;
    private final LocalDateTime end;
    private final TimeDimension dimension;
    private final double[] values;

    public TimeSeriesSlice(LocalDateTime start, LocalDateTime end,
                           TimeDimension dimension, double[] values) {
        this.start = start;
        this.end = end;
        this.dimension = dimension;
        this.values = values;
    }

    /**
     * Berechnet den Timestamp für den Wert an Position index.
     * Berücksichtigt DST: An Umstellungstagen variiert die Intervallanzahl.
     */
    public OffsetDateTime getTimestamp(int index) {
        ZonedDateTime zdt = start.atZone(ZONE);
        switch (dimension) {
            case QUARTER_HOUR:
            case HOUR:
                return zdt.plus(getInterval().multipliedBy(index)).toOffsetDateTime();
            case DAY:
                return start.toLocalDate().plusDays(index)
                        .atStartOfDay(ZONE).toOffsetDateTime();
            case MONTH:
                return start.toLocalDate().plusMonths(index)
                        .atStartOfDay(ZONE).toOffsetDateTime();
            case YEAR:
                return start.toLocalDate().plusYears(index)
                        .atStartOfDay(ZONE).toOffsetDateTime();
            default:
                throw new UnsupportedOperationException(
                        "getTimestamp nicht unterstützt für: " + dimension);
        }
    }

    public double getValue(int index) {
        return values[index];
    }

    public int size() {
        return values.length;
    }

    public double[] getValues() {
        return values;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public TimeDimension getDimension() {
        return dimension;
    }

    private Duration getInterval() {
        switch (dimension) {
            case QUARTER_HOUR: return Duration.ofMinutes(15);
            case HOUR: return Duration.ofHours(1);
            default: throw new UnsupportedOperationException(
                    "getTimestamp nicht unterstützt für: " + dimension);
        }
    }

    @Override
    public String toString() {
        return "TimeSeriesSlice{start=" + start + ", end=" + end +
               ", dim=" + dimension + ", values=" + values.length + "}";
    }
}
