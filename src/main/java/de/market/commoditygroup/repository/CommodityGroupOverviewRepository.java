package de.market.commoditygroup.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsCommodityGroup.TS_COMMODITY_GROUP;

@Repository
public class CommodityGroupOverviewRepository extends AbstractOverviewRepository {

    public CommodityGroupOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<Map<String, Object>> findAllAsRows() {
        return dsl
                .select(
                        TS_COMMODITY_GROUP.COMMODITY_GROUP_ID.as("id"),
                        TS_COMMODITY_GROUP.NAME.as("name")
                )
                .from(TS_COMMODITY_GROUP)
                .orderBy(TS_COMMODITY_GROUP.NAME)
                .fetchMaps();
    }

    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return dsl
                .select(
                        TS_COMMODITY_GROUP.COMMODITY_GROUP_ID.as("id"),
                        TS_COMMODITY_GROUP.NAME.as("name")
                )
                .from(TS_COMMODITY_GROUP)
                .where(condition)
                .orderBy(TS_COMMODITY_GROUP.NAME)
                .fetchMaps();
    }
}
