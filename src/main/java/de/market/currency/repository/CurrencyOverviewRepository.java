package de.market.currency.repository;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;

@Repository
public class CurrencyOverviewRepository {

    private final DSLContext dsl;

    public CurrencyOverviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Map<String, Object>> findAllAsRows() {
        return dsl
                .select(
                        TS_CURRENCY.CURRENCY_ID.as("id"),
                        TS_CURRENCY.ISO_CODE.as("isoCode"),
                        TS_CURRENCY.DESCRIPTION.as("description")
                )
                .from(TS_CURRENCY)
                .orderBy(TS_CURRENCY.ISO_CODE)
                .fetchMaps();
    }

    public List<Map<String, Object>> findFiltered(Condition condition) {
        return dsl
                .select(
                        TS_CURRENCY.CURRENCY_ID.as("id"),
                        TS_CURRENCY.ISO_CODE.as("isoCode"),
                        TS_CURRENCY.DESCRIPTION.as("description")
                )
                .from(TS_CURRENCY)
                .where(condition)
                .orderBy(TS_CURRENCY.ISO_CODE)
                .fetchMaps();
    }
}
