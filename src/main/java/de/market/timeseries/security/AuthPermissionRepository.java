package de.market.timeseries.security;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.*;

import static de.market.jooq.generated.tables.TsAuthFieldRestriction.TS_AUTH_FIELD_RESTRICTION;
import static de.market.jooq.generated.tables.TsAuthPermission.TS_AUTH_PERMISSION;

@Repository
public class AuthPermissionRepository {

    private final DSLContext dsl;

    public AuthPermissionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<AuthPermission> findByGroupId(int groupId) {
        return dsl.selectFrom(TS_AUTH_PERMISSION)
                .where(TS_AUTH_PERMISSION.GROUP_ID.eq(groupId))
                .fetch(this::mapPermission);
    }

    public List<AuthPermission> findByGroupIds(List<Integer> groupIds) {
        if (groupIds.isEmpty()) return Collections.emptyList();
        return dsl.selectFrom(TS_AUTH_PERMISSION)
                .where(TS_AUTH_PERMISSION.GROUP_ID.in(groupIds))
                .fetch(this::mapPermission);
    }

    public void replacePermissions(int groupId, List<AuthPermission> permissions) {
        dsl.transaction(cfg -> {
            var tx = cfg.dsl();

            tx.deleteFrom(TS_AUTH_PERMISSION)
                    .where(TS_AUTH_PERMISSION.GROUP_ID.eq(groupId))
                    .execute();

            for (AuthPermission p : permissions) {
                tx.insertInto(TS_AUTH_PERMISSION)
                        .set(TS_AUTH_PERMISSION.GROUP_ID, groupId)
                        .set(TS_AUTH_PERMISSION.RESOURCE_KEY, p.getResourceKey())
                        .set(TS_AUTH_PERMISSION.OBJECT_TYPE_ID, p.getObjectTypeId() != null ? p.getObjectTypeId().shortValue() : null)
                        .set(TS_AUTH_PERMISSION.CAN_READ, p.isCanRead())
                        .set(TS_AUTH_PERMISSION.CAN_WRITE, p.isCanWrite())
                        .set(TS_AUTH_PERMISSION.CAN_DELETE, p.isCanDelete())
                        .execute();
            }
        });
    }

    public List<AuthFieldRestriction> findRestrictionsByGroupId(int groupId) {
        return dsl.selectFrom(TS_AUTH_FIELD_RESTRICTION)
                .where(TS_AUTH_FIELD_RESTRICTION.GROUP_ID.eq(groupId))
                .fetch(this::mapRestriction);
    }

    public List<AuthFieldRestriction> findRestrictionsByGroupIds(List<Integer> groupIds) {
        if (groupIds.isEmpty()) return Collections.emptyList();
        return dsl.selectFrom(TS_AUTH_FIELD_RESTRICTION)
                .where(TS_AUTH_FIELD_RESTRICTION.GROUP_ID.in(groupIds))
                .fetch(this::mapRestriction);
    }

    public void replaceFieldRestrictions(int groupId, List<AuthFieldRestriction> restrictions) {
        dsl.transaction(cfg -> {
            var tx = cfg.dsl();

            tx.deleteFrom(TS_AUTH_FIELD_RESTRICTION)
                    .where(TS_AUTH_FIELD_RESTRICTION.GROUP_ID.eq(groupId))
                    .execute();

            for (AuthFieldRestriction r : restrictions) {
                tx.insertInto(TS_AUTH_FIELD_RESTRICTION)
                        .set(TS_AUTH_FIELD_RESTRICTION.GROUP_ID, groupId)
                        .set(TS_AUTH_FIELD_RESTRICTION.RESOURCE_KEY, r.getResourceKey())
                        .set(TS_AUTH_FIELD_RESTRICTION.FIELD_KEY, r.getFieldKey())
                        .set(TS_AUTH_FIELD_RESTRICTION.OBJECT_TYPE_ID, r.getObjectTypeId() != null ? r.getObjectTypeId().shortValue() : null)
                        .execute();
            }
        });
    }

    private AuthPermission mapPermission(Record r) {
        AuthPermission p = new AuthPermission();
        p.setPermissionId(r.get(TS_AUTH_PERMISSION.PERMISSION_ID));
        p.setGroupId(r.get(TS_AUTH_PERMISSION.GROUP_ID));
        p.setResourceKey(r.get(TS_AUTH_PERMISSION.RESOURCE_KEY));
        Short typeId = r.get(TS_AUTH_PERMISSION.OBJECT_TYPE_ID);
        p.setObjectTypeId(typeId != null ? typeId.intValue() : null);
        p.setCanRead(Boolean.TRUE.equals(r.get(TS_AUTH_PERMISSION.CAN_READ)));
        p.setCanWrite(Boolean.TRUE.equals(r.get(TS_AUTH_PERMISSION.CAN_WRITE)));
        p.setCanDelete(Boolean.TRUE.equals(r.get(TS_AUTH_PERMISSION.CAN_DELETE)));
        return p;
    }

    private AuthFieldRestriction mapRestriction(Record r) {
        AuthFieldRestriction fr = new AuthFieldRestriction();
        fr.setRestrictionId(r.get(TS_AUTH_FIELD_RESTRICTION.RESTRICTION_ID));
        fr.setGroupId(r.get(TS_AUTH_FIELD_RESTRICTION.GROUP_ID));
        fr.setResourceKey(r.get(TS_AUTH_FIELD_RESTRICTION.RESOURCE_KEY));
        fr.setFieldKey(r.get(TS_AUTH_FIELD_RESTRICTION.FIELD_KEY));
        Short typeId = r.get(TS_AUTH_FIELD_RESTRICTION.OBJECT_TYPE_ID);
        fr.setObjectTypeId(typeId != null ? typeId.intValue() : null);
        return fr;
    }
}
