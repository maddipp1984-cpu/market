package de.market.seriestype.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsSeriesType.TS_SERIES_TYPE;
import static org.jooq.impl.DSL.*;

@Repository
public class SeriesTypeOverviewRepository extends AbstractOverviewRepository {

    public SeriesTypeOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<Map<String, Object>> findAllAsRows() {
        return dsl
                .select(
                        TS_SERIES_TYPE.SERIES_TYPE_ID.as("id"),
                        TS_SERIES_TYPE.CODE.as("code"),
                        TS_SERIES_TYPE.NAME.as("name"),
                        categoryLabel().as("category")
                )
                .from(TS_SERIES_TYPE)
                .orderBy(TS_SERIES_TYPE.CODE)
                .fetchMaps();
    }

    @Override
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return dsl
                .select(
                        TS_SERIES_TYPE.SERIES_TYPE_ID.as("id"),
                        TS_SERIES_TYPE.CODE.as("code"),
                        TS_SERIES_TYPE.NAME.as("name"),
                        categoryLabel().as("category")
                )
                .from(TS_SERIES_TYPE)
                .where(condition)
                .orderBy(TS_SERIES_TYPE.CODE)
                .fetchMaps();
    }

    private static org.jooq.Field<String> categoryLabel() {
        return when(TS_SERIES_TYPE.CATEGORY.eq((short) 1), inline("Finanziell"))
                .when(TS_SERIES_TYPE.CATEGORY.eq((short) 2), inline("Physikalisch"))
                .otherwise(inline(""));
    }
}
