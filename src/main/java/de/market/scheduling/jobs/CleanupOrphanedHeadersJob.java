package de.market.scheduling.jobs;

import de.market.scheduling.model.JobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@Component
public class CleanupOrphanedHeadersJob extends AbstractBatchJob {

    private static final Logger log = LoggerFactory.getLogger(CleanupOrphanedHeadersJob.class);

    private final DataSource dataSource;

    public CleanupOrphanedHeadersJob(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getJobKey() {
        return "cleanup-orphaned-headers";
    }

    @Override
    public String getName() {
        return "Verwaiste Header bereinigen";
    }

    @Override
    public String getDescription() {
        return "Loescht ts_header-Eintraege ohne Werte in allen Werte-Tabellen";
    }

    @Override
    public JobResult execute() {
        log.info("Starte Bereinigung verwaister Header...");
        String deleteSql = "DELETE FROM ts_header " +
                "WHERE NOT EXISTS (SELECT 1 FROM ts_values_15min v WHERE v.ts_id = ts_header.id) " +
                "AND NOT EXISTS (SELECT 1 FROM ts_values_1h v WHERE v.ts_id = ts_header.id) " +
                "AND NOT EXISTS (SELECT 1 FROM ts_values_day v WHERE v.ts_id = ts_header.id) " +
                "AND NOT EXISTS (SELECT 1 FROM ts_values_month v WHERE v.ts_id = ts_header.id) " +
                "AND NOT EXISTS (SELECT 1 FROM ts_values_year v WHERE v.ts_id = ts_header.id) " +
                "AND ts_key NOT LIKE 'PERF_TEST_%'";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            int deleted = ps.executeUpdate();
            log.info("{} verwaiste Header geloescht", deleted);
            return new JobResult(deleted, deleted + " verwaiste Header geloescht");
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Bereinigen verwaister Header", e);
        }
    }
}
