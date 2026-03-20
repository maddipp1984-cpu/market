package de.market.timeseries.repository;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.TableField;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Map;

import static de.market.jooq.generated.tables.TsCurrency.TS_CURRENCY;
import static de.market.jooq.generated.tables.TsHeader.TS_HEADER;
import static de.market.jooq.generated.tables.TsObject.TS_OBJECT;
import static de.market.jooq.generated.tables.TsUnit.TS_UNIT;
import static de.market.jooq.generated.tables.TsValuesDay.TS_VALUES_DAY;
import static de.market.jooq.generated.tables.TsValuesMonth.TS_VALUES_MONTH;
import static de.market.jooq.generated.tables.TsValuesYear.TS_VALUES_YEAR;
import static de.market.jooq.generated.tables.TsValues_15min.TS_VALUES_15MIN;
import static de.market.jooq.generated.tables.TsValues_1h.TS_VALUES_1H;
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
        // CTE: value_range — erste und letzte Datum pro Zeitreihe über alle 5 Dimensionen
        Field<Long> vrTsId = field(name("value_range", "ts_id"), Long.class);
        Field<Date> vrFirstDate = field(name("value_range", "first_date"), Date.class);
        Field<Date> vrLastDate = field(name("value_range", "last_date"), Date.class);

        CommonTableExpression<Record3<Long, Date, Date>> valueRange =
                name("value_range").fields("ts_id", "first_date", "last_date").as(
                        select(TS_VALUES_15MIN.TS_ID.cast(Long.class),
                                min(TS_VALUES_15MIN.TS_DATE).cast(Date.class),
                                max(TS_VALUES_15MIN.TS_DATE).cast(Date.class))
                                .from(TS_VALUES_15MIN).groupBy(TS_VALUES_15MIN.TS_ID)
                        .unionAll(
                                select(TS_VALUES_1H.TS_ID.cast(Long.class),
                                        min(TS_VALUES_1H.TS_DATE).cast(Date.class),
                                        max(TS_VALUES_1H.TS_DATE).cast(Date.class))
                                        .from(TS_VALUES_1H).groupBy(TS_VALUES_1H.TS_ID))
                        .unionAll(
                                select(TS_VALUES_DAY.TS_ID.cast(Long.class),
                                        min(TS_VALUES_DAY.TS_DATE).cast(Date.class),
                                        max(TS_VALUES_DAY.TS_DATE).cast(Date.class))
                                        .from(TS_VALUES_DAY).groupBy(TS_VALUES_DAY.TS_ID))
                        .unionAll(
                                select(TS_VALUES_MONTH.TS_ID.cast(Long.class),
                                        min(TS_VALUES_MONTH.TS_DATE).cast(Date.class),
                                        max(TS_VALUES_MONTH.TS_DATE).cast(Date.class))
                                        .from(TS_VALUES_MONTH).groupBy(TS_VALUES_MONTH.TS_ID))
                        .unionAll(
                                select(TS_VALUES_YEAR.TS_ID.cast(Long.class),
                                        min(yearToDate(TS_VALUES_YEAR.TS_YEAR)),
                                        max(yearToDate(TS_VALUES_YEAR.TS_YEAR)))
                                        .from(TS_VALUES_YEAR).groupBy(TS_VALUES_YEAR.TS_ID))
                );

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
                .with(valueRange)
                .select(
                        h.TS_ID.as("id"),
                        h.TS_KEY.as("key"),
                        dimensionLabel.as("dimension"),
                        u.SYMBOL.as("unit"),
                        c.ISO_CODE.as("currency"),
                        o.OBJECT_KEY.as("object"),
                        h.DESCRIPTION.as("description"),
                        vrFirstDate.as("firstDate"),
                        vrLastDate.as("lastDate"),
                        h.CREATED_AT.as("createdAt"),
                        h.UPDATED_AT.as("updatedAt")
                )
                .from(h)
                .join(u).on(u.UNIT_ID.eq(h.UNIT_ID))
                .leftJoin(c).on(c.CURRENCY_ID.eq(h.CURRENCY_ID))
                .leftJoin(o).on(o.OBJECT_ID.eq(h.OBJECT_ID))
                .leftJoin(valueRange).on(vrTsId.eq(h.TS_ID))
                .where(condition)
                .orderBy(h.TS_KEY)
                .fetchMaps();
    }

    /**
     * Konvertiert SMALLINT-Jahr zu DATE: CONCAT(year, '-01-01') → DATE.
     * jOOQ übersetzt concat() und cast(DATE) korrekt pro Dialekt.
     */
    private static Field<Date> yearToDate(TableField<?, Short> yearField) {
        return concat(yearField.cast(String.class), inline("-01-01")).cast(Date.class);
    }
}
