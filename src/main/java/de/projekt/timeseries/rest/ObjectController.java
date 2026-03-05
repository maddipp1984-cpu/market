package de.projekt.timeseries.rest;

import de.projekt.timeseries.api.TimeSeriesService;
import de.projekt.timeseries.model.ObjectType;
import de.projekt.timeseries.model.TimeSeriesHeader;
import de.projekt.timeseries.rest.dto.CreateObjectRequest;
import de.projekt.timeseries.rest.dto.ObjectResponse;
import de.projekt.timeseries.rest.dto.TimeSeriesHeaderResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/objects")
public class ObjectController {

    private final TimeSeriesService service;

    public ObjectController(TimeSeriesService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody CreateObjectRequest req) throws SQLException {
        ObjectType type = parseEnum(ObjectType.class, req.getType(), "type");
        long objectId = service.createObject(type, req.getKey(), req.getDescription());
        return ResponseEntity.status(201).body(Map.of("objectId", objectId));
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
        ObjectType objectType = parseEnum(ObjectType.class, type, "type");
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

    @DeleteMapping("/{objectId}")
    public ResponseEntity<Void> delete(@PathVariable long objectId) throws SQLException {
        service.deleteObject(objectId);
        return ResponseEntity.noContent().build();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Ungueltiger Wert fuer '" + fieldName + "': " + value
                    + ". Erlaubt: " + java.util.Arrays.toString(enumClass.getEnumConstants()));
        }
    }
}
