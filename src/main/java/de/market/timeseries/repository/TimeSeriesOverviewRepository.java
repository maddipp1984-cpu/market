package de.market.timeseries.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;
import static de.market.jooq.generated.tables.TsHeader.TS_HEADER;
import static de.market.jooq.generated.tables.TsObject.TS_OBJECT;
import static de.market.jooq.generated.tables.TsUnit.TS_UNIT;
import static org.jooq.impl.DSL.*;

@Repository
public class TimeSeriesOverviewRepository extends AbstractOverviewRepository {

    public TimeSeriesOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<Map<String, Object>> findAllAsRows() {
        return buildAndFetch(noCondition());
    }

    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return buildAndFetch(condition);
    }

    private List<Map<String, Object>> buildAndFetch(Condition condition) {
        var h = TS_HEADER.as("h");
        var u = TS_UNIT.as("u");
        var c = TS_CURRENCY.as("c");
        var o = TS_OBJECT.as("o");

        Field<String> dimensionLabel = when(h.TIME_DIM.eq((short) 1), inline("15 Minuten"))
                .when(h.TIME_DIM.eq((short) 2), inline("1 Stunde"))
                .when(h.TIME_DIM.eq((short) 3), inline("Tag"))
                .when(h.TIME_DIM.eq((short) 4), inline("Monat"))
                .when(h.TIME_DIM.eq((short) 5), inline("Jahr"))
                .otherwise(inline(""));

        return dsl
                .select(
                        h.TS_ID.as("id"),
                        h.TS_KEY.as("key"),
                        dimensionLabel.as("dimension"),
                        u.SYMBOL.as("unit"),
                        c.ISO_CODE.as("currency"),
                        o.OBJECT_KEY.as("object"),
                        h.DESCRIPTION.as("description"),
                        h.FIRST_DATE.as("firstDate"),
                        h.LAST_DATE.as("lastDate"),
                        h.CREATED_AT.as("createdAt"),
                        h.UPDATED_AT.as("updatedAt")
                )
                .from(h)
                .join(u).on(u.UNIT_ID.eq(h.UNIT_ID))
                .leftJoin(c).on(c.CURRENCY_ID.eq(h.CURRENCY_ID))
                .leftJoin(o).on(o.OBJECT_ID.eq(h.OBJECT_ID))
                .where(condition)
                .orderBy(h.TS_KEY)
                .fetchMaps();
    }
}
