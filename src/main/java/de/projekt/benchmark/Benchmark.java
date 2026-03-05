package de.projekt.benchmark;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.projekt.timeseries.repository.TimeSeriesRepository;
import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesSlice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

        System.out.println("=== Lese-Benchmark gegen PERF_TEST-Zeitreihen ===");
        System.out.println();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(4);
        config.setPoolName("bench-pool");

        try (HikariDataSource ds = new HikariDataSource(config)) {
            TimeSeriesRepository tsRepo = new TimeSeriesRepository(ds);

            // PERF_TEST ts_ids laden
            List<Long> allIds = loadPerfTestIds(ds);
            if (allIds.isEmpty()) {
                System.out.println("FEHLER: Keine PERF_TEST-Zeitreihen gefunden!");
                System.out.println("Bitte zuerst PERF_TEST-Daten einfügen.");
                return;
            }
            System.out.printf("Gefundene PERF_TEST-Zeitreihen: %s%n", formatNumber(allIds.size()));

            // Zufällige Stichprobe
            int sampleSize = Math.min(SAMPLE_SIZE, allIds.size());
            List<Long> sampled = new ArrayList<>(allIds);
            Collections.shuffle(sampled, RNG);
            long[] sampleIds = sampled.subList(0, sampleSize).stream().mapToLong(Long::longValue).toArray();
            System.out.printf("Stichprobe: %d Zeitreihen%n", sampleSize);
            System.out.println();

            // 1. Tabellen-Statistik
            System.out.println("--- 1. Tabellen-Statistik ---");
            printTableStats(ds);

            // 2. Read (ganzes Jahr 2024)
            System.out.println();
            System.out.println("--- 2. Lesen: Jahr 2024 ---");
            LocalDateTime yearStart = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime yearEnd = LocalDateTime.of(2025, 1, 1, 0, 0);
            benchmarkRead(tsRepo, sampleIds, yearStart, yearEnd);

            // 3. Read (1 Monat Juni 2024)
            System.out.println();
            System.out.println("--- 3. Lesen: Juni 2024 ---");
            LocalDateTime monthStart = LocalDateTime.of(2024, 6, 1, 0, 0);
            LocalDateTime monthEnd = LocalDateTime.of(2024, 7, 1, 0, 0);
            benchmarkRead(tsRepo, sampleIds, monthStart, monthEnd);

            System.out.println();
            System.out.println("=== Benchmark abgeschlossen ===");
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    // ================================================================
    // PERF_TEST-IDs laden
    // ================================================================

    private static List<Long> loadPerfTestIds(HikariDataSource ds) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT ts_id FROM ts_header WHERE ts_key LIKE 'PERF_TEST_%' ORDER BY ts_id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    // ================================================================
    // Benchmark
    // ================================================================

    private static void benchmarkRead(TimeSeriesRepository repo, long[] tsIds,
                                      LocalDateTime start, LocalDateTime end) throws SQLException {
        // Warmup
        repo.read(tsIds[0], TimeDimension.QUARTER_HOUR, start, end);

        long benchStart = System.nanoTime();
        long totalValues = 0;
        long minNs = Long.MAX_VALUE;
        long maxNs = Long.MIN_VALUE;

        for (int i = 0; i < tsIds.length; i++) {
            long iterStart = System.nanoTime();
            TimeSeriesSlice slice = repo.read(tsIds[i], TimeDimension.QUARTER_HOUR, start, end);
            long iterElapsed = System.nanoTime() - iterStart;
            minNs = Math.min(minNs, iterElapsed);
            maxNs = Math.max(maxNs, iterElapsed);

            totalValues += slice.size();
        }

        long elapsed = System.nanoTime() - benchStart;
        double avgMs = (elapsed / 1_000_000.0) / tsIds.length;
        System.out.printf("  %d Zeitreihen: %s Werte%n", tsIds.length, formatNumber(totalValues));
        System.out.printf("  Pro Zeitreihe: %.1f ms (min: %.1f ms, max: %.1f ms)%n",
                avgMs, minNs / 1_000_000.0, maxNs / 1_000_000.0);
        System.out.printf("  Gesamt: %s%n", formatDuration(elapsed));
    }

    // ================================================================
    // Hilfsmethoden
    // ================================================================

    private static void printTableStats(HikariDataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) FROM ts_header WHERE ts_key LIKE 'PERF_TEST_%'");
            rs.next();
            System.out.printf("  PERF_TEST Header: %s%n", formatNumber(rs.getLong(1)));
            rs.close();

            rs = stmt.executeQuery("SELECT count(*) FROM ts_values_15min");
            rs.next();
            long rows = rs.getLong(1);
            System.out.printf("  Zeilen (Tage): %s%n", formatNumber(rows));
            rs.close();

            rs = stmt.executeQuery("SELECT pg_size_pretty(hypertable_size('ts_values_15min'))");
            rs.next();
            System.out.printf("  Tabellengröße: %s%n", rs.getString(1));
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
        if (nanos < 1_000_000) return String.format("%.2f µs", nanos / 1_000.0);
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
