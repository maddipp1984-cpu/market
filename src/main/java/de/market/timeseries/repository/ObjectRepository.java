package de.market.timeseries.repository;

import de.market.timeseries.model.ObjectType;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static de.market.jooq.generated.tables.TsObject.TS_OBJECT;
import static org.jooq.impl.DSL.currentOffsetDateTime;

@Repository
public class ObjectRepository {

    private final DSLContext dsl;

    public ObjectRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long create(de.market.timeseries.model.TsObject object) {
        Long id = dsl.insertInto(TS_OBJECT)
                .set(TS_OBJECT.TYPE_ID, (short) object.getObjectType().getCode())
                .set(TS_OBJECT.OBJECT_KEY, object.getObjectKey())
                .set(TS_OBJECT.DESCRIPTION, object.getDescription())
                .returning(TS_OBJECT.OBJECT_ID)
                .fetchOne()
                .get(TS_OBJECT.OBJECT_ID);

        object.setObjectId(id);
        return id;
    }

    public Optional<de.market.timeseries.model.TsObject> findById(long objectId) {
        return dsl.selectFrom(TS_OBJECT)
                .where(TS_OBJECT.OBJECT_ID.eq(objectId))
                .fetchOptional(this::mapRow);
    }

    public Optional<de.market.timeseries.model.TsObject> findByKey(String objectKey) {
        return dsl.selectFrom(TS_OBJECT)
                .where(TS_OBJECT.OBJECT_KEY.eq(objectKey))
                .fetchOptional(this::mapRow);
    }

    public List<de.market.timeseries.model.TsObject> findAll() {
        return dsl.selectFrom(TS_OBJECT)
                .orderBy(TS_OBJECT.OBJECT_KEY)
                .fetch(this::mapRow);
    }

    public List<de.market.timeseries.model.TsObject> findByType(ObjectType type) {
        return dsl.selectFrom(TS_OBJECT)
                .where(TS_OBJECT.TYPE_ID.eq((short) type.getCode()))
                .fetch(this::mapRow);
    }

    public boolean update(de.market.timeseries.model.TsObject object) {
        int rows = dsl.update(TS_OBJECT)
                .set(TS_OBJECT.TYPE_ID, (short) object.getObjectType().getCode())
                .set(TS_OBJECT.OBJECT_KEY, object.getObjectKey())
                .set(TS_OBJECT.DESCRIPTION, object.getDescription())
                .set(TS_OBJECT.UPDATED_AT, currentOffsetDateTime())
                .where(TS_OBJECT.OBJECT_ID.eq(object.getObjectId()))
                .execute();
        return rows > 0;
    }

    public boolean delete(long objectId) {
        int rows = dsl.deleteFrom(TS_OBJECT)
                .where(TS_OBJECT.OBJECT_ID.eq(objectId))
                .execute();
        return rows > 0;
    }

    public List<de.market.timeseries.model.TsObject> findFiltered(Condition condition) {
        return dsl.selectFrom(TS_OBJECT)
                .where(condition)
                .orderBy(TS_OBJECT.OBJECT_KEY)
                .fetch(this::mapRow);
    }

    private de.market.timeseries.model.TsObject mapRow(Record r) {
        de.market.timeseries.model.TsObject obj = new de.market.timeseries.model.TsObject();
        obj.setObjectId(r.get(TS_OBJECT.OBJECT_ID));
        obj.setObjectType(ObjectType.fromCode(r.get(TS_OBJECT.TYPE_ID)));
        obj.setObjectKey(r.get(TS_OBJECT.OBJECT_KEY));
        obj.setDescription(r.get(TS_OBJECT.DESCRIPTION));
        obj.setCreatedAt(r.get(TS_OBJECT.CREATED_AT));
        obj.setUpdatedAt(r.get(TS_OBJECT.UPDATED_AT));
        return obj;
    }
}
