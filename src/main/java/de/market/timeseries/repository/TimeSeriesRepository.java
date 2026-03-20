package de.market.timeseries.repository;

import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.*;
import java.util.*;

import static org.jooq.impl.DSL.*;

@Repository
public class TimeSeriesRepository {

    private final DSLContext dsl;

    public TimeSeriesRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ================================================================
    // Schreiben: 1/4h und 1h (horizontal, Stored Procedures)
    // ================================================================

    /**
     * Schreibt einen Tag per Stored Procedure (Upsert, DST-validiert).
     */
    public void writeDay(long tsId, TimeDimension dim, LocalDate date, double[] values) {
        requireSubdaily(dim, "writeDay");
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_write_15min_day" : "ts_write_1h_day";
        String sql = "SELECT " + func + "(?, ?, ?)";

        dsl.connection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, tsId);
                ps.setObject(2, date);
                ps.setArray(3, conn.createArrayOf("float8", toBoxed(values)));
                ps.execute();
            }
        });
    }

    /**
     * Schreibt ein ganzes Jahr per Stored Procedure.
     * EIN Datenbankaufruf fuer 35.136 Werte (1/4h) bzw. 8.760 Werte (1h).
     */
    public int writeYear(long tsId, TimeDimension dim, int year, double[] values) {
        requireSubdaily(dim, "writeYear");
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_write_15min_year" : "ts_write_1h_year";
        String sql = "SELECT " + func + "(?, ?, ?)";

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, tsId);
                ps.setInt(2, year);
                ps.setArray(3, conn.createArrayOf("float8", toBoxed(values)));

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    /**
     * Schreibt einen Bereich per Stored Procedure.
     */
    public int writeRange(long tsId, TimeDimension dim, LocalDate from, LocalDate to,
                          double[] values) {
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_write_15min_range" : null;
        if (func == null) throw new UnsupportedOperationException("writeRange nur fuer 15min implementiert");

        String sql = "SELECT " + func + "(?, ?, ?, ?)";

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, tsId);
                ps.setObject(2, from);
                ps.setObject(3, to);
                ps.setArray(4, conn.createArrayOf("float8", toBoxed(values)));

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    // ================================================================
    // Lesen: 1/4h und 1h
    // ================================================================

    /**
     * Liest eine Zeitreihe als TimeSeriesSlice.
     * Fehlende Tage werden mit NaN aufgefuellt (DST-aware).
     * Start/End-Uhrzeiten werden beruecksichtigt (Anschnitt erster/letzter Tag).
     *
     * @param start Beginn (inklusiv), Uhrzeit wird beruecksichtigt
     * @param end   Ende (exklusiv), Uhrzeit wird beruecksichtigt
     */
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

        String sql = "SELECT ts_date, vals FROM " + dim.getTableName() +
                     " WHERE ts_id = ? AND ts_date >= ? AND ts_date < ?" +
                     " ORDER BY ts_date";

        final LocalDate finalLastDayExcl = lastDayExcl;

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, tsId);
                ps.setObject(2, firstDay);
                ps.setObject(3, finalLastDayExcl);
                ps.setFetchSize(1_000);

                Map<LocalDate, double[]> dayValues = new HashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate date = rs.getObject(1, LocalDate.class);
                        Array sqlArray = rs.getArray(2);
                        Double[] boxed = (Double[]) sqlArray.getArray();
                        double[] vals = new double[boxed.length];
                        for (int i = 0; i < boxed.length; i++) {
                            vals[i] = boxed[i] != null ? boxed[i] : Double.NaN;
                        }
                        dayValues.put(date, vals);
                    }
                }

                double[] values = assembleValues(dayValues, dim, start, end, finalLastDayExcl);
                return new TimeSeriesSlice(start, end, dim, values);
            }
        });
    }

    /**
     * Baut das Werte-Array zusammen: fuellt fehlende Tage mit NaN, schneidet ersten/letzten Tag an.
     * Package-private fuer Testbarkeit.
     */
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
                fromSlot = slotOffset(day, start.toLocalTime(), interval);
            }
            if (day.equals(end.toLocalDate()) && !end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                toSlot = slotOffset(day, end.toLocalTime(), interval);
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

    /**
     * Berechnet den Slot-Index fuer eine Uhrzeit an einem bestimmten Tag (DST-aware).
     * Nutzt ZonedDateTime fuer korrekte Berechnung an DST-Umstellungstagen.
     * Bei nicht-existenten Zeiten (z.B. 02:30 am Sommerzeit-Tag) wird auf die
     * naechste gueltige Zeit vorgerueckt. Bei doppelten Zeiten (Winterzeit) wird
     * die erste Occurrence verwendet.
     */
    static int slotOffset(LocalDate date, LocalTime time, Duration interval) {
        ZonedDateTime startOfDay = date.atStartOfDay(TimeSeriesSlice.ZONE);
        ZonedDateTime target = date.atTime(time).atZone(TimeSeriesSlice.ZONE);
        long seconds = Duration.between(startOfDay, target).getSeconds();
        return (int) (seconds / interval.getSeconds());
    }

    // ================================================================
    // Schreiben/Lesen: Tag, Monat, Jahr (einfache Einzelwerte)
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
        } else {
            dsl.insertInto(table(name(dim.getTableName())))
                    .columns(field(name("ts_id")), field(name("ts_date")), field(name("value")))
                    .values(tsId, date, value)
                    .onConflict(field(name("ts_id")), field(name("ts_date")))
                    .doUpdate()
                    .set(field(name("value")), value)
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
    // Aggregation: SQL-Shortcut (gleiche Dimension + Einheit)
    // ================================================================

    /**
     * Summiert mehrere subdaily-Zeitreihen (QH/H).
     * <=1000 IDs: Stored Procedure (unnest + SUM komplett in DB).
     * >1000 IDs: Rohdaten in einer Query lesen, in Java summieren
     *            (skaliert besser, da kein LATERAL unnest auf Millionen Zeilen).
     */
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
            dayValues = readSumViaStoredProc(tsIds, dim, firstDay, lastDayExcl);
        } else {
            dayValues = readSumViaJava(tsIds, dim, firstDay, lastDayExcl);
        }

        double[] values = assembleValues(dayValues, dim, start, end, lastDayExcl);
        return new TimeSeriesSlice(start, end, dim, values);
    }

    /** Stored Procedure: DB summiert Arrays elementweise (schnell bei <=1000 IDs) */
    private Map<LocalDate, double[]> readSumViaStoredProc(List<Long> tsIds, TimeDimension dim,
                                                          LocalDate firstDay, LocalDate lastDayExcl) {
        Long[] idArray = tsIds.toArray(new Long[0]);
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_sum_15min" : "ts_sum_1h";
        String sql = "SELECT ts_date, vals FROM " + func + "(?, ?, ?)";

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                java.sql.Array sqlIds = conn.createArrayOf("bigint", idArray);
                try {
                    ps.setArray(1, sqlIds);
                    ps.setObject(2, firstDay);
                    ps.setObject(3, lastDayExcl);
                    ps.setFetchSize(1_000);

                    Map<LocalDate, double[]> dayValues = new HashMap<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            LocalDate date = rs.getObject(1, LocalDate.class);
                            java.sql.Array sqlArray = rs.getArray(2);
                            Double[] boxed = (Double[]) sqlArray.getArray();
                            sqlArray.free();
                            double[] vals = new double[boxed.length];
                            for (int i = 0; i < boxed.length; i++) {
                                vals[i] = boxed[i] != null ? boxed[i] : Double.NaN;
                            }
                            dayValues.put(date, vals);
                        }
                    }
                    return dayValues;
                } finally {
                    sqlIds.free();
                }
            }
        });
    }

    /** Rohdaten in einer Query, Java summiert (skaliert besser bei >1000 IDs) */
    private Map<LocalDate, double[]> readSumViaJava(List<Long> tsIds, TimeDimension dim,
                                                     LocalDate firstDay, LocalDate lastDayExcl) {
        Long[] idArray = tsIds.toArray(new Long[0]);
        String sql = "SELECT ts_date, vals FROM " + dim.getTableName() +
                " WHERE ts_id = ANY(?) AND ts_date >= ? AND ts_date < ?" +
                " ORDER BY ts_date";

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                java.sql.Array sqlIds = conn.createArrayOf("bigint", idArray);
                try {
                    ps.setArray(1, sqlIds);
                    ps.setObject(2, firstDay);
                    ps.setObject(3, lastDayExcl);
                    ps.setFetchSize(10_000);

                    Map<LocalDate, double[]> sumByDate = new HashMap<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            LocalDate date = rs.getObject(1, LocalDate.class);
                            java.sql.Array sqlArray = rs.getArray(2);
                            Double[] boxed = (Double[]) sqlArray.getArray();
                            sqlArray.free();

                            double[] existing = sumByDate.get(date);
                            if (existing == null) {
                                double[] vals = new double[boxed.length];
                                for (int i = 0; i < boxed.length; i++) {
                                    vals[i] = boxed[i] != null ? boxed[i] : 0;
                                }
                                sumByDate.put(date, vals);
                            } else {
                                for (int i = 0; i < Math.min(existing.length, boxed.length); i++) {
                                    if (boxed[i] != null) existing[i] += boxed[i];
                                }
                            }
                        }
                    }
                    return sumByDate;
                } finally {
                    sqlIds.free();
                }
            }
        });
    }

    /**
     * Summiert mehrere simple-Zeitreihen (Tag/Monat/Jahr) in einer einzigen SQL-Query.
     */
    public TimeSeriesSlice readSumSimple(List<Long> tsIds, TimeDimension dim,
                                          LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end muss nach start liegen: " + start + " / " + end);
        }
        Long[] idArray = tsIds.toArray(new Long[0]);
        String timeCol = dim == TimeDimension.YEAR ? "ts_year" : "ts_date";

        String sql = "SELECT " + timeCol + ", COALESCE(SUM(value), 0) " +
                "FROM " + dim.getTableName() +
                " WHERE ts_id = ANY(?) AND " + timeCol + " >= ? AND " + timeCol + " < ?" +
                " GROUP BY " + timeCol +
                " ORDER BY " + timeCol;

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                java.sql.Array sqlIds = conn.createArrayOf("bigint", idArray);
                try {
                    ps.setArray(1, sqlIds);
                    if (dim == TimeDimension.YEAR) {
                        ps.setShort(2, (short) start.getYear());
                        ps.setShort(3, (short) end.getYear());
                    } else {
                        ps.setObject(2, start.toLocalDate());
                        ps.setObject(3, end.toLocalDate());
                    }

                    List<Double> valueList = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            valueList.add(rs.getDouble(2));
                        }
                    }

                    double[] values = new double[valueList.size()];
                    for (int i = 0; i < valueList.size(); i++) {
                        values[i] = valueList.get(i);
                    }
                    return new TimeSeriesSlice(start, end, dim, values);
                } finally {
                    sqlIds.free();
                }
            }
        });
    }

    // ================================================================
    // Loeschen
    // ================================================================

    public int delete(long tsId, TimeDimension dim) {
        return delete(tsId, dim, null, null);
    }

    public int delete(long tsId, TimeDimension dim, LocalDate from, LocalDate to) {
        if (dim == TimeDimension.QUARTER_HOUR || dim == TimeDimension.HOUR) {
            String func = dim == TimeDimension.QUARTER_HOUR ? "ts_delete_15min" : "ts_delete_1h";
            String sql = "SELECT " + func + "(?, ?, ?)";
            return dsl.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, tsId);
                    if (from != null) ps.setObject(2, from);
                    else ps.setNull(2, Types.DATE);
                    if (to != null) ps.setObject(3, to);
                    else ps.setNull(3, Types.DATE);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getInt(1);
                    }
                }
            });
        }

        String timeCol = dim == TimeDimension.YEAR ? "ts_year" : "ts_date";
        StringBuilder sb = new StringBuilder("DELETE FROM " + dim.getTableName() + " WHERE ts_id = ?");
        if (from != null) sb.append(" AND ").append(timeCol).append(" >= ?");
        if (to != null) sb.append(" AND ").append(timeCol).append(" < ?");

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                int idx = 1;
                ps.setLong(idx++, tsId);
                if (from != null) {
                    if (dim == TimeDimension.YEAR) ps.setShort(idx++, (short) from.getYear());
                    else ps.setObject(idx++, from);
                }
                if (to != null) {
                    if (dim == TimeDimension.YEAR) ps.setShort(idx++, (short) to.getYear());
                    else ps.setObject(idx++, to);
                }
                return ps.executeUpdate();
            }
        });
    }

    // ================================================================
    // Zaehlen
    // ================================================================

    public long count(long tsId, TimeDimension dim) {
        return dsl.selectCount()
                .from(table(name(dim.getTableName())))
                .where(field(name("ts_id")).eq(tsId))
                .fetchOne(0, Long.class);
    }

    // ================================================================
    // Hilfsmethoden
    // ================================================================

    private static void requireSubdaily(TimeDimension dim, String method) {
        if (dim != TimeDimension.QUARTER_HOUR && dim != TimeDimension.HOUR) {
            throw new IllegalArgumentException(method + " nur fuer 15min und 1h, nicht: " + dim);
        }
    }

    private static Double[] toBoxed(double[] arr) {
        Double[] boxed = new Double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            boxed[i] = arr[i];
        }
        return boxed;
    }
}
