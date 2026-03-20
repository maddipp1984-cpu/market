package de.market.timeseries.repository;

import de.market.timeseries.model.Currency;
import de.market.timeseries.model.TimeDimension;
import de.market.timeseries.model.TimeSeriesHeader;
import de.market.timeseries.model.Unit;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static de.market.jooq.generated.tables.TsHeader.TS_HEADER;
import static org.jooq.impl.DSL.currentOffsetDateTime;

@Repository
public class HeaderRepository {

    private final DSLContext dsl;

    public HeaderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long create(TimeSeriesHeader header) {
        Long id = dsl.insertInto(TS_HEADER)
                .set(TS_HEADER.TS_KEY, header.getTsKey())
                .set(TS_HEADER.TIME_DIM, (short) header.getTimeDimension().getCode())
                .set(TS_HEADER.UNIT_ID, (short) header.getUnit().getCode())
                .set(TS_HEADER.CURRENCY_ID, header.getCurrency() != null ? (short) header.getCurrency().getCode() : null)
                .set(TS_HEADER.OBJECT_ID, header.getObjectId())
                .set(TS_HEADER.DESCRIPTION, header.getDescription())
                .returning(TS_HEADER.TS_ID)
                .fetchOne()
                .get(TS_HEADER.TS_ID);

        header.setTsId(id);
        return id;
    }

    public Optional<TimeSeriesHeader> findById(long tsId) {
        return dsl.selectFrom(TS_HEADER)
                .where(TS_HEADER.TS_ID.eq(tsId))
                .fetchOptional(this::mapRow);
    }

    public List<TimeSeriesHeader> findByIds(List<Long> tsIds) {
        return dsl.selectFrom(TS_HEADER)
                .where(TS_HEADER.TS_ID.in(tsIds))
                .fetch(this::mapRow);
    }

    public Optional<TimeSeriesHeader> findByKey(String tsKey) {
        return dsl.selectFrom(TS_HEADER)
                .where(TS_HEADER.TS_KEY.eq(tsKey))
                .fetchOptional(this::mapRow);
    }

    public List<TimeSeriesHeader> findByDimension(TimeDimension dimension) {
        return dsl.selectFrom(TS_HEADER)
                .where(TS_HEADER.TIME_DIM.eq((short) dimension.getCode()))
                .fetch(this::mapRow);
    }

    public boolean update(TimeSeriesHeader header) {
        int rows = dsl.update(TS_HEADER)
                .set(TS_HEADER.TS_KEY, header.getTsKey())
                .set(TS_HEADER.UNIT_ID, (short) header.getUnit().getCode())
                .set(TS_HEADER.CURRENCY_ID, header.getCurrency() != null ? (short) header.getCurrency().getCode() : null)
                .set(TS_HEADER.OBJECT_ID, header.getObjectId())
                .set(TS_HEADER.DESCRIPTION, header.getDescription())
                .set(TS_HEADER.UPDATED_AT, currentOffsetDateTime())
                .where(TS_HEADER.TS_ID.eq(header.getTsId()))
                .execute();
        return rows > 0;
    }

    public boolean updateObjectId(long tsId, Long objectId) {
        int rows = dsl.update(TS_HEADER)
                .set(TS_HEADER.OBJECT_ID, objectId)
                .where(TS_HEADER.TS_ID.eq(tsId))
                .execute();
        return rows > 0;
    }

    public List<TimeSeriesHeader> findByObjectId(long objectId) {
        return dsl.selectFrom(TS_HEADER)
                .where(TS_HEADER.OBJECT_ID.eq(objectId))
                .fetch(this::mapRow);
    }

    public boolean delete(long tsId) {
        int rows = dsl.deleteFrom(TS_HEADER)
                .where(TS_HEADER.TS_ID.eq(tsId))
                .execute();
        return rows > 0;
    }

    private TimeSeriesHeader mapRow(Record r) {
        TimeSeriesHeader h = new TimeSeriesHeader();
        h.setTsId(r.get(TS_HEADER.TS_ID));
        h.setTsKey(r.get(TS_HEADER.TS_KEY));
        h.setTimeDimension(TimeDimension.fromCode(r.get(TS_HEADER.TIME_DIM)));
        h.setUnit(Unit.fromCode(r.get(TS_HEADER.UNIT_ID)));
        Short currencyId = r.get(TS_HEADER.CURRENCY_ID);
        if (currencyId != null) {
            h.setCurrency(Currency.fromCode(currencyId));
        }
        h.setObjectId(r.get(TS_HEADER.OBJECT_ID));
        h.setDescription(r.get(TS_HEADER.DESCRIPTION));
        h.setCreatedAt(r.get(TS_HEADER.CREATED_AT));
        h.setUpdatedAt(r.get(TS_HEADER.UPDATED_AT));
        return h;
    }
}
