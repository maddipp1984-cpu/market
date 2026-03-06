package de.projekt.timeseries.rest;

import de.projekt.timeseries.api.TimeSeriesService;
import de.projekt.timeseries.model.ObjectType;
import de.projekt.timeseries.model.TimeSeriesHeader;
import de.projekt.timeseries.model.TsObject;
import de.projekt.timeseries.rest.dto.ColumnMeta;
import de.projekt.timeseries.rest.dto.CreateObjectRequest;
import de.projekt.timeseries.rest.dto.FilterQueryBuilder;
import de.projekt.timeseries.rest.dto.FilterRequest;
import de.projekt.timeseries.rest.dto.ObjectResponse;
import de.projekt.timeseries.rest.dto.TableResponse;
import de.projekt.timeseries.rest.dto.TimeSeriesHeaderResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/objects")
public class ObjectController {

    private static final List<ColumnMeta> OBJECT_COLUMNS = List.of(
            new ColumnMeta("objectId", "ID", "o.object_id", "NUMBER"),
            new ColumnMeta("type", "Typ", "o.type_id", "TEXT"),
            new ColumnMeta("objectKey", "Schlüssel", "o.object_key", "TEXT"),
            new ColumnMeta("description", "Beschreibung", "o.description", "TEXT"),
            new ColumnMeta("createdAt", "Erstellt", "o.created_at", "DATE"),
            new ColumnMeta("updatedAt", "Aktualisiert", "o.updated_at", "DATE")
    );

    private static final Set<String> ALLOWED_SQL_COLUMNS = OBJECT_COLUMNS.stream()
            .map(ColumnMeta::getSqlColumn)
            .collect(Collectors.toSet());

    private final TimeSeriesService service;

    public ObjectController(TimeSeriesService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody CreateObjectRequest req) throws SQLException {
        ObjectType type = EnumParser.parse(ObjectType.class, req.getType(), "type");
        long objectId = service.createObject(type, req.getKey(), req.getDescription());
        return ResponseEntity.status(201).body(Map.of("objectId", objectId));
    }

    @GetMapping(params = {"!key", "!type"})
    public ResponseEntity<TableResponse> getAll() throws SQLException {
        List<Map<String, Object>> data = service.getAllObjects().stream()
                .map(this::toRow)
                .toList();
        return ResponseEntity.ok(new TableResponse(OBJECT_COLUMNS, data));
    }

    @GetMapping("/{objectId}")
    public ResponseEntity<ObjectResponse> getById(@PathVariable long objectId) throws SQLException {
        return service.getObject(objectId)
                .map(o -> ResponseEntity.ok(ObjectResponse.from(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(params = "key")
    public ResponseEntity<ObjectResponse> getByKey(@RequestParam String key) throws SQLException {
        return service.getObject(key)
                .map(o -> ResponseEntity.ok(ObjectResponse.from(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(params = "type")
    public ResponseEntity<List<ObjectResponse>> getByType(@RequestParam String type) throws SQLException {
        ObjectType objectType = EnumParser.parse(ObjectType.class, type, "type");
        List<ObjectResponse> result = service.getObjectsByType(objectType).stream()
                .map(ObjectResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{objectId}/timeseries")
    public ResponseEntity<List<TimeSeriesHeaderResponse>> getTimeSeries(@PathVariable long objectId) throws SQLException {
        List<TimeSeriesHeaderResponse> result = service.getTimeSeriesByObject(objectId).stream()
                .map(TimeSeriesHeaderResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{objectId}/timeseries/{tsId}")
    public ResponseEntity<Void> assign(@PathVariable long objectId, @PathVariable long tsId) throws SQLException {
        service.assignToObject(tsId, objectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{objectId}/timeseries/{tsId}")
    public ResponseEntity<Void> unassign(@PathVariable long objectId, @PathVariable long tsId) throws SQLException {
        TimeSeriesHeader header = service.getHeader(tsId)
                .orElseThrow(() -> new IllegalArgumentException("Zeitreihe nicht gefunden: tsId=" + tsId));
        if (header.getObjectId() == null || header.getObjectId() != objectId) {
            throw new IllegalArgumentException(
                    "Zeitreihe tsId=" + tsId + " ist nicht dem Objekt objectId=" + objectId + " zugeordnet");
        }
        service.removeFromObject(tsId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/query")
    public ResponseEntity<TableResponse> query(@RequestBody FilterRequest request) throws SQLException {
        List<TsObject> objects;

        if (request.getConditions() != null && !request.getConditions().isEmpty()) {
            FilterQueryBuilder.WhereClause where = FilterQueryBuilder.build(
                    request.getConditions(), ALLOWED_SQL_COLUMNS);
            objects = service.findObjectsFiltered(where.getSql(), where.getParams());
        } else {
            objects = service.getAllObjects();
        }

        List<Map<String, Object>> data = objects.stream().map(this::toRow).toList();
        return ResponseEntity.ok(new TableResponse(OBJECT_COLUMNS, data));
    }

    @DeleteMapping("/{objectId}")
    public ResponseEntity<Void> delete(@PathVariable long objectId) throws SQLException {
        service.deleteObject(objectId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toRow(TsObject obj) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("objectId", obj.getObjectId());
        row.put("type", obj.getObjectType().name());
        row.put("objectKey", obj.getObjectKey());
        row.put("description", obj.getDescription());
        row.put("createdAt", obj.getCreatedAt());
        row.put("updatedAt", obj.getUpdatedAt());
        return row;
    }

}
