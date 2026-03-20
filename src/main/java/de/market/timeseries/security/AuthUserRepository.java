package de.market.timeseries.security;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.*;

import static de.market.jooq.generated.tables.TsAuthUser.TS_AUTH_USER;

@Repository
public class AuthUserRepository {

    private final DSLContext dsl;

    public AuthUserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<AuthUser> findById(UUID userId) {
        return dsl.selectFrom(TS_AUTH_USER)
                .where(TS_AUTH_USER.USER_ID.eq(userId))
                .fetchOptional(this::mapRow);
    }

    public List<AuthUser> findAll() {
        return dsl.selectFrom(TS_AUTH_USER)
                .orderBy(TS_AUTH_USER.USERNAME)
                .fetch(this::mapRow);
    }

    public void upsert(UUID userId, String username, String email) {
        dsl.insertInto(TS_AUTH_USER)
                .set(TS_AUTH_USER.USER_ID, userId)
                .set(TS_AUTH_USER.USERNAME, username)
                .set(TS_AUTH_USER.EMAIL, email)
                .onDuplicateKeyUpdate()
                .set(TS_AUTH_USER.USERNAME, username)
                .set(TS_AUTH_USER.EMAIL, email)
                .execute();
    }

    public void setAdmin(UUID userId, boolean admin) {
        dsl.update(TS_AUTH_USER)
                .set(TS_AUTH_USER.IS_ADMIN, admin)
                .where(TS_AUTH_USER.USER_ID.eq(userId))
                .execute();
    }

    private AuthUser mapRow(org.jooq.Record r) {
        AuthUser u = new AuthUser();
        u.setUserId(r.get(TS_AUTH_USER.USER_ID));
        u.setUsername(r.get(TS_AUTH_USER.USERNAME));
        u.setEmail(r.get(TS_AUTH_USER.EMAIL));
        u.setAdmin(Boolean.TRUE.equals(r.get(TS_AUTH_USER.IS_ADMIN)));
        u.setCreatedAt(r.get(TS_AUTH_USER.CREATED_AT));
        return u;
    }
}
