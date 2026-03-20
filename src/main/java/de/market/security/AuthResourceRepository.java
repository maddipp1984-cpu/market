package de.market.security;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.*;

import static de.market.jooq.generated.tables.TsAuthResource.TS_AUTH_RESOURCE;

@Repository
public class AuthResourceRepository {

    private final DSLContext dsl;

    public AuthResourceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<AuthResource> findAll() {
        return dsl.selectFrom(TS_AUTH_RESOURCE)
                .orderBy(TS_AUTH_RESOURCE.RESOURCE_KEY)
                .fetch(r -> new AuthResource(
                        r.get(TS_AUTH_RESOURCE.RESOURCE_KEY),
                        r.get(TS_AUTH_RESOURCE.LABEL),
                        Boolean.TRUE.equals(r.get(TS_AUTH_RESOURCE.HAS_TYPE_SCOPE))
                ));
    }
}
