package de.market.scheduling.repository;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JobOverviewRepository {

    private final DataSource dataSource;

    public JobOverviewRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> findAllAsRows() throws SQLException {
        String sql = "SELECT d.id, d.job_key, d.name, d.schedule_type, d.enabled, " +
                     "d.cron_expression, d.interval_seconds, " +
                     "(SELECT MAX(e.start_time) FROM batch_job_execution_log e WHERE e.job_definition_id = d.id) AS last_run, " +
                     "(SELECT e.status FROM batch_job_execution_log e WHERE e.job_definition_id = d.id ORDER BY e.start_time DESC LIMIT 1) AS last_status " +
                     "FROM batch_job_definition d ORDER BY d.name";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("jobKey", rs.getString("job_key"));
                row.put("name", rs.getString("name"));
                row.put("scheduleType", rs.getString("schedule_type"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("cronExpression", rs.getString("cron_expression"));
                row.put("intervalSeconds", rs.getObject("interval_seconds"));
                OffsetDateTime lastRun = rs.getObject("last_run", OffsetDateTime.class);
                row.put("lastRun", lastRun != null ? lastRun.toString() : null);
                row.put("lastStatus", rs.getString("last_status"));
                rows.add(row);
            }
        }
        return rows;
    }
}
