package de.market.timeseries.security;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Repository
public class AuthPermissionRepository {

    private final DataSource dataSource;

    public AuthPermissionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<AuthPermission> findByGroupId(int groupId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT permission_id, group_id, resource_key, object_type_id, can_read, can_write, can_delete " +
                 "FROM ts_auth_permission WHERE group_id = ?")) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AuthPermission> list = new ArrayList<>();
                while (rs.next()) list.add(mapPermission(rs));
                return list;
            }
        }
    }

    public List<AuthPermission> findByGroupIds(List<Integer> groupIds) throws SQLException {
        if (groupIds.isEmpty()) return Collections.emptyList();
        // Build IN clause
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT permission_id, group_id, resource_key, object_type_id, can_read, can_write, can_delete ");
        sb.append("FROM ts_auth_permission WHERE group_id IN (");
        for (int i = 0; i < groupIds.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        sb.append(')');
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < groupIds.size(); i++) {
                ps.setInt(i + 1, groupIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<AuthPermission> list = new ArrayList<>();
                while (rs.next()) list.add(mapPermission(rs));
                return list;
            }
        }
    }

    public void replacePermissions(int groupId, List<AuthPermission> permissions) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete existing
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM ts_auth_permission WHERE group_id = ?")) {
                    del.setInt(1, groupId);
                    del.executeUpdate();
                }
                // Insert new
                if (!permissions.isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO ts_auth_permission (group_id, resource_key, object_type_id, can_read, can_write, can_delete) " +
                            "VALUES (?, ?, ?, ?, ?, ?)")) {
                        for (AuthPermission p : permissions) {
                            ins.setInt(1, groupId);
                            ins.setString(2, p.getResourceKey());
                            if (p.getObjectTypeId() != null) {
                                ins.setInt(3, p.getObjectTypeId());
                            } else {
                                ins.setNull(3, Types.SMALLINT);
                            }
                            ins.setBoolean(4, p.isCanRead());
                            ins.setBoolean(5, p.isCanWrite());
                            ins.setBoolean(6, p.isCanDelete());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<AuthFieldRestriction> findRestrictionsByGroupId(int groupId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT restriction_id, group_id, resource_key, field_key, object_type_id " +
                 "FROM ts_auth_field_restriction WHERE group_id = ?")) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AuthFieldRestriction> list = new ArrayList<>();
                while (rs.next()) list.add(mapRestriction(rs));
                return list;
            }
        }
    }

    public List<AuthFieldRestriction> findRestrictionsByGroupIds(List<Integer> groupIds) throws SQLException {
        if (groupIds.isEmpty()) return Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT restriction_id, group_id, resource_key, field_key, object_type_id ");
        sb.append("FROM ts_auth_field_restriction WHERE group_id IN (");
        for (int i = 0; i < groupIds.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        sb.append(')');
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < groupIds.size(); i++) {
                ps.setInt(i + 1, groupIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<AuthFieldRestriction> list = new ArrayList<>();
                while (rs.next()) list.add(mapRestriction(rs));
                return list;
            }
        }
    }

    public void replaceFieldRestrictions(int groupId, List<AuthFieldRestriction> restrictions) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM ts_auth_field_restriction WHERE group_id = ?")) {
                    del.setInt(1, groupId);
                    del.executeUpdate();
                }
                if (!restrictions.isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO ts_auth_field_restriction (group_id, resource_key, field_key, object_type_id) " +
                            "VALUES (?, ?, ?, ?)")) {
                        for (AuthFieldRestriction r : restrictions) {
                            ins.setInt(1, groupId);
                            ins.setString(2, r.getResourceKey());
                            ins.setString(3, r.getFieldKey());
                            if (r.getObjectTypeId() != null) {
                                ins.setInt(4, r.getObjectTypeId());
                            } else {
                                ins.setNull(4, Types.SMALLINT);
                            }
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private AuthPermission mapPermission(ResultSet rs) throws SQLException {
        AuthPermission p = new AuthPermission();
        p.setPermissionId(rs.getInt("permission_id"));
        p.setGroupId(rs.getInt("group_id"));
        p.setResourceKey(rs.getString("resource_key"));
        int typeId = rs.getInt("object_type_id");
        p.setObjectTypeId(rs.wasNull() ? null : typeId);
        p.setCanRead(rs.getBoolean("can_read"));
        p.setCanWrite(rs.getBoolean("can_write"));
        p.setCanDelete(rs.getBoolean("can_delete"));
        return p;
    }

    private AuthFieldRestriction mapRestriction(ResultSet rs) throws SQLException {
        AuthFieldRestriction r = new AuthFieldRestriction();
        r.setRestrictionId(rs.getInt("restriction_id"));
        r.setGroupId(rs.getInt("group_id"));
        r.setResourceKey(rs.getString("resource_key"));
        r.setFieldKey(rs.getString("field_key"));
        int typeId = rs.getInt("object_type_id");
        r.setObjectTypeId(rs.wasNull() ? null : typeId);
        return r;
    }
}
