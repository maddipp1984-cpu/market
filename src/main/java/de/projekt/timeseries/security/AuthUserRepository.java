package de.projekt.timeseries.security;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class AuthUserRepository {

    private final DataSource dataSource;

    public AuthUserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<AuthUser> findById(UUID userId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT user_id, username, email, is_admin, created_at FROM ts_auth_user WHERE user_id = ?")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public List<AuthUser> findAll() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT user_id, username, email, is_admin, created_at FROM ts_auth_user ORDER BY username")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<AuthUser> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    public void upsert(UUID userId, String username, String email) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO ts_auth_user (user_id, username, email) VALUES (?, ?, ?) " +
                 "ON CONFLICT (user_id) DO UPDATE SET username = EXCLUDED.username, email = EXCLUDED.email")) {
            ps.setObject(1, userId);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.executeUpdate();
        }
    }

    public void setAdmin(UUID userId, boolean admin) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE ts_auth_user SET is_admin = ? WHERE user_id = ?")) {
            ps.setBoolean(1, admin);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
    }

    private AuthUser mapRow(ResultSet rs) throws SQLException {
        AuthUser u = new AuthUser();
        u.setUserId(rs.getObject("user_id", UUID.class));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setAdmin(rs.getBoolean("is_admin"));
        u.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return u;
    }
}
