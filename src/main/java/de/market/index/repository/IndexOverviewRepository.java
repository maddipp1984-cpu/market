package de.market.index.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsIndex.TS_INDEX;
import static de.market.jooq.generated.tables.TsObject.TS_OBJECT;
import static de.market.jooq.generated.tables.TsHeader.TS_HEADER;
import static de.market.jooq.generated.tables.TsUnit.TS_UNIT;
import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;

@Repository
public class IndexOverviewRepository extends AbstractOverviewRepository {

    public IndexOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<Map<String, Object>> findAllAsRows() {
        return baseQuery()
                .orderBy(TS_OBJECT.OBJECT_KEY)
                .fetchMaps();
    }

    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return baseQuery()
                .where(condition)
                .orderBy(TS_OBJECT.OBJECT_KEY)
                .fetchMaps();
    }

    private SelectOnConditionStep<?> baseQuery() {
        return dsl
                .select(
                        TS_INDEX.INDEX_ID.as("id"),
                        TS_OBJECT.OBJECT_KEY.as("name"),
                        TS_OBJECT.DESCRIPTION.as("description"),
                        TS_HEADER.TIME_DIM.as("timeDim"),
                        TS_UNIT.SYMBOL.as("unit"),
                        TS_CURRENCY.ISO_CODE.as("currency"),
                        TS_HEADER.FIRST_DATE.as("firstDate"),
                        TS_HEADER.LAST_DATE.as("lastDate"),
                        TS_INDEX.CREATED_AT.as("createdAt")
                )
                .from(TS_INDEX)
                .join(TS_OBJECT).on(TS_INDEX.OBJECT_ID.eq(TS_OBJECT.OBJECT_ID))
                .join(TS_HEADER).on(TS_HEADER.OBJECT_ID.eq(TS_OBJECT.OBJECT_ID))
                .join(TS_UNIT).on(TS_HEADER.UNIT_ID.eq(TS_UNIT.UNIT_ID))
                .leftJoin(TS_CURRENCY).on(TS_HEADER.CURRENCY_ID.eq(TS_CURRENCY.CURRENCY_ID));
    }
}
