package de.market.businesspartner.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.BusinessPartner.BUSINESS_PARTNER;

@Repository
public class BusinessPartnerOverviewRepository {

    private final DSLContext dsl;

    public BusinessPartnerOverviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Map<String, Object>> findAllAsRows() {
        return dsl
                .select(
                        BUSINESS_PARTNER.ID.as("id"),
                        BUSINESS_PARTNER.SHORT_NAME.as("shortName"),
                        BUSINESS_PARTNER.NAME.as("name")
                )
                .from(BUSINESS_PARTNER)
                .orderBy(BUSINESS_PARTNER.SHORT_NAME)
                .fetchMaps();
    }
}
