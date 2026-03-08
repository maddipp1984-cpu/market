package de.market.currency.repository;

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
public class CurrencyOverviewRepository {

    private final DataSource dataSource;
    private final QueryRegistry queryRegistry;

    public CurrencyOverviewRepository(DataSource dataSource, QueryRegistry queryRegistry) {
        this.dataSource = dataSource;
        this.queryRegistry = queryRegistry;
    }

    public List<Map<String, Object>> findAllAsRows() throws SQLException {
        String sql = queryRegistry.get("currency/overview");
        return executeQuery(sql, List.of());
    }

    public List<Map<String, Object>> findFiltered(String whereSql, List<Object> params) throws SQLException {
        String baseSql = queryRegistry.get("currency/overview");
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
                    row.put("id", rs.getShort("currency_id"));
                    row.put("isoCode", rs.getString("iso_code"));
                    row.put("description", rs.getString("description"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
