package de.projekt.timeseries.repository;

import de.projekt.common.db.ConnectionPool;
import de.projekt.timeseries.model.TimeDimension;
import de.projekt.timeseries.model.TimeSeriesHeader;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HeaderRepository {

    private final ConnectionPool pool;

    public HeaderRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public long create(TimeSeriesHeader header) throws SQLException {
        String sql = "INSERT INTO ts_header (ts_key, time_dim, unit, description) " +
                     "VALUES (?, ?, ?, ?) RETURNING ts_id";

        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, header.getTsKey());
            ps.setInt(2, header.getTimeDimension().getCode());
            ps.setString(3, header.getUnit());
            ps.setString(4, header.getDescription());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                header.setTsId(id);
                return id;
            }
        }
    }

    public Optional<TimeSeriesHeader> findById(long tsId) throws SQLException {
        String sql = "SELECT ts_id, ts_key, time_dim, unit, description, created_at, updated_at " +
                     "FROM ts_header WHERE ts_id = ?";

        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tsId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<TimeSeriesHeader> findByKey(String tsKey) throws SQLException {
        String sql = "SELECT ts_id, ts_key, time_dim, unit, description, created_at, updated_at " +
                     "FROM ts_header WHERE ts_key = ?";

        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tsKey);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public List<TimeSeriesHeader> findByDimension(TimeDimension dimension) throws SQLException {
        String sql = "SELECT ts_id, ts_key, time_dim, unit, description, created_at, updated_at " +
                     "FROM ts_header WHERE time_dim = ?";

        List<TimeSeriesHeader> result = new ArrayList<>();

        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, dimension.getCode());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public void update(TimeSeriesHeader header) throws SQLException {
        String sql = "UPDATE ts_header SET ts_key = ?, unit = ?, description = ?, " +
                     "updated_at = NOW() WHERE ts_id = ?";

        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, header.getTsKey());
            ps.setString(2, header.getUnit());
            ps.setString(3, header.getDescription());
            ps.setLong(4, header.getTsId());

            ps.executeUpdate();
        }
    }

    public boolean delete(long tsId) throws SQLException {
        String sql = "DELETE FROM ts_header WHERE ts_id = ?";

        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tsId);
            return ps.executeUpdate() > 0;
        }
    }

    private TimeSeriesHeader mapRow(ResultSet rs) throws SQLException {
        TimeSeriesHeader h = new TimeSeriesHeader();
        h.setTsId(rs.getLong("ts_id"));
        h.setTsKey(rs.getString("ts_key"));
        h.setTimeDimension(TimeDimension.fromCode(rs.getInt("time_dim")));
        h.setUnit(rs.getString("unit"));
        h.setDescription(rs.getString("description"));
        h.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        h.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return h;
    }
}
