package de.market.filterpreset.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.market.timeseries.api.TimeSeriesService;
import de.market.filterpreset.model.FilterPreset;
import de.market.filterpreset.rest.dto.CreateFilterPresetRequest;
import de.market.filterpreset.rest.dto.FilterPresetResponse;
import de.market.security.PermissionService;
import de.market.security.SecurityUtils;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/filter-presets")
public class FilterPresetController {

    private final TimeSeriesService service;
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;

    public FilterPresetController(TimeSeriesService service, ObjectMapper objectMapper,
                                  PermissionService permissionService) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.permissionService = permissionService;
    }

    private void checkOwnerOrAdmin(FilterPreset existing, String currentUserId) {
        if (existing.getUserId() != null && existing.getUserId().equals(currentUserId)) return;
        if (permissionService.isAdmin(UUID.fromString(currentUserId))) return;
        throw new AccessDeniedException("Zugriff verweigert");
    }

    @GetMapping
    public ResponseEntity<List<FilterPresetResponse>> getPresets(
            @RequestParam String pageKey) {

        String userId = SecurityUtils.getCurrentUserId();
        List<FilterPresetResponse> result = service.getPresets(pageKey, userId).stream()
                .map(p -> FilterPresetResponse.from(p, objectMapper))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(
            @RequestBody CreateFilterPresetRequest req) {

        String userId = SecurityUtils.getCurrentUserId();

        // GLOBAL-Scope nur fuer Admins
        if ("GLOBAL".equals(req.getScope()) && !permissionService.isAdmin(UUID.fromString(userId))) {
            throw new AccessDeniedException("Nur Admins koennen globale Presets erstellen");
        }

        FilterPreset preset = new FilterPreset();
        preset.setPageKey(req.getPageKey());
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

        long presetId = service.createPreset(preset);
        return ResponseEntity.status(201).body(Map.of("presetId", presetId));
    }

    @PutMapping("/{presetId}")
    public ResponseEntity<Void> update(
            @PathVariable long presetId,
            @RequestBody CreateFilterPresetRequest req) {

        String userId = SecurityUtils.getCurrentUserId();

        // Owner-Check
        FilterPreset existing = service.getPreset(presetId)
                .orElseThrow(() -> new IllegalArgumentException("Preset nicht gefunden"));
        checkOwnerOrAdmin(existing, userId);

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
    public ResponseEntity<Void> delete(@PathVariable long presetId) {
        String userId = SecurityUtils.getCurrentUserId();

        // Owner-Check
        FilterPreset existing = service.getPreset(presetId)
                .orElseThrow(() -> new IllegalArgumentException("Preset nicht gefunden"));
        checkOwnerOrAdmin(existing, userId);

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
            @RequestParam String scope) {

        String userId = SecurityUtils.getCurrentUserId();

        // Owner-Check
        FilterPreset existing = service.getPreset(presetId)
                .orElseThrow(() -> new IllegalArgumentException("Preset nicht gefunden"));
        checkOwnerOrAdmin(existing, userId);

        service.setPresetAsDefault(presetId, pageKey, scope, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{presetId}/default")
    public ResponseEntity<Void> clearDefault(@PathVariable long presetId) {
        String userId = SecurityUtils.getCurrentUserId();

        // Owner-Check
        FilterPreset existing = service.getPreset(presetId)
                .orElseThrow(() -> new IllegalArgumentException("Preset nicht gefunden"));
        checkOwnerOrAdmin(existing, userId);

        service.clearPresetDefault(presetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/default")
    public ResponseEntity<FilterPresetResponse> getDefault(
            @RequestParam String pageKey) {

        String userId = SecurityUtils.getCurrentUserId();
        return service.getDefaultPreset(pageKey, userId)
                .map(p -> ResponseEntity.ok(FilterPresetResponse.from(p, objectMapper)))
                .orElse(ResponseEntity.notFound().build());
    }
}
