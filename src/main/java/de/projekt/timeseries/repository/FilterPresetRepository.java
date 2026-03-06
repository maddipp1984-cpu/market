package de.projekt.timeseries.repository;

import de.projekt.timeseries.model.FilterPreset;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class FilterPresetRepository {

    private final DataSource dataSource;

    public FilterPresetRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long create(FilterPreset preset) throws SQLException {
        String sql = "INSERT INTO ts_filter_preset (page_key, user_id, name, conditions, scope, is_default) " +
                     "VALUES (?, ?, ?, ?::jsonb, ?, ?) RETURNING preset_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, preset.getPageKey());
            ps.setString(2, preset.getUserId());
            ps.setString(3, preset.getName());
            ps.setObject(4, preset.getConditions(), Types.OTHER);
            ps.setString(5, preset.getScope());
            ps.setBoolean(6, preset.isDefault());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                preset.setPresetId(id);
                return id;
            }
        }
    }

    public List<FilterPreset> findByPageKey(String pageKey, String userId) throws SQLException {
        String sql = "SELECT preset_id, page_key, user_id, name, conditions, scope, is_default, created_at, updated_at " +
                     "FROM ts_filter_preset WHERE page_key = ? AND (scope = 'GLOBAL' OR user_id = ?) " +
                     "ORDER BY scope, name";

        List<FilterPreset> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pageKey);
            ps.setString(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public boolean update(FilterPreset preset) throws SQLException {
        String sql = "UPDATE ts_filter_preset SET name = ?, conditions = ?::jsonb, scope = ?, is_default = ?, " +
                     "updated_at = NOW() WHERE preset_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, preset.getName());
            ps.setObject(2, preset.getConditions(), Types.OTHER);
            ps.setString(3, preset.getScope());
            ps.setBoolean(4, preset.isDefault());
            ps.setLong(5, preset.getPresetId());

            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(long presetId) throws SQLException {
        String sql = "DELETE FROM ts_filter_preset WHERE preset_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, presetId);
            return ps.executeUpdate() > 0;
        }
    }

    public void setDefault(long presetId, String pageKey, String scope, String userId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Reset current default for this page/scope
                String resetSql = "UPDATE ts_filter_preset SET is_default = FALSE " +
                                  "WHERE page_key = ? AND scope = ? AND is_default = TRUE";
                if ("USER".equals(scope)) {
                    resetSql += " AND user_id = ?";
                }

                try (PreparedStatement ps = conn.prepareStatement(resetSql)) {
                    ps.setString(1, pageKey);
                    ps.setString(2, scope);
                    if ("USER".equals(scope)) {
                        ps.setString(3, userId);
                    }
                    ps.executeUpdate();
                }

                // 2. Set new default
                String setSql = "UPDATE ts_filter_preset SET is_default = TRUE WHERE preset_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(setSql)) {
                    ps.setLong(1, presetId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        conn.rollback();
                        throw new IllegalArgumentException("Preset nicht gefunden: presetId=" + presetId);
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

    public void clearDefault(long presetId) throws SQLException {
        String sql = "UPDATE ts_filter_preset SET is_default = FALSE WHERE preset_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, presetId);
            ps.executeUpdate();
        }
    }

    public Optional<FilterPreset> findDefault(String pageKey, String userId) throws SQLException {
        String sql = "SELECT preset_id, page_key, user_id, name, conditions, scope, is_default, created_at, updated_at " +
                     "FROM ts_filter_preset " +
                     "WHERE page_key = ? AND is_default = TRUE AND (user_id = ? OR scope = 'GLOBAL') " +
                     "ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pageKey);
            ps.setString(2, userId);
            ps.setString(3, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    private FilterPreset mapRow(ResultSet rs) throws SQLException {
        FilterPreset preset = new FilterPreset();
        preset.setPresetId(rs.getLong("preset_id"));
        preset.setPageKey(rs.getString("page_key"));
        preset.setUserId(rs.getString("user_id"));
        preset.setName(rs.getString("name"));
        preset.setConditions(rs.getString("conditions"));
        preset.setScope(rs.getString("scope"));
        preset.setDefault(rs.getBoolean("is_default"));
        preset.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        preset.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return preset;
    }
}
