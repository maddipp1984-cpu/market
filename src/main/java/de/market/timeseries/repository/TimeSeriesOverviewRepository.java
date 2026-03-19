package de.market.timeseries.repository;

import de.market.shared.query.QueryRegistry;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TimeSeriesOverviewRepository {

    private final DataSource dataSource;
    private final QueryRegistry queryRegistry;

    public TimeSeriesOverviewRepository(DataSource dataSource, QueryRegistry queryRegistry) {
        this.dataSource = dataSource;
        this.queryRegistry = queryRegistry;
    }

    public List<Map<String, Object>> findAllAsRows() throws SQLException {
        String sql = queryRegistry.get("timeseries/overview");
        return executeQuery(sql, List.of());
    }

    public List<Map<String, Object>> findFiltered(String whereSql, List<Object> params) throws SQLException {
        String baseSql = queryRegistry.get("timeseries/overview");
        String sql = baseSql.replaceFirst("(?i)ORDER BY", "WHERE " + whereSql + " ORDER BY");
        return executeQuery(sql, params);
    }

    private List<Map<String, Object>> executeQuery(String sql, List<Object> params) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("ts_id"));
                    row.put("key", rs.getString("ts_key"));
                    row.put("dimension", rs.getString("dimension"));
                    row.put("unit", rs.getString("symbol"));
                    row.put("currency", rs.getString("iso_code"));
                    row.put("object", rs.getString("object_key"));
                    row.put("description", rs.getString("description"));
                    row.put("createdAt", rs.getTimestamp("created_at"));
                    row.put("updatedAt", rs.getTimestamp("updated_at"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
