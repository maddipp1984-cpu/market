package de.projekt.timeseries.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.projekt.timeseries.api.TimeSeriesService;
import de.projekt.timeseries.model.FilterPreset;
import de.projekt.timeseries.rest.dto.CreateFilterPresetRequest;
import de.projekt.timeseries.rest.dto.FilterPresetResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/filter-presets")
public class FilterPresetController {

    private final TimeSeriesService service;
    private final ObjectMapper objectMapper;

    public FilterPresetController(TimeSeriesService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<List<FilterPresetResponse>> getPresets(
            @RequestParam String pageKey,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) throws SQLException {

        List<FilterPresetResponse> result = service.getPresets(pageKey, userId).stream()
                .map(p -> FilterPresetResponse.from(p, objectMapper))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(
            @RequestBody CreateFilterPresetRequest req,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) throws SQLException {

        FilterPreset preset = new FilterPreset();
        preset.setPageKey(req.getPageKey());
        preset.setName(req.getName());
        preset.setScope(req.getScope());
        preset.setDefault(req.isDefault());

        // Serialize conditions Object to JSON string
        try {
            preset.setConditions(objectMapper.writeValueAsString(req.getConditions()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Ungueltige conditions: " + e.getMessage());
        }

        // Set userId based on scope
        if ("USER".equals(preset.getScope())) {
            preset.setUserId(userId);
        }

        long presetId = service.createPreset(preset);
        return ResponseEntity.status(201).body(Map.of("presetId", presetId));
    }

    @PutMapping("/{presetId}")
    public ResponseEntity<Void> update(
            @PathVariable long presetId,
            @RequestBody CreateFilterPresetRequest req,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) throws SQLException {

        FilterPreset preset = new FilterPreset();
        preset.setPresetId(presetId);
        preset.setName(req.getName());
        preset.setScope(req.getScope());
        preset.setDefault(req.isDefault());

        try {
            preset.setConditions(objectMapper.writeValueAsString(req.getConditions()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Ungueltige conditions: " + e.getMessage());
        }

        if ("USER".equals(preset.getScope())) {
            preset.setUserId(userId);
        }

        boolean updated = service.updatePreset(preset);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{presetId}")
    public ResponseEntity<Void> delete(@PathVariable long presetId) throws SQLException {
        boolean deleted = service.deletePreset(presetId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{presetId}/default")
    public ResponseEntity<Void> setDefault(
            @PathVariable long presetId,
            @RequestParam String pageKey,
            @RequestParam String scope,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) throws SQLException {

        service.setPresetAsDefault(presetId, pageKey, scope, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{presetId}/default")
    public ResponseEntity<Void> clearDefault(@PathVariable long presetId) throws SQLException {
        service.clearPresetDefault(presetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/default")
    public ResponseEntity<FilterPresetResponse> getDefault(
            @RequestParam String pageKey,
            @RequestHeader(value = "X-User-Id", defaultValue = "default") String userId) throws SQLException {

        return service.getDefaultPreset(pageKey, userId)
                .map(p -> ResponseEntity.ok(FilterPresetResponse.from(p, objectMapper)))
                .orElse(ResponseEntity.notFound().build());
    }
}
