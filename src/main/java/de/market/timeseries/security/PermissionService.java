package de.market.timeseries.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.*;

@Service
public class PermissionService {

    private final AuthUserRepository userRepo;
    private final AuthGroupRepository groupRepo;
    private final AuthPermissionRepository permRepo;

    public PermissionService(AuthUserRepository userRepo, AuthGroupRepository groupRepo,
                            AuthPermissionRepository permRepo) {
        this.userRepo = userRepo;
        this.groupRepo = groupRepo;
        this.permRepo = permRepo;
    }

    public void registerUser(Jwt jwt) throws SQLException {
        UUID userId = UUID.fromString(jwt.getSubject());
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        userRepo.upsert(userId, username, email);
    }

    public boolean isAdmin(UUID userId) throws SQLException {
        return userRepo.findById(userId).map(AuthUser::isAdmin).orElse(false);
    }

    public List<EffectivePermission> getEffectivePermissions(UUID userId) throws SQLException {
        // Admin gets everything
        if (isAdmin(userId)) {
            return Collections.emptyList(); // Frontend checks isAdmin separately
        }

        List<Integer> groupIds = groupRepo.findGroupIdsForUser(userId);
        if (groupIds.isEmpty()) return Collections.emptyList();

        List<AuthPermission> allPerms = permRepo.findByGroupIds(groupIds);
        List<AuthFieldRestriction> allRestrictions = permRepo.findRestrictionsByGroupIds(groupIds);

        // Aggregate permissions: OR over groups
        // Key = resourceKey + ":" + objectTypeId (null -> "null")
        Map<String, EffectivePermission> effectiveMap = new LinkedHashMap<>();

        for (AuthPermission p : allPerms) {
            String key = p.getResourceKey() + ":" + p.getObjectTypeId();
            EffectivePermission ep = effectiveMap.computeIfAbsent(key, k ->
                new EffectivePermission(p.getResourceKey(), p.getObjectTypeId()));
            // OR over groups
            ep.setCanRead(ep.isCanRead() || p.isCanRead());
            ep.setCanWrite(ep.isCanWrite() || p.isCanWrite());
            ep.setCanDelete(ep.isCanDelete() || p.isCanDelete());
        }

        // Field restrictions: only restricted if ALL groups restrict it
        // Build map: fieldKey -> Set<groupId> that restrict it
        // Then: restricted only if all user's groups restrict it
        Map<String, Map<String, Set<Integer>>> restrictionMap = new HashMap<>();
        // key = resourceKey:objectTypeId:fieldKey -> Set<groupId>
        for (AuthFieldRestriction r : allRestrictions) {
            restrictionMap
                .computeIfAbsent(r.getResourceKey() + ":" + r.getObjectTypeId(), k -> new HashMap<>())
                .computeIfAbsent(r.getFieldKey(), k -> new HashSet<>())
                .add(r.getGroupId());
        }

        int totalGroups = groupIds.size();
        for (var entry : restrictionMap.entrySet()) {
            String permKey = entry.getKey();
            EffectivePermission ep = effectiveMap.get(permKey);
            if (ep == null) continue;
            for (var fieldEntry : entry.getValue().entrySet()) {
                // Only restricted if ALL groups restrict it
                if (fieldEntry.getValue().size() == totalGroups) {
                    ep.getRestrictedFields().add(fieldEntry.getKey());
                }
            }
        }

        return new ArrayList<>(effectiveMap.values());
    }

    public Set<String> getVisibleResourceKeys(UUID userId) throws SQLException {
        if (isAdmin(userId)) return null; // null = all visible (admin)

        List<EffectivePermission> perms = getEffectivePermissions(userId);
        Set<String> visible = new HashSet<>();
        for (EffectivePermission ep : perms) {
            if (ep.isCanRead()) {
                visible.add(ep.getResourceKey());
            }
        }
        return visible;
    }

    public void checkAccess(UUID userId, String resourceKey, Integer objectTypeId, String scope) throws SQLException {
        if (isAdmin(userId)) return;

        List<EffectivePermission> perms = getEffectivePermissions(userId);
        for (EffectivePermission ep : perms) {
            if (!ep.getResourceKey().equals(resourceKey)) continue;
            // Match type: null matches resource-level, specific typeId matches type-level
            if (objectTypeId == null && ep.getObjectTypeId() == null) {
                checkScope(ep, scope);
                return;
            }
            if (objectTypeId != null && objectTypeId.equals(ep.getObjectTypeId())) {
                checkScope(ep, scope);
                return;
            }
        }
        throw new AccessDeniedException("Zugriff verweigert");
    }

    private void checkScope(EffectivePermission ep, String scope) {
        boolean allowed = switch (scope) {
            case "read" -> ep.isCanRead();
            case "write" -> ep.isCanWrite();
            case "delete" -> ep.isCanDelete();
            default -> false;
        };
        if (!allowed) throw new AccessDeniedException("Zugriff verweigert");
    }

    public Set<Integer> getPermittedTypeIds(UUID userId, String resourceKey, String scope) throws SQLException {
        if (isAdmin(userId)) return null; // null = all types allowed

        List<EffectivePermission> perms = getEffectivePermissions(userId);
        Set<Integer> allowed = new HashSet<>();
        for (EffectivePermission ep : perms) {
            if (!ep.getResourceKey().equals(resourceKey)) continue;
            if (ep.getObjectTypeId() == null) continue; // skip resource-level
            boolean hasScope = switch (scope) {
                case "read" -> ep.isCanRead();
                case "write" -> ep.isCanWrite();
                case "delete" -> ep.isCanDelete();
                default -> false;
            };
            if (hasScope) allowed.add(ep.getObjectTypeId());
        }
        return allowed;
    }

    public Set<String> getRestrictedFields(UUID userId, String resourceKey, Integer objectTypeId) throws SQLException {
        if (isAdmin(userId)) return Collections.emptySet();

        List<EffectivePermission> perms = getEffectivePermissions(userId);
        for (EffectivePermission ep : perms) {
            if (!ep.getResourceKey().equals(resourceKey)) continue;
            if (Objects.equals(ep.getObjectTypeId(), objectTypeId)) {
                return ep.getRestrictedFields();
            }
        }
        return Collections.emptySet();
    }
}
