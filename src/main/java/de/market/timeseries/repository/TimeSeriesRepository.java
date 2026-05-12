package de.market.timeseries.repository;

import de.market.timeseries.client.DimensionConverter;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.*;
import java.util.*;

import static de.market.jooq.generated.tables.TsHeader.TS_HEADER;
import static org.jooq.impl.DSL.*;

/**
 * Zeitreihen-Repository: Lesen, Schreiben, Loeschen, Aggregation.
 *
 * DB-unabhaengige Operationen (count, writeSimple, readSimple, delete simple)
 * nutzen jOOQ DSL. DB-spezifische Operationen (Stored Procedures, Arrays)
 * werden an TimeSeriesProcedures delegiert.
 */
@Repository
public class TimeSeriesRepository {

    private final DSLContext dsl;
    private final TimeSeriesProcedures procedures;

    public TimeSeriesRepository(DSLContext dsl, TimeSeriesProcedures procedures) {
        this.dsl = dsl;
        this.procedures = procedures;
    }

    // ================================================================
    // Schreiben: 1/4h und 1h (Stored Procedures — delegiert)
    // ================================================================

    public void writeDay(long tsId, TimeDimension dim, LocalDate date, double[] values) {
        requireSubdaily(dim, "writeDay");
        procedures.writeDay(tsId, dim, date, values);
    }

    public int writeYear(long tsId, TimeDimension dim, int year, double[] values) {
        requireSubdaily(dim, "writeYear");
        return procedures.writeYear(tsId, dim, year, values);
    }

    public int writeRange(long tsId, TimeDimension dim, LocalDate from, LocalDate to, double[] values) {
        return procedures.writeRange(tsId, dim, from, to, values);
    }

    // ================================================================
    // Lesen: 1/4h und 1h (Array-Rueckgabe — delegiert)
    // ================================================================

    public TimeSeriesSlice read(long tsId, TimeDimension dim,
                                LocalDateTime start, LocalDateTime end) {
        requireSubdaily(dim, "read");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end muss nach start liegen: " + start + " / " + end);
        }

        LocalDate firstDay = start.toLocalDate();
        LocalDate lastDayExcl = end.toLocalDate();
        if (!end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            lastDayExcl = lastDayExcl.plusDays(1);
        }

        Map<LocalDate, double[]> dayValues = procedures.readSubdaily(tsId, dim, firstDay, lastDayExcl);
        double[] values = assembleValues(dayValues, dim, start, end, lastDayExcl);
        return new TimeSeriesSlice(start, end, dim, values);
    }

    // ================================================================
    // Schreiben/Lesen: Tag, Monat, Jahr (jOOQ DSL — DB-unabhaengig)
    // ================================================================

    public void writeSimple(long tsId, TimeDimension dim, LocalDate date, double value) {
        if (dim == TimeDimension.YEAR) {
            dsl.insertInto(table(name(dim.getTableName())))
                    .columns(field(name("ts_id")), field(name("ts_year")), field(name("value")))
                    .values(tsId, (short) date.getYear(), value)
                    .onConflict(field(name("ts_id")), field(name("ts_year")))
                    .doUpdate()
                    .set(field(name("value")), value)
                    .execute();

            LocalDate firstOfYear = LocalDate.of(date.getYear(), 1, 1);
            LocalDate lastOfYear = LocalDate.of(date.getYear(), 12, 31);
            dsl.update(TS_HEADER)
                    .set(TS_HEADER.FIRST_DATE, least(TS_HEADER.FIRST_DATE, val(firstOfYear)))
                    .set(TS_HEADER.LAST_DATE, greatest(TS_HEADER.LAST_DATE, val(lastOfYear)))
                    .where(TS_HEADER.TS_ID.eq(tsId))
                    .execute();
        } else {
            dsl.insertInto(table(name(dim.getTableName())))
                    .columns(field(name("ts_id")), field(name("ts_date")), field(name("value")))
                    .values(tsId, date, value)
                    .onConflict(field(name("ts_id")), field(name("ts_date")))
                    .doUpdate()
                    .set(field(name("value")), value)
                    .execute();

            dsl.update(TS_HEADER)
                    .set(TS_HEADER.FIRST_DATE, least(TS_HEADER.FIRST_DATE, val(date)))
                    .set(TS_HEADER.LAST_DATE, greatest(TS_HEADER.LAST_DATE, val(date)))
                    .where(TS_HEADER.TS_ID.eq(tsId))
                    .execute();
        }
    }

    public TimeSeriesSlice readSimple(long tsId, TimeDimension dim,
                                      LocalDateTime start, LocalDateTime end) {
        String timeCol = dim == TimeDimension.YEAR ? "ts_year" : "ts_date";

        var query = dsl.select(field(name("value"), Double.class))
                .from(table(name(dim.getTableName())))
                .where(field(name("ts_id")).eq(tsId))
                .and(field(name(timeCol)).greaterOrEqual(
                        dim == TimeDimension.YEAR ? (Object) (short) start.getYear() : start.toLocalDate()))
                .and(field(name(timeCol)).lessThan(
                        dim == TimeDimension.YEAR ? (Object) (short) end.getYear() : end.toLocalDate()))
                .orderBy(field(name(timeCol)));

        List<Double> valueList = query.fetch(field(name("value"), Double.class));

        double[] values = new double[valueList.size()];
        for (int i = 0; i < valueList.size(); i++) {
            Double v = valueList.get(i);
            values[i] = v != null ? v : Double.NaN;
        }

        return new TimeSeriesSlice(start, end, dim, values);
    }

    // ================================================================
    // Aggregation
    // ================================================================

    public TimeSeriesSlice readSumSubdaily(List<Long> tsIds, TimeDimension dim,
                                            LocalDateTime start, LocalDateTime end) {
        requireSubdaily(dim, "readSumSubdaily");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end muss nach start liegen");
        }

        LocalDate firstDay = start.toLocalDate();
        LocalDate lastDayExcl = end.toLocalDate();
        if (!end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            lastDayExcl = lastDayExcl.plusDays(1);
        }

        Map<LocalDate, double[]> dayValues;
        if (tsIds.size() <= 1000) {
            dayValues = procedures.readSumViaStoredProc(tsIds, dim, firstDay, lastDayExcl);
        } else {
            dayValues = procedures.readSumViaJava(tsIds, dim, firstDay, lastDayExcl);
        }

        double[] values = assembleValues(dayValues, dim, start, end, lastDayExcl);
        return new TimeSeriesSlice(start, end, dim, values);
    }

    public TimeSeriesSlice readSumSimple(List<Long> tsIds, TimeDimension dim,
                                          LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end muss nach start liegen: " + start + " / " + end);
        }
        return procedures.readSumSimple(tsIds, dim, start, end);
    }

    // ================================================================
    // Loeschen
    // ================================================================

    public int delete(long tsId, TimeDimension dim) {
        return delete(tsId, dim, null, null);
    }

    public int delete(long tsId, TimeDimension dim, LocalDate from, LocalDate to) {
        // Subdaily: Stored Procedure (delegiert)
        if (dim == TimeDimension.QUARTER_HOUR || dim == TimeDimension.HOUR) {
            return procedures.deleteSubdaily(tsId, dim, from, to);
        }

        // Simple Dimensionen: jOOQ DSL (DB-unabhaengig)
        String timeCol = dim == TimeDimension.YEAR ? "ts_year" : "ts_date";
        var delete = dsl.deleteFrom(table(name(dim.getTableName())))
                .where(field(name("ts_id")).eq(tsId));

        if (from != null) {
            delete = delete.and(field(name(timeCol)).greaterOrEqual(
                    dim == TimeDimension.YEAR ? (Object) (short) from.getYear() : from));
        }
        if (to != null) {
            delete = delete.and(field(name(timeCol)).lessThan(
                    dim == TimeDimension.YEAR ? (Object) (short) to.getYear() : to));
        }

        return delete.execute();
    }

    // ================================================================
    // Zaehlen (jOOQ DSL — DB-unabhaengig)
    // ================================================================

    public long count(long tsId, TimeDimension dim) {
        return dsl.selectCount()
                .from(table(name(dim.getTableName())))
                .where(field(name("ts_id")).eq(tsId))
                .fetchOne(0, Long.class);
    }

    // ================================================================
    // Hilfsmethoden (statisch, DB-unabhaengig)
    // ================================================================

    static double[] assembleValues(Map<LocalDate, double[]> dayValues, TimeDimension dim,
                                   LocalDateTime start, LocalDateTime end,
                                   LocalDate lastDayExcl) {
        LocalDate firstDay = start.toLocalDate();

        Duration interval = dim == TimeDimension.QUARTER_HOUR
                ? Duration.ofMinutes(15) : Duration.ofHours(1);

        List<double[]> chunks = new ArrayList<>();
        int totalLength = 0;

        for (LocalDate day = firstDay; day.isBefore(lastDayExcl); day = day.plusDays(1)) {
            int expectedSlots = dim.intervalsPerDay(day);
            double[] fullDay = dayValues.get(day);

            if (fullDay == null) {
                fullDay = new double[expectedSlots];
                Arrays.fill(fullDay, Double.NaN);
            } else if (fullDay.length != expectedSlots) {
                double[] padded = new double[expectedSlots];
                Arrays.fill(padded, Double.NaN);
                System.arraycopy(fullDay, 0, padded, 0, Math.min(fullDay.length, expectedSlots));
                fullDay = padded;
            }

            int fromSlot = 0;
            int toSlot = expectedSlots;

            if (day.equals(firstDay)) {
                fromSlot = DimensionConverter.slotOffsetForTime(day, start, dim);
            }
            if (day.equals(end.toLocalDate()) && !end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                toSlot = DimensionConverter.slotOffsetForTime(day, end, dim);
            }

            double[] slice = Arrays.copyOfRange(fullDay, fromSlot, toSlot);
            chunks.add(slice);
            totalLength += slice.length;
        }

        double[] values = new double[totalLength];
        int offset = 0;
        for (double[] chunk : chunks) {
            System.arraycopy(chunk, 0, values, offset, chunk.length);
            offset += chunk.length;
        }
        return values;
    }

    private static void requireSubdaily(TimeDimension dim, String method) {
        if (dim != TimeDimension.QUARTER_HOUR && dim != TimeDimension.HOUR) {
            throw new IllegalArgumentException(method + " nur fuer 15min und 1h, nicht: " + dim);
        }
    }
}
