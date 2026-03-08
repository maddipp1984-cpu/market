package de.market.scheduling.repository;

import de.market.scheduling.model.JobStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JobExecutionLogRepository {

    private final DataSource dataSource;

    public JobExecutionLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long insertExecution(int scheduleId, OffsetDateTime startTime,
                                 JobStatus status, String triggeredBy) throws SQLException {
        String sql = "INSERT INTO batch_job_execution_log (schedule_id, start_time, status, triggered_by) " +
                     "VALUES (?, ?, ?, ?) RETURNING id";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleId);
            ps.setObject(2, startTime);
            ps.setString(3, status.name());
            ps.setString(4, triggeredBy);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    public void updateExecution(long executionId, OffsetDateTime endTime,
                                 JobStatus status, String errorMessage,
                                 Integer recordsAffected, String logFile) throws SQLException {
        String sql = "UPDATE batch_job_execution_log SET end_time = ?, status = ?, " +
                     "error_message = ?, records_affected = ?, log_file = ? WHERE id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, endTime);
            ps.setString(2, status.name());
            ps.setString(3, errorMessage);
            if (recordsAffected != null) {
                ps.setInt(4, recordsAffected);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setString(5, logFile);
            ps.setLong(6, executionId);
            ps.executeUpdate();
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    public List<Map<String, Object>> findByScheduleId(int scheduleId, int limit) throws SQLException {
        String sql = """
            SELECT e.id, e.schedule_id AS "scheduleId",
                   s.name AS "scheduleName",
                   e.start_time AS "startTime",
                   e.end_time AS "endTime",
                   CASE WHEN e.end_time IS NOT NULL
                        THEN EXTRACT(EPOCH FROM (e.end_time - e.start_time))::INTEGER
                        ELSE NULL END AS "duration",
                   e.status, e.error_message AS "errorMessage",
                   e.records_affected AS "recordsAffected",
                   e.log_file AS "logFile",
                   e.triggered_by AS "triggeredBy"
            FROM batch_job_execution_log e
            JOIN batch_schedule s ON s.id = e.schedule_id
            WHERE e.schedule_id = ?
            ORDER BY e.start_time DESC
            LIMIT ?
            """;
        List<Map<String, Object>> rows = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        if (val instanceof OffsetDateTime) {
                            val = val.toString();
                        }
                        row.put(meta.getColumnLabel(i), val);
                    }
                    rows.add(row);
                }
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return rows;
    }

    public String findLogFileByExecutionId(long executionId) throws SQLException {
        String sql = "SELECT log_file FROM batch_job_execution_log WHERE id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("log_file");
                }
                return null;
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    public List<Map<String, Object>> findAllAsRows(int limit) throws SQLException {
        String sql = """
            SELECT e.id, e.schedule_id AS "scheduleId",
                   s.job_key AS "jobKey",
                   s.name AS "scheduleName",
                   e.start_time AS "startTime",
                   e.end_time AS "endTime",
                   CASE WHEN e.end_time IS NOT NULL
                        THEN EXTRACT(EPOCH FROM (e.end_time - e.start_time))::INTEGER
                        ELSE NULL END AS "duration",
                   e.status, e.error_message AS "errorMessage",
                   e.records_affected AS "recordsAffected",
                   e.log_file AS "logFile",
                   e.triggered_by AS "triggeredBy"
            FROM batch_job_execution_log e
            JOIN batch_schedule s ON s.id = e.schedule_id
            ORDER BY e.start_time DESC
            LIMIT ?
            """;
        List<Map<String, Object>> rows = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        if (val instanceof OffsetDateTime) {
                            val = val.toString();
                        }
                        row.put(meta.getColumnLabel(i), val);
                    }
                    rows.add(row);
                }
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return rows;
    }
}
