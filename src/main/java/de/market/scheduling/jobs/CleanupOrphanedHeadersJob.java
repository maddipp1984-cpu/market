package de.market.scheduling.jobs;

import de.market.scheduling.model.JobParameter;
import de.market.scheduling.model.JobParameterType;
import de.market.scheduling.model.JobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

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
    public List<JobParameter> getParameters() {
        return List.of(
                JobParameter.optional("excludePattern", JobParameterType.PATTERN,
                        "Key-Muster zum Ausschliessen", "PERF_TEST_%"),
                JobParameter.optional("retentionDays", JobParameterType.INTEGER,
                        "Mindest-Alter in Tagen bevor ein Header als verwaist gilt", 90)
        );
    }

    @Override
    public JobResult execute(Map<String, Object> parameters) {
        String excludePattern = (String) parameters.getOrDefault("excludePattern", "PERF_TEST_%");
        int retentionDays = parameters.containsKey("retentionDays")
                ? ((Number) parameters.get("retentionDays")).intValue()
                : 90;

        log.info("Starte Bereinigung verwaister Header (excludePattern={}, retentionDays={})...",
                excludePattern, retentionDays);

        String deleteSql = "DELETE FROM ts_header " +
                "WHERE NOT EXISTS (SELECT 1 FROM ts_values_15min v WHERE v.ts_id = ts_header.id) " +
                "AND NOT EXISTS (SELECT 1 FROM ts_values_1h v WHERE v.ts_id = ts_header.id) " +
                "AND NOT EXISTS (SELECT 1 FROM ts_values_day v WHERE v.ts_id = ts_header.id) " +
                "AND NOT EXISTS (SELECT 1 FROM ts_values_month v WHERE v.ts_id = ts_header.id) " +
                "AND NOT EXISTS (SELECT 1 FROM ts_values_year v WHERE v.ts_id = ts_header.id) " +
                "AND ts_key NOT LIKE ? " +
                "AND created_at < now() - make_interval(days => ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, excludePattern);
            ps.setInt(2, retentionDays);
            int deleted = ps.executeUpdate();
            log.info("{} verwaiste Header geloescht", deleted);
            return new JobResult(deleted, deleted + " verwaiste Header geloescht");
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Bereinigen verwaister Header", e);
        }
    }
}
