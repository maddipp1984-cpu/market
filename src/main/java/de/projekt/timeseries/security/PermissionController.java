package de.projekt.timeseries.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.*;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyPermissions() throws SQLException {
        UUID userId = UUID.fromString(SecurityUtils.getCurrentUserId());
        boolean admin = permissionService.isAdmin(userId);
        List<EffectivePermission> perms = admin ? Collections.emptyList() : permissionService.getEffectivePermissions(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId.toString());
        result.put("isAdmin", admin);
        result.put("permissions", perms);
        return ResponseEntity.ok(result);
    }
}
