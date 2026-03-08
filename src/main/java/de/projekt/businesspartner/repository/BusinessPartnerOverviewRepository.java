package de.projekt.businesspartner.repository;

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
public class BusinessPartnerOverviewRepository {

    private final DataSource dataSource;

    public BusinessPartnerOverviewRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> findAllAsRows() throws SQLException {
        String sql = "SELECT id, short_name, name FROM business_partner ORDER BY short_name";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("shortName", rs.getString("short_name"));
                row.put("name", rs.getString("name"));
                rows.add(row);
            }
        }
        return rows;
    }
}
