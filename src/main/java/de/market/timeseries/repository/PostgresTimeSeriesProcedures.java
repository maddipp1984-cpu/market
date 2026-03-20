package de.market.timeseries.repository;

import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PostgreSQL-spezifische Zeitreihen-Operationen.
 * Nutzt PL/pgSQL Stored Procedures und PostgreSQL-Arrays.
 */
@Repository
@Profile("!oracle")
public class PostgresTimeSeriesProcedures implements TimeSeriesProcedures {

    private final DSLContext dsl;

    public PostgresTimeSeriesProcedures(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void writeDay(long tsId, TimeDimension dim, LocalDate date, double[] values) {
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

    @Override
    public int writeYear(long tsId, TimeDimension dim, int year, double[] values) {
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

    @Override
    public int writeRange(long tsId, TimeDimension dim, LocalDate from, LocalDate to, double[] values) {
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

    @Override
    public Map<LocalDate, double[]> readSubdaily(long tsId, TimeDimension dim,
                                                  LocalDate firstDay, LocalDate lastDayExcl) {
        String sql = "SELECT ts_date, vals FROM " + dim.getTableName() +
                " WHERE ts_id = ? AND ts_date >= ? AND ts_date < ? ORDER BY ts_date";

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, tsId);
                ps.setObject(2, firstDay);
                ps.setObject(3, lastDayExcl);
                ps.setFetchSize(1_000);
                return fetchDayArrays(ps);
            }
        });
    }

    @Override
    public Map<LocalDate, double[]> readSumViaStoredProc(List<Long> tsIds, TimeDimension dim,
                                                          LocalDate firstDay, LocalDate lastDayExcl) {
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_sum_15min" : "ts_sum_1h";
        String sql = "SELECT ts_date, vals FROM " + func + "(?, ?, ?)";

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                Array sqlIds = conn.createArrayOf("bigint", tsIds.toArray(new Long[0]));
                try {
                    ps.setArray(1, sqlIds);
                    ps.setObject(2, firstDay);
                    ps.setObject(3, lastDayExcl);
                    ps.setFetchSize(1_000);
                    return fetchDayArrays(ps);
                } finally {
                    sqlIds.free();
                }
            }
        });
    }

    @Override
    public Map<LocalDate, double[]> readSumViaJava(List<Long> tsIds, TimeDimension dim,
                                                     LocalDate firstDay, LocalDate lastDayExcl) {
        String sql = "SELECT ts_date, vals FROM " + dim.getTableName() +
                " WHERE ts_id = ANY(?) AND ts_date >= ? AND ts_date < ? ORDER BY ts_date";

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                Array sqlIds = conn.createArrayOf("bigint", tsIds.toArray(new Long[0]));
                try {
                    ps.setArray(1, sqlIds);
                    ps.setObject(2, firstDay);
                    ps.setObject(3, lastDayExcl);
                    ps.setFetchSize(10_000);

                    Map<LocalDate, double[]> sumByDate = new HashMap<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            LocalDate date = rs.getObject(1, LocalDate.class);
                            Array sqlArray = rs.getArray(2);
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

    @Override
    public TimeSeriesSlice readSumSimple(List<Long> tsIds, TimeDimension dim,
                                          LocalDateTime start, LocalDateTime end) {
        String timeCol = dim == TimeDimension.YEAR ? "ts_year" : "ts_date";
        String sql = "SELECT " + timeCol + ", COALESCE(SUM(value), 0) FROM " + dim.getTableName() +
                " WHERE ts_id = ANY(?) AND " + timeCol + " >= ? AND " + timeCol + " < ?" +
                " GROUP BY " + timeCol + " ORDER BY " + timeCol;

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                Array sqlIds = conn.createArrayOf("bigint", tsIds.toArray(new Long[0]));
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

    @Override
    public int deleteSubdaily(long tsId, TimeDimension dim, LocalDate from, LocalDate to) {
        String func = dim == TimeDimension.QUARTER_HOUR ? "ts_delete_15min" : "ts_delete_1h";
        String sql = "SELECT " + func + "(?, ?, ?)";

        return dsl.connectionResult(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, tsId);
                if (from != null) ps.setObject(2, from);
                else ps.setNull(2, java.sql.Types.DATE);
                if (to != null) ps.setObject(3, to);
                else ps.setNull(3, java.sql.Types.DATE);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    // --- Hilfsmethoden ---

    private Map<LocalDate, double[]> fetchDayArrays(PreparedStatement ps) throws java.sql.SQLException {
        Map<LocalDate, double[]> dayValues = new HashMap<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LocalDate date = rs.getObject(1, LocalDate.class);
                Array sqlArray = rs.getArray(2);
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
    }

    private static Double[] toBoxed(double[] arr) {
        Double[] boxed = new Double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            boxed[i] = arr[i];
        }
        return boxed;
    }
}
