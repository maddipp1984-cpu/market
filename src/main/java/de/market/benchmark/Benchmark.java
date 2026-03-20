package de.market.benchmark;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.market.timeseries.repository.TimeSeriesRepository;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesSlice;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Benchmark {

    private static final Random RNG = new Random(42);
    private static final int SAMPLE_SIZE = 100;

    public static void main(String[] args) throws Exception {
        String jdbcUrl = env("TS_JDBC_URL", "jdbc:postgresql://localhost:5432/timeseries");
        String user = env("TS_DB_USER", "postgres");
        String pass = env("TS_DB_PASSWORD", "postgres");

        String prefix = args.length > 0 ? args[0] : "QH_";
        System.out.println("=== Lese-Benchmark gegen " + prefix + "-Zeitreihen ===");
        System.out.println();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(4);
        config.setPoolName("bench-pool");

        try (HikariDataSource ds = new HikariDataSource(config)) {
            DSLContext dsl = DSL.using(ds, SQLDialect.POSTGRES);
            TimeSeriesRepository tsRepo = new TimeSeriesRepository(dsl);

            List<Long> allIds = loadIdsByPrefix(ds, prefix);
            if (allIds.isEmpty()) {
                System.out.println("FEHLER: Keine " + prefix + "-Zeitreihen gefunden!");
                return;
            }
            System.out.printf("Gefundene Zeitreihen: %s%n", formatNumber(allIds.size()));

            int sampleSize = Math.min(SAMPLE_SIZE, allIds.size());
            List<Long> sampled = new ArrayList<>(allIds);
            Collections.shuffle(sampled, RNG);
            long[] sampleIds = sampled.subList(0, sampleSize).stream().mapToLong(Long::longValue).toArray();
            System.out.printf("Stichprobe: %d Zeitreihen%n", sampleSize);
            System.out.println();

            // 1. Tabellen-Statistik
            System.out.println("--- 1. Tabellen-Statistik ---");
            printTableStats(ds, prefix);

            LocalDate from = LocalDate.of(2023, 1, 1);
            LocalDate to = LocalDate.of(2024, 1, 1);
            LocalDateTime dtFrom = from.atStartOfDay();
            LocalDateTime dtTo = to.atStartOfDay();

            // 2. Raw JDBC (Repository.read)
            System.out.println();
            System.out.println("--- 2. Raw JDBC (Repository.read) — Jahr 2023 ---");
            benchmarkRepoRead(tsRepo, sampleIds, dtFrom, dtTo);

            // 3. PL/pgSQL Raw (ts_read_15min_raw) — Arrays, Timestamps in Java
            System.out.println();
            System.out.println("--- 3. PL/pgSQL Raw (ts_read_15min_raw) — Jahr 2023 ---");
            benchmarkPlsqlRaw(ds, sampleIds, from, to);

            // 4. PL/pgSQL Expanded (ts_read_15min) — Timestamp+Value Paare
            System.out.println();
            System.out.println("--- 4. PL/pgSQL Expanded (ts_read_15min) — Jahr 2023 ---");
            benchmarkPlsqlExpanded(ds, sampleIds, from, to);

            // 5. Aggregation: ts_sum_15min (1000 Zeitreihen summieren)
            System.out.println();
            System.out.println("--- 5. PL/pgSQL Aggregation (ts_sum_15min) — Jahr 2023 ---");
            benchmarkPlsqlSum(ds, allIds, from, to);

            // 6. Monat-Vergleich
            LocalDate monthFrom = LocalDate.of(2023, 6, 1);
            LocalDate monthTo = LocalDate.of(2023, 7, 1);
            LocalDateTime dtMonthFrom = monthFrom.atStartOfDay();
            LocalDateTime dtMonthTo = monthTo.atStartOfDay();

            System.out.println();
            System.out.println("--- 6. Vergleich Juni 2023 (1 Monat) ---");
            System.out.println("  Raw JDBC:");
            benchmarkRepoRead(tsRepo, sampleIds, dtMonthFrom, dtMonthTo);
            System.out.println("  PL/pgSQL Raw:");
            benchmarkPlsqlRaw(ds, sampleIds, monthFrom, monthTo);
            System.out.println("  PL/pgSQL Expanded:");
            benchmarkPlsqlExpanded(ds, sampleIds, monthFrom, monthTo);

            System.out.println();
            System.out.println("=== Benchmark abgeschlossen ===");
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static List<Long> loadIdsByPrefix(HikariDataSource ds, String prefix) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT ts_id FROM ts_header WHERE ts_key LIKE ? ORDER BY ts_id")) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    // ================================================================
    // Benchmark-Methoden
    // ================================================================

    private static void benchmarkRepoRead(TimeSeriesRepository repo, long[] tsIds,
                                           LocalDateTime start, LocalDateTime end) throws SQLException {
        // Warmup
        repo.read(tsIds[0], TimeDimension.QUARTER_HOUR, start, end);

        long totalValues = 0;
        long minNs = Long.MAX_VALUE, maxNs = Long.MIN_VALUE;
        long benchStart = System.nanoTime();

        for (long tsId : tsIds) {
            long t = System.nanoTime();
            TimeSeriesSlice slice = repo.read(tsId, TimeDimension.QUARTER_HOUR, start, end);
            long elapsed = System.nanoTime() - t;
            minNs = Math.min(minNs, elapsed);
            maxNs = Math.max(maxNs, elapsed);
            totalValues += slice.size();
        }

        printResult(tsIds.length, totalValues, benchStart, minNs, maxNs);
    }

    private static void benchmarkPlsqlRaw(HikariDataSource ds, long[] tsIds,
                                           LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT ts_date, vals FROM ts_read_15min_raw(?, ?, ?)";

        // Warmup
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tsIds[0]); ps.setObject(2, from); ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) {} }
        }

        long totalValues = 0;
        long minNs = Long.MAX_VALUE, maxNs = Long.MIN_VALUE;
        long benchStart = System.nanoTime();

        try (Connection conn = ds.getConnection()) {
            for (long tsId : tsIds) {
                long t = System.nanoTime();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, tsId); ps.setObject(2, from); ps.setObject(3, to);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Array arr = rs.getArray(2);
                            Double[] vals = (Double[]) arr.getArray();
                            totalValues += vals.length;
                            arr.free();
                        }
                    }
                }
                long elapsed = System.nanoTime() - t;
                minNs = Math.min(minNs, elapsed);
                maxNs = Math.max(maxNs, elapsed);
            }
        }

        printResult(tsIds.length, totalValues, benchStart, minNs, maxNs);
    }

    private static void benchmarkPlsqlExpanded(HikariDataSource ds, long[] tsIds,
                                                LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT ts_time, value FROM ts_read_15min(?, ?, ?)";

        // Warmup
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tsIds[0]); ps.setObject(2, from); ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) {} }
        }

        long totalValues = 0;
        long minNs = Long.MAX_VALUE, maxNs = Long.MIN_VALUE;
        long benchStart = System.nanoTime();

        try (Connection conn = ds.getConnection()) {
            for (long tsId : tsIds) {
                long t = System.nanoTime();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, tsId); ps.setObject(2, from); ps.setObject(3, to);
                    ps.setFetchSize(10_000);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) totalValues++;
                    }
                }
                long elapsed = System.nanoTime() - t;
                minNs = Math.min(minNs, elapsed);
                maxNs = Math.max(maxNs, elapsed);
            }
        }

        printResult(tsIds.length, totalValues, benchStart, minNs, maxNs);
    }

    private static void benchmarkPlsqlSum(HikariDataSource ds, List<Long> allIds,
                                           LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT ts_date, vals FROM ts_sum_15min(?, ?, ?)";
        Long[] idArray = allIds.toArray(new Long[0]);

        // Warmup
        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Array sqlIds = conn.createArrayOf("bigint", new Long[]{idArray[0], idArray[1]});
            ps.setArray(1, sqlIds); ps.setObject(2, from); ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) {} }
            sqlIds.free();
        }

        long benchStart = System.nanoTime();
        long totalValues = 0;

        try (Connection conn = ds.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Array sqlIds = conn.createArrayOf("bigint", idArray);
            try {
                ps.setArray(1, sqlIds); ps.setObject(2, from); ps.setObject(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Array arr = rs.getArray(2);
                        Double[] vals = (Double[]) arr.getArray();
                        totalValues += vals.length;
                        arr.free();
                    }
                }
            } finally {
                sqlIds.free();
            }
        }

        long elapsed = System.nanoTime() - benchStart;
        System.out.printf("  %s Zeitreihen summiert: %s Ergebnis-Werte%n",
                formatNumber(allIds.size()), formatNumber(totalValues));
        System.out.printf("  Gesamt: %s%n", formatDuration(elapsed));
    }

    // ================================================================
    // Hilfsmethoden
    // ================================================================

    private static void printResult(int count, long totalValues, long benchStartNs,
                                     long minNs, long maxNs) {
        long elapsed = System.nanoTime() - benchStartNs;
        double avgMs = (elapsed / 1_000_000.0) / count;
        System.out.printf("  %d Zeitreihen: %s Werte%n", count, formatNumber(totalValues));
        System.out.printf("  Pro Zeitreihe: %.1f ms (min: %.1f ms, max: %.1f ms)%n",
                avgMs, minNs / 1_000_000.0, maxNs / 1_000_000.0);
        System.out.printf("  Gesamt: %s%n", formatDuration(elapsed));
    }

    private static void printTableStats(HikariDataSource ds, String prefix) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement pst = conn.prepareStatement(
                     "SELECT count(*) FROM ts_header WHERE ts_key LIKE ?")) {
            pst.setString(1, prefix + "%");
            ResultSet rs = pst.executeQuery();
            rs.next();
            System.out.printf("  Header: %s%n", formatNumber(rs.getLong(1)));
            rs.close();

            Statement stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT count(*) FROM ts_values_15min");
            rs.next();
            System.out.printf("  Zeilen (Tage): %s%n", formatNumber(rs.getLong(1)));
            rs.close();

            rs = stmt.executeQuery("SELECT pg_size_pretty(hypertable_size('ts_values_15min'))");
            rs.next();
            System.out.printf("  Tabellengroesse: %s%n", rs.getString(1));
            rs.close();

            rs = stmt.executeQuery(
                    "SELECT count(*) FROM timescaledb_information.chunks " +
                    "WHERE hypertable_name = 'ts_values_15min'");
            rs.next();
            System.out.printf("  Chunks: %d%n", rs.getInt(1));
            rs.close();
        }
    }

    private static String formatDuration(long nanos) {
        if (nanos < 1_000_000) return String.format("%.2f us", nanos / 1_000.0);
        if (nanos < 1_000_000_000) return String.format("%.1f ms", nanos / 1_000_000.0);
        return String.format("%.2f s", nanos / 1_000_000_000.0);
    }

    private static String formatNumber(long n) {
        if (n < 1_000) return String.valueOf(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1_000.0);
        if (n < 1_000_000_000) return String.format("%.2f Mio", n / 1_000_000.0);
        return String.format("%.2f Mrd", n / 1_000_000_000.0);
    }
}
