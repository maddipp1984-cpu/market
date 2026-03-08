package de.market.timeseries.security;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Repository
public class AuthGroupRepository {

    private final DataSource dataSource;

    public AuthGroupRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<AuthGroup> findAll() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT group_id, name, description FROM ts_auth_group ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<AuthGroup> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    public Optional<AuthGroup> findById(int groupId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT group_id, name, description FROM ts_auth_group WHERE group_id = ?")) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public int create(String name, String description) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO ts_auth_group (name, description) VALUES (?, ?) RETURNING group_id")) {
            ps.setString(1, name);
            ps.setString(2, description);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void update(int groupId, String name, String description) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE ts_auth_group SET name = ?, description = ? WHERE group_id = ?")) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setInt(3, groupId);
            ps.executeUpdate();
        }
    }

    public void delete(int groupId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM ts_auth_group WHERE group_id = ?")) {
            ps.setInt(1, groupId);
            ps.executeUpdate();
        }
    }

    public void addMember(int groupId, UUID userId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO ts_auth_group_member (group_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setInt(1, groupId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
    }

    public void removeMember(int groupId, UUID userId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM ts_auth_group_member WHERE group_id = ? AND user_id = ?")) {
            ps.setInt(1, groupId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
    }

    public List<UUID> findMemberIds(int groupId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT user_id FROM ts_auth_group_member WHERE group_id = ?")) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<UUID> list = new ArrayList<>();
                while (rs.next()) list.add(rs.getObject("user_id", UUID.class));
                return list;
            }
        }
    }

    public List<Integer> findGroupIdsForUser(UUID userId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT group_id FROM ts_auth_group_member WHERE user_id = ?")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> list = new ArrayList<>();
                while (rs.next()) list.add(rs.getInt("group_id"));
                return list;
            }
        }
    }

    public int countMembers(int groupId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM ts_auth_group_member WHERE group_id = ?")) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private AuthGroup mapRow(ResultSet rs) throws SQLException {
        AuthGroup g = new AuthGroup();
        g.setGroupId(rs.getInt("group_id"));
        g.setName(rs.getString("name"));
        g.setDescription(rs.getString("description"));
        return g;
    }
}
