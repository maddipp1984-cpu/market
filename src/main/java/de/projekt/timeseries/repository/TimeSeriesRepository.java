package de.projekt.timeseries.repository;

import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesSlice;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class TimeSeriesRepository {

    private final DataSource dataSource;

    public TimeSeriesRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ================================================================
    // Schreiben: 1/4h und 1h (horizontal, Stored Procedures)
    // ================================================================

    /**
     * Schreibt einen Tag per Stored Procedure (Upsert, DST-validiert).
     */
    public void writeDay(long tsId, TimeDimension dim, LocalDate date, double[] values) throws SQLException {
        requireSubdaily(dim, "writeDay");
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_write_15min_day" : "ts_write_1h_day";
        String sql = "SELECT " + func + "(?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tsId);
            ps.setObject(2, date);
            ps.setArray(3, conn.createArrayOf("float8", toBoxed(values)));
            ps.execute();
        }
    }

    /**
     * Schreibt ein ganzes Jahr per Stored Procedure.
     * EIN Datenbankaufruf für 35.136 Werte (1/4h) bzw. 8.760 Werte (1h).
     */
    public int writeYear(long tsId, TimeDimension dim, int year, double[] values) throws SQLException {
        requireSubdaily(dim, "writeYear");
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_write_15min_year" : "ts_write_1h_year";
        String sql = "SELECT " + func + "(?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tsId);
            ps.setInt(2, year);
            ps.setArray(3, conn.createArrayOf("float8", toBoxed(values)));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Schreibt einen Bereich per Stored Procedure.
     */
    public int writeRange(long tsId, TimeDimension dim, LocalDate from, LocalDate to,
                          double[] values) throws SQLException {
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_write_15min_range" : null;
        if (func == null) throw new UnsupportedOperationException("writeRange nur für 15min implementiert");

        String sql = "SELECT " + func + "(?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tsId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            ps.setArray(4, conn.createArrayOf("float8", toBoxed(values)));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ================================================================
    // Lesen: 1/4h und 1h
    // ================================================================

    /**
     * Liest eine Zeitreihe als TimeSeriesSlice.
     * Fehlende Tage werden mit NaN aufgefüllt (DST-aware).
     * Start/End-Uhrzeiten werden berücksichtigt (Anschnitt erster/letzter Tag).
     *
     * @param start Beginn (inklusiv), Uhrzeit wird berücksichtigt
     * @param end   Ende (exklusiv), Uhrzeit wird berücksichtigt
     */
    public TimeSeriesSlice read(long tsId, TimeDimension dim,
                                LocalDateTime start, LocalDateTime end) throws SQLException {
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

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tsId);
            ps.setObject(2, firstDay);
            ps.setObject(3, lastDayExcl);
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

            double[] values = assembleValues(dayValues, dim, start, end, lastDayExcl);
            return new TimeSeriesSlice(start, end, dim, values);
        }
    }

    /**
     * Baut das Werte-Array zusammen: füllt fehlende Tage mit NaN, schneidet ersten/letzten Tag an.
     * Package-private für Testbarkeit.
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
     * Berechnet den Slot-Index für eine Uhrzeit an einem bestimmten Tag (DST-aware).
     * Nutzt ZonedDateTime für korrekte Berechnung an DST-Umstellungstagen.
     * Bei nicht-existenten Zeiten (z.B. 02:30 am Sommerzeit-Tag) wird auf die
     * nächste gültige Zeit vorgerückt. Bei doppelten Zeiten (Winterzeit) wird
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

    public void writeSimple(long tsId, TimeDimension dim, LocalDate date, double value) throws SQLException {
        String sql;
        if (dim == TimeDimension.YEAR) {
            sql = "INSERT INTO ts_values_year (ts_id, ts_year, value) VALUES (?, ?, ?) " +
                  "ON CONFLICT (ts_id, ts_year) DO UPDATE SET value = EXCLUDED.value";
        } else {
            sql = "INSERT INTO " + dim.getTableName() + " (ts_id, ts_date, value) VALUES (?, ?, ?) " +
                  "ON CONFLICT (ts_id, ts_date) DO UPDATE SET value = EXCLUDED.value";
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tsId);
            if (dim == TimeDimension.YEAR) {
                ps.setShort(2, (short) date.getYear());
            } else {
                ps.setObject(2, date);
            }
            ps.setDouble(3, value);
            ps.executeUpdate();
        }
    }

    public TimeSeriesSlice readSimple(long tsId, TimeDimension dim,
                                      LocalDateTime start, LocalDateTime end) throws SQLException {
        String timeCol = dim == TimeDimension.YEAR ? "ts_year" : "ts_date";
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT value FROM ").append(dim.getTableName());
        sb.append(" WHERE ts_id = ?");
        sb.append(" AND ").append(timeCol).append(" >= ?");
        sb.append(" AND ").append(timeCol).append(" < ?");
        sb.append(" ORDER BY ").append(timeCol);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {

            ps.setLong(1, tsId);
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
                    valueList.add(rs.getDouble(1));
                }
            }

            double[] values = new double[valueList.size()];
            for (int i = 0; i < valueList.size(); i++) {
                values[i] = valueList.get(i);
            }

            return new TimeSeriesSlice(start, end, dim, values);
        }
    }

    // ================================================================
    // Löschen
    // ================================================================

    public int delete(long tsId, TimeDimension dim) throws SQLException {
        return delete(tsId, dim, null, null);
    }

    public int delete(long tsId, TimeDimension dim, LocalDate from, LocalDate to) throws SQLException {
        if (dim == TimeDimension.QUARTER_HOUR || dim == TimeDimension.HOUR) {
            String func = dim == TimeDimension.QUARTER_HOUR ? "ts_delete_15min" : "ts_delete_1h";
            String sql = "SELECT " + func + "(?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
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
        }

        String timeCol = dim == TimeDimension.YEAR ? "ts_year" : "ts_date";
        StringBuilder sb = new StringBuilder("DELETE FROM " + dim.getTableName() + " WHERE ts_id = ?");
        if (from != null) sb.append(" AND ").append(timeCol).append(" >= ?");
        if (to != null) sb.append(" AND ").append(timeCol).append(" < ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
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
    }

    // ================================================================
    // Zählen
    // ================================================================

    public long count(long tsId, TimeDimension dim) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + dim.getTableName() + " WHERE ts_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tsId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ================================================================
    // Hilfsmethoden
    // ================================================================

    private static void requireSubdaily(TimeDimension dim, String method) {
        if (dim != TimeDimension.QUARTER_HOUR && dim != TimeDimension.HOUR) {
            throw new IllegalArgumentException(method + " nur für 15min und 1h, nicht: " + dim);
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
