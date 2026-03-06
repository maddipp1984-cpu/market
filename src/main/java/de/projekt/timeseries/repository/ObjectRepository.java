package de.projekt.timeseries.repository;

import de.projekt.timeseries.model.ObjectType;
import de.projekt.timeseries.model.TsObject;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ObjectRepository {

    private final DataSource dataSource;

    public ObjectRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long create(TsObject object) throws SQLException {
        String sql = "INSERT INTO ts_object (type_id, object_key, description) " +
                     "VALUES (?, ?, ?) RETURNING object_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, object.getObjectType().getCode());
            ps.setString(2, object.getObjectKey());
            ps.setString(3, object.getDescription());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                object.setObjectId(id);
                return id;
            }
        }
    }

    public Optional<TsObject> findById(long objectId) throws SQLException {
        String sql = "SELECT object_id, type_id, object_key, description, created_at, updated_at " +
                     "FROM ts_object WHERE object_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, objectId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<TsObject> findByKey(String objectKey) throws SQLException {
        String sql = "SELECT object_id, type_id, object_key, description, created_at, updated_at " +
                     "FROM ts_object WHERE object_key = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, objectKey);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public List<TsObject> findAll() throws SQLException {
        String sql = "SELECT object_id, type_id, object_key, description, created_at, updated_at " +
                     "FROM ts_object ORDER BY object_key";

        List<TsObject> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    public List<TsObject> findByType(ObjectType type) throws SQLException {
        String sql = "SELECT object_id, type_id, object_key, description, created_at, updated_at " +
                     "FROM ts_object WHERE type_id = ?";

        List<TsObject> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, type.getCode());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public boolean update(TsObject object) throws SQLException {
        String sql = "UPDATE ts_object SET type_id = ?, object_key = ?, description = ?, " +
                     "updated_at = NOW() WHERE object_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, object.getObjectType().getCode());
            ps.setString(2, object.getObjectKey());
            ps.setString(3, object.getDescription());
            ps.setLong(4, object.getObjectId());

            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(long objectId) throws SQLException {
        String sql = "DELETE FROM ts_object WHERE object_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, objectId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<TsObject> findFiltered(String whereClause, List<Object> params) throws SQLException {
        String sql = "SELECT object_id, type_id, object_key, description, created_at, updated_at " +
                     "FROM ts_object o WHERE " + whereClause + " ORDER BY o.object_key";

        List<TsObject> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    private TsObject mapRow(ResultSet rs) throws SQLException {
        TsObject obj = new TsObject();
        obj.setObjectId(rs.getLong("object_id"));
        obj.setObjectType(ObjectType.fromCode(rs.getInt("type_id")));
        obj.setObjectKey(rs.getString("object_key"));
        obj.setDescription(rs.getString("description"));
        obj.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        obj.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return obj;
    }
}
