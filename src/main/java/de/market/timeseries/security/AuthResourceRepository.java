package de.market.timeseries.security;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Repository
public class AuthResourceRepository {

    private final DataSource dataSource;

    public AuthResourceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<AuthResource> findAll() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT resource_key, label, has_type_scope FROM ts_auth_resource ORDER BY resource_key")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<AuthResource> list = new ArrayList<>();
                while (rs.next()) {
                    AuthResource r = new AuthResource();
                    r.setResourceKey(rs.getString("resource_key"));
                    r.setLabel(rs.getString("label"));
                    r.setHasTypeScope(rs.getBoolean("has_type_scope"));
                    list.add(r);
                }
                return list;
            }
        }
    }
}
