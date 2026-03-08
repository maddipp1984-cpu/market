package de.market.timeseries.repository;

import de.market.timeseries.model.Currency;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesHeader;
import de.market.timeseries.model.Unit;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class HeaderRepository {

    private final DataSource dataSource;

    public HeaderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long create(TimeSeriesHeader header) throws SQLException {
        String sql = "INSERT INTO ts_header (ts_key, time_dim, unit_id, currency_id, object_id, description) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING ts_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, header.getTsKey());
            ps.setInt(2, header.getTimeDimension().getCode());
            ps.setInt(3, header.getUnit().getCode());
            if (header.getCurrency() != null) {
                ps.setInt(4, header.getCurrency().getCode());
            } else {
                ps.setNull(4, Types.SMALLINT);
            }
            if (header.getObjectId() != null) {
                ps.setLong(5, header.getObjectId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setString(6, header.getDescription());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                header.setTsId(id);
                return id;
            }
        }
    }

    public Optional<TimeSeriesHeader> findById(long tsId) throws SQLException {
        String sql = "SELECT ts_id, ts_key, time_dim, unit_id, currency_id, object_id, description, created_at, updated_at " +
                     "FROM ts_header WHERE ts_id = ?";

        try (Connection conn = dataSource.getConnection();
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
        String sql = "SELECT ts_id, ts_key, time_dim, unit_id, currency_id, object_id, description, created_at, updated_at " +
                     "FROM ts_header WHERE ts_key = ?";

        try (Connection conn = dataSource.getConnection();
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
        String sql = "SELECT ts_id, ts_key, time_dim, unit_id, currency_id, object_id, description, created_at, updated_at " +
                     "FROM ts_header WHERE time_dim = ?";

        List<TimeSeriesHeader> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
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

    public boolean update(TimeSeriesHeader header) throws SQLException {
        String sql = "UPDATE ts_header SET ts_key = ?, unit_id = ?, currency_id = ?, object_id = ?, " +
                     "description = ?, updated_at = NOW() WHERE ts_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, header.getTsKey());
            ps.setInt(2, header.getUnit().getCode());
            if (header.getCurrency() != null) {
                ps.setInt(3, header.getCurrency().getCode());
            } else {
                ps.setNull(3, Types.SMALLINT);
            }
            if (header.getObjectId() != null) {
                ps.setLong(4, header.getObjectId());
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            ps.setString(5, header.getDescription());
            ps.setLong(6, header.getTsId());

            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateObjectId(long tsId, Long objectId) throws SQLException {
        String sql = "UPDATE ts_header SET object_id = ? WHERE ts_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (objectId != null) {
                ps.setLong(1, objectId);
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setLong(2, tsId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<TimeSeriesHeader> findByObjectId(long objectId) throws SQLException {
        String sql = "SELECT ts_id, ts_key, time_dim, unit_id, currency_id, object_id, description, created_at, updated_at " +
                     "FROM ts_header WHERE object_id = ?";

        List<TimeSeriesHeader> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, objectId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public boolean delete(long tsId) throws SQLException {
        String sql = "DELETE FROM ts_header WHERE ts_id = ?";

        try (Connection conn = dataSource.getConnection();
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
        h.setUnit(Unit.fromCode(rs.getInt("unit_id")));
        int currencyId = rs.getInt("currency_id");
        if (!rs.wasNull()) {
            h.setCurrency(Currency.fromCode(currencyId));
        }
        long objectId = rs.getLong("object_id");
        if (!rs.wasNull()) {
            h.setObjectId(objectId);
        }
        h.setDescription(rs.getString("description"));
        h.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        h.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return h;
    }
}
