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
public class ScheduleOverviewRepository {

    private final DataSource dataSource;

    public ScheduleOverviewRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> findAllAsRows() {
        String sql = """
            SELECT s.id, s.job_key AS "jobKey", s.name,
                   s.schedule_type AS "scheduleType",
                   s.enabled, s.cron_expression AS "cronExpression",
                   s.interval_seconds AS "intervalSeconds",
                   (SELECT MAX(e.start_time) FROM batch_job_execution_log e WHERE e.schedule_id = s.id) AS "lastRun",
                   (SELECT e2.status FROM batch_job_execution_log e2
                    WHERE e2.schedule_id = s.id ORDER BY e2.start_time DESC LIMIT 1) AS "lastStatus"
            FROM batch_schedule s
            ORDER BY s.name
            """;

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("jobKey", rs.getString("jobKey"));
                row.put("name", rs.getString("name"));
                row.put("scheduleType", rs.getString("scheduleType"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("cronExpression", rs.getString("cronExpression"));
                row.put("intervalSeconds", rs.getObject("intervalSeconds"));
                OffsetDateTime lastRun = rs.getObject("lastRun", OffsetDateTime.class);
                row.put("lastRun", lastRun != null ? lastRun.toString() : null);
                row.put("lastStatus", rs.getString("lastStatus"));
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Laden der Batch-Schedules", e);
        }
        return rows;
    }
}
