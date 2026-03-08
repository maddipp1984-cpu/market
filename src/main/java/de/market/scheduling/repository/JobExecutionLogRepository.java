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

    public long insertExecution(int definitionId, OffsetDateTime startTime,
                                 JobStatus status, String triggeredBy) throws SQLException {
        String sql = "INSERT INTO batch_job_execution_log (job_definition_id, start_time, status, triggered_by) " +
                     "VALUES (?, ?, ?, ?) RETURNING id";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, definitionId);
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

    public List<Map<String, Object>> findByDefinitionId(int definitionId, int limit) throws SQLException {
        String sql = "SELECT id, start_time, end_time, status, error_message, records_affected, " +
                     "log_file, triggered_by FROM batch_job_execution_log " +
                     "WHERE job_definition_id = ? ORDER BY start_time DESC LIMIT ?";
        List<Map<String, Object>> rows = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, definitionId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("startTime", rs.getObject("start_time", OffsetDateTime.class).toString());
                    OffsetDateTime endTime = rs.getObject("end_time", OffsetDateTime.class);
                    row.put("endTime", endTime != null ? endTime.toString() : null);
                    row.put("status", rs.getString("status"));
                    row.put("errorMessage", rs.getString("error_message"));
                    row.put("recordsAffected", rs.getObject("records_affected"));
                    row.put("logFile", rs.getString("log_file"));
                    row.put("triggeredBy", rs.getString("triggered_by"));
                    rows.add(row);
                }
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return rows;
    }
}
