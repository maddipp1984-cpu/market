package de.market.businesspartner.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.BusinessPartner.BUSINESS_PARTNER;

@Repository
public class BusinessPartnerOverviewRepository extends AbstractOverviewRepository {

    public BusinessPartnerOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
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

    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return dsl
                .select(
                        BUSINESS_PARTNER.ID.as("id"),
                        BUSINESS_PARTNER.SHORT_NAME.as("shortName"),
                        BUSINESS_PARTNER.NAME.as("name")
                )
                .from(BUSINESS_PARTNER)
                .where(condition)
                .orderBy(BUSINESS_PARTNER.SHORT_NAME)
                .fetchMaps();
    }
}
