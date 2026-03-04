package de.projekt;

import de.projekt.benchmark.Benchmark;
import de.projekt.common.EnvUtil;
import de.projekt.common.db.ConnectionPool;
import de.projekt.timeseries.repository.HeaderRepository;
import de.projekt.timeseries.repository.TimeSeriesRepository;
import de.projekt.timeseries.api.TimeSeriesService;

public class Main {

    public static void main(String[] args) {
        try {
            if (args.length > 0 && "benchmark".equalsIgnoreCase(args[0])) {
                Benchmark.main(args);
                return;
            }

            String jdbcUrl = EnvUtil.getEnvOrDefault("TS_JDBC_URL", "jdbc:postgresql://localhost:5432/timeseries");
            String username = EnvUtil.getEnvOrDefault("TS_DB_USER", "postgres");
            String password = EnvUtil.getEnvOrDefault("TS_DB_PASSWORD", "postgres");

            System.out.println("Zeitreihensystem startet...");
            System.out.println("Verbinde mit: " + jdbcUrl);

            try (ConnectionPool pool = new ConnectionPool(jdbcUrl, username, password)) {
                HeaderRepository headerRepo = new HeaderRepository(pool);
                TimeSeriesRepository tsRepo = new TimeSeriesRepository(pool);
                TimeSeriesService service = new TimeSeriesService(headerRepo, tsRepo);

                System.out.println("Verbindung hergestellt.");
                System.out.println("Zeitreihensystem bereit.");
            }

        } catch (Exception e) {
            System.err.println("Fehler: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
