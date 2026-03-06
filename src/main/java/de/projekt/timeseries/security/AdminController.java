package de.projekt.timeseries.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final PermissionService permissionService;
    private final AuthUserRepository userRepo;
    private final AuthGroupRepository groupRepo;
    private final AuthPermissionRepository permRepo;
    private final AuthResourceRepository resourceRepo;
    private final KeycloakAdminClient keycloakClient;

    public AdminController(PermissionService permissionService, AuthUserRepository userRepo,
                          AuthGroupRepository groupRepo, AuthPermissionRepository permRepo,
                          AuthResourceRepository resourceRepo, KeycloakAdminClient keycloakClient) {
        this.permissionService = permissionService;
        this.userRepo = userRepo;
        this.groupRepo = groupRepo;
        this.permRepo = permRepo;
        this.resourceRepo = resourceRepo;
        this.keycloakClient = keycloakClient;
    }

    private UUID requireAdmin() throws SQLException {
        UUID userId = UUID.fromString(SecurityUtils.getCurrentUserId());
        if (!permissionService.isAdmin(userId)) {
            throw new AccessDeniedException("Nur Administratoren haben Zugriff");
        }
        return userId;
    }

    // ==================== User Management ====================

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers() throws SQLException {
        requireAdmin();
        List<AuthUser> dbUsers = userRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AuthUser u : dbUsers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("userId", u.getUserId().toString());
            map.put("username", u.getUsername());
            map.put("email", u.getEmail());
            map.put("isAdmin", u.isAdmin());
            map.put("createdAt", u.getCreatedAt());
            // Count groups
            List<Integer> groupIds = groupRepo.findGroupIdsForUser(u.getUserId());
            map.put("groupCount", groupIds.size());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, String>> createUser(@RequestBody Map<String, String> body)
            throws SQLException, IOException, InterruptedException {
        requireAdmin();
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");

        if (username == null || username.isBlank()) throw new IllegalArgumentException("username ist erforderlich");
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Passwort muss mindestens 8 Zeichen lang sein");
        }

        String keycloakId = keycloakClient.createUser(username, email, password);
        userRepo.upsert(UUID.fromString(keycloakId), username, email);
        audit.info("User erstellt: {} ({}), durch Admin {}", username, keycloakId, SecurityUtils.getCurrentUsername());

        return ResponseEntity.status(201).body(Map.of("userId", keycloakId));
    }

    @PutMapping("/users/{id}/admin")
    public ResponseEntity<Void> setAdmin(@PathVariable String id, @RequestBody Map<String, Boolean> body) throws SQLException {
        UUID currentUserId = requireAdmin();
        UUID targetUserId = UUID.fromString(id);
        boolean newAdminState = body.getOrDefault("isAdmin", false);

        // Lockout-Schutz: letzten Admin nicht degradieren
        if (!newAdminState) {
            if (currentUserId.equals(targetUserId)) {
                throw new IllegalArgumentException("Du kannst dir nicht selbst den Admin-Status entziehen");
            }
            long adminCount = userRepo.findAll().stream().filter(AuthUser::isAdmin).count();
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Der letzte Admin kann nicht degradiert werden");
            }
        }

        userRepo.setAdmin(targetUserId, newAdminState);
        audit.info("Admin-Status geaendert: {} -> {}, durch Admin {}", targetUserId, newAdminState, SecurityUtils.getCurrentUsername());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable String id, @RequestBody Map<String, Boolean> body)
            throws SQLException, IOException, InterruptedException {
        requireAdmin();
        String validId = UUID.fromString(id).toString(); // UUID-Validierung gegen Path Traversal
        boolean enabled = body.getOrDefault("enabled", true);
        keycloakClient.setEnabled(validId, enabled);
        audit.info("User {}: {}, durch Admin {}", validId, enabled ? "aktiviert" : "deaktiviert", SecurityUtils.getCurrentUsername());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body)
            throws SQLException, IOException, InterruptedException {
        requireAdmin();
        String validId = UUID.fromString(id).toString(); // UUID-Validierung gegen Path Traversal
        String password = body.get("password");
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Passwort muss mindestens 8 Zeichen lang sein");
        }
        keycloakClient.resetPassword(validId, password);
        audit.info("Passwort zurueckgesetzt: {}, durch Admin {}", validId, SecurityUtils.getCurrentUsername());
        return ResponseEntity.noContent().build();
    }

    // ==================== Group Management ====================

    @GetMapping("/groups")
    public ResponseEntity<List<Map<String, Object>>> getGroups() throws SQLException {
        requireAdmin();
        List<AuthGroup> groups = groupRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AuthGroup g : groups) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("groupId", g.getGroupId());
            map.put("name", g.getName());
            map.put("description", g.getDescription());
            map.put("memberCount", groupRepo.countMembers(g.getGroupId()));
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<Map<String, Object>> getGroup(@PathVariable int id) throws SQLException {
        requireAdmin();
        return groupRepo.findById(id)
            .map(g -> {
                try {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("groupId", g.getGroupId());
                    map.put("name", g.getName());
                    map.put("description", g.getDescription());

                    // Members
                    List<UUID> memberIds = groupRepo.findMemberIds(g.getGroupId());
                    List<Map<String, Object>> members = new ArrayList<>();
                    for (UUID uid : memberIds) {
                        userRepo.findById(uid).ifPresent(u -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("userId", u.getUserId().toString());
                            m.put("username", u.getUsername());
                            m.put("email", u.getEmail());
                            members.add(m);
                        });
                    }
                    map.put("members", members);

                    // Permissions
                    map.put("permissions", permRepo.findByGroupId(g.getGroupId()));
                    map.put("fieldRestrictions", permRepo.findRestrictionsByGroupId(g.getGroupId()));

                    return ResponseEntity.ok(map);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/groups")
    public ResponseEntity<Map<String, Integer>> createGroup(@RequestBody Map<String, String> body) throws SQLException {
        requireAdmin();
        String name = body.get("name");
        String description = body.get("description");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name ist erforderlich");
        int groupId = groupRepo.create(name, description);
        return ResponseEntity.status(201).body(Map.of("groupId", groupId));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<Void> updateGroup(@PathVariable int id, @RequestBody Map<String, String> body) throws SQLException {
        requireAdmin();
        String name = body.get("name");
        String description = body.get("description");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name ist erforderlich");
        groupRepo.update(id, name, description);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable int id) throws SQLException {
        requireAdmin();
        groupRepo.delete(id);
        audit.info("Gruppe geloescht: {}, durch Admin {}", id, SecurityUtils.getCurrentUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{id}/members")
    public ResponseEntity<Void> addMember(@PathVariable int id, @RequestBody Map<String, String> body) throws SQLException {
        requireAdmin();
        String userId = body.get("userId");
        if (userId == null) throw new IllegalArgumentException("userId ist erforderlich");
        groupRepo.addMember(id, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/groups/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable int id, @PathVariable String userId) throws SQLException {
        requireAdmin();
        groupRepo.removeMember(id, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    // ==================== Permissions ====================

    @PutMapping("/groups/{id}/permissions")
    public ResponseEntity<Void> setPermissions(@PathVariable int id, @RequestBody List<AuthPermission> permissions) throws SQLException {
        requireAdmin();
        for (AuthPermission p : permissions) {
            p.setGroupId(id);
        }
        permRepo.replacePermissions(id, permissions);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/groups/{id}/field-restrictions")
    public ResponseEntity<Void> setFieldRestrictions(@PathVariable int id, @RequestBody List<AuthFieldRestriction> restrictions) throws SQLException {
        requireAdmin();
        for (AuthFieldRestriction r : restrictions) {
            r.setGroupId(id);
        }
        permRepo.replaceFieldRestrictions(id, restrictions);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resources")
    public ResponseEntity<List<AuthResource>> getResources() throws SQLException {
        requireAdmin();
        return ResponseEntity.ok(resourceRepo.findAll());
    }

    @GetMapping("/users/{id}/effective")
    public ResponseEntity<Map<String, Object>> getUserEffective(@PathVariable String id) throws SQLException {
        requireAdmin();
        UUID userId = UUID.fromString(id);
        boolean admin = permissionService.isAdmin(userId);
        List<EffectivePermission> perms = admin ? Collections.emptyList() : permissionService.getEffectivePermissions(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", id);
        result.put("isAdmin", admin);
        result.put("permissions", perms);
        return ResponseEntity.ok(result);
    }
}
