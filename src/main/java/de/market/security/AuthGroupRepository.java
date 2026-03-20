package de.market.security;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.*;

import static de.market.jooq.generated.tables.TsAuthGroup.TS_AUTH_GROUP;
import static de.market.jooq.generated.tables.TsAuthGroupMember.TS_AUTH_GROUP_MEMBER;

@Repository
public class AuthGroupRepository {

    private final DSLContext dsl;

    public AuthGroupRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<AuthGroup> findAll() {
        return dsl.selectFrom(TS_AUTH_GROUP)
                .orderBy(TS_AUTH_GROUP.NAME)
                .fetch(this::mapRow);
    }

    public Optional<AuthGroup> findById(int groupId) {
        return dsl.selectFrom(TS_AUTH_GROUP)
                .where(TS_AUTH_GROUP.GROUP_ID.eq(groupId))
                .fetchOptional(this::mapRow);
    }

    public int create(String name, String description) {
        return dsl.insertInto(TS_AUTH_GROUP)
                .set(TS_AUTH_GROUP.NAME, name)
                .set(TS_AUTH_GROUP.DESCRIPTION, description)
                .returning(TS_AUTH_GROUP.GROUP_ID)
                .fetchOne()
                .get(TS_AUTH_GROUP.GROUP_ID);
    }

    public void update(int groupId, String name, String description) {
        dsl.update(TS_AUTH_GROUP)
                .set(TS_AUTH_GROUP.NAME, name)
                .set(TS_AUTH_GROUP.DESCRIPTION, description)
                .where(TS_AUTH_GROUP.GROUP_ID.eq(groupId))
                .execute();
    }

    public void delete(int groupId) {
        dsl.deleteFrom(TS_AUTH_GROUP)
                .where(TS_AUTH_GROUP.GROUP_ID.eq(groupId))
                .execute();
    }

    public void addMember(int groupId, UUID userId) {
        dsl.insertInto(TS_AUTH_GROUP_MEMBER)
                .set(TS_AUTH_GROUP_MEMBER.GROUP_ID, groupId)
                .set(TS_AUTH_GROUP_MEMBER.USER_ID, userId)
                .onDuplicateKeyIgnore()
                .execute();
    }

    public void removeMember(int groupId, UUID userId) {
        dsl.deleteFrom(TS_AUTH_GROUP_MEMBER)
                .where(TS_AUTH_GROUP_MEMBER.GROUP_ID.eq(groupId))
                .and(TS_AUTH_GROUP_MEMBER.USER_ID.eq(userId))
                .execute();
    }

    public List<UUID> findMemberIds(int groupId) {
        return dsl.select(TS_AUTH_GROUP_MEMBER.USER_ID)
                .from(TS_AUTH_GROUP_MEMBER)
                .where(TS_AUTH_GROUP_MEMBER.GROUP_ID.eq(groupId))
                .fetch(TS_AUTH_GROUP_MEMBER.USER_ID);
    }

    public List<Integer> findGroupIdsForUser(UUID userId) {
        return dsl.select(TS_AUTH_GROUP_MEMBER.GROUP_ID)
                .from(TS_AUTH_GROUP_MEMBER)
                .where(TS_AUTH_GROUP_MEMBER.USER_ID.eq(userId))
                .fetch(TS_AUTH_GROUP_MEMBER.GROUP_ID);
    }

    public int countMembers(int groupId) {
        return dsl.selectCount()
                .from(TS_AUTH_GROUP_MEMBER)
                .where(TS_AUTH_GROUP_MEMBER.GROUP_ID.eq(groupId))
                .fetchOne(0, int.class);
    }

    private AuthGroup mapRow(Record r) {
        AuthGroup g = new AuthGroup();
        g.setGroupId(r.get(TS_AUTH_GROUP.GROUP_ID));
        g.setName(r.get(TS_AUTH_GROUP.NAME));
        g.setDescription(r.get(TS_AUTH_GROUP.DESCRIPTION));
        return g;
    }
}
