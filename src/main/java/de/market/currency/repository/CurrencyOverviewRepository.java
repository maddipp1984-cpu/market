package de.market.currency.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;

@Repository
public class CurrencyOverviewRepository extends AbstractOverviewRepository {

    public CurrencyOverviewRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
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

    @Override
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
