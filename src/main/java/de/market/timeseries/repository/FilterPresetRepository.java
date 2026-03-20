package de.market.timeseries.repository;

import de.market.timeseries.model.FilterPreset;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static de.market.jooq.generated.tables.TsFilterPreset.TS_FILTER_PRESET;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.when;

@Repository
public class FilterPresetRepository {

    private final DSLContext dsl;

    public FilterPresetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long create(FilterPreset preset) {
        Long id = dsl.insertInto(TS_FILTER_PRESET,
                        TS_FILTER_PRESET.PAGE_KEY,
                        TS_FILTER_PRESET.USER_ID,
                        TS_FILTER_PRESET.NAME,
                        TS_FILTER_PRESET.CONDITIONS,
                        TS_FILTER_PRESET.SCOPE,
                        TS_FILTER_PRESET.IS_DEFAULT)
                .values(
                        preset.getPageKey(),
                        preset.getUserId(),
                        preset.getName(),
                        JSONB.jsonb(preset.getConditions()),
                        preset.getScope(),
                        preset.isDefault())
                .returning(TS_FILTER_PRESET.PRESET_ID)
                .fetchOne(TS_FILTER_PRESET.PRESET_ID);

        preset.setPresetId(id);
        return id;
    }

    public List<FilterPreset> findByPageKey(String pageKey, String userId) {
        return dsl.select()
                .from(TS_FILTER_PRESET)
                .where(TS_FILTER_PRESET.PAGE_KEY.eq(pageKey))
                .and(TS_FILTER_PRESET.SCOPE.eq("GLOBAL")
                        .or(TS_FILTER_PRESET.USER_ID.eq(userId)))
                .orderBy(TS_FILTER_PRESET.SCOPE, TS_FILTER_PRESET.NAME)
                .fetch(this::mapRecord);
    }

    public boolean update(FilterPreset preset) {
        int rows = dsl.update(TS_FILTER_PRESET)
                .set(TS_FILTER_PRESET.NAME, preset.getName())
                .set(TS_FILTER_PRESET.CONDITIONS, JSONB.jsonb(preset.getConditions()))
                .set(TS_FILTER_PRESET.SCOPE, preset.getScope())
                .set(TS_FILTER_PRESET.IS_DEFAULT, preset.isDefault())
                .set(TS_FILTER_PRESET.UPDATED_AT, currentOffsetDateTime())
                .where(TS_FILTER_PRESET.PRESET_ID.eq(preset.getPresetId()))
                .execute();

        return rows > 0;
    }

    public boolean delete(long presetId) {
        int rows = dsl.deleteFrom(TS_FILTER_PRESET)
                .where(TS_FILTER_PRESET.PRESET_ID.eq(presetId))
                .execute();

        return rows > 0;
    }

    public void setDefault(long presetId, String pageKey, String scope, String userId) {
        dsl.transaction(cfg -> {
            var tx = cfg.dsl();

            // 1. Reset current default for this page/scope
            var resetQuery = tx.update(TS_FILTER_PRESET)
                    .set(TS_FILTER_PRESET.IS_DEFAULT, false)
                    .where(TS_FILTER_PRESET.PAGE_KEY.eq(pageKey))
                    .and(TS_FILTER_PRESET.SCOPE.eq(scope))
                    .and(TS_FILTER_PRESET.IS_DEFAULT.isTrue());

            if ("USER".equals(scope)) {
                resetQuery = resetQuery.and(TS_FILTER_PRESET.USER_ID.eq(userId));
            }
            resetQuery.execute();

            // 2. Set new default
            int updated = tx.update(TS_FILTER_PRESET)
                    .set(TS_FILTER_PRESET.IS_DEFAULT, true)
                    .where(TS_FILTER_PRESET.PRESET_ID.eq(presetId))
                    .execute();

            if (updated == 0) {
                throw new IllegalArgumentException("Preset nicht gefunden: presetId=" + presetId);
            }
        });
    }

    public void clearDefault(long presetId) {
        dsl.update(TS_FILTER_PRESET)
                .set(TS_FILTER_PRESET.IS_DEFAULT, false)
                .where(TS_FILTER_PRESET.PRESET_ID.eq(presetId))
                .execute();
    }

    public Optional<FilterPreset> findDefault(String pageKey, String userId) {
        return dsl.select()
                .from(TS_FILTER_PRESET)
                .where(TS_FILTER_PRESET.PAGE_KEY.eq(pageKey))
                .and(TS_FILTER_PRESET.IS_DEFAULT.isTrue())
                .and(TS_FILTER_PRESET.USER_ID.eq(userId)
                        .or(TS_FILTER_PRESET.SCOPE.eq("GLOBAL")))
                .orderBy(when(TS_FILTER_PRESET.USER_ID.eq(userId), inline(0)).otherwise(inline(1)))
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    public Optional<FilterPreset> findById(long presetId) {
        return dsl.select()
                .from(TS_FILTER_PRESET)
                .where(TS_FILTER_PRESET.PRESET_ID.eq(presetId))
                .fetchOptional(this::mapRecord);
    }

    private FilterPreset mapRecord(Record r) {
        FilterPreset preset = new FilterPreset();
        preset.setPresetId(r.get(TS_FILTER_PRESET.PRESET_ID));
        preset.setPageKey(r.get(TS_FILTER_PRESET.PAGE_KEY));
        preset.setUserId(r.get(TS_FILTER_PRESET.USER_ID));
        preset.setName(r.get(TS_FILTER_PRESET.NAME));
        JSONB jsonb = r.get(TS_FILTER_PRESET.CONDITIONS);
        preset.setConditions(jsonb != null ? jsonb.data() : null);
        preset.setScope(r.get(TS_FILTER_PRESET.SCOPE));
        preset.setDefault(r.get(TS_FILTER_PRESET.IS_DEFAULT));
        preset.setCreatedAt(r.get(TS_FILTER_PRESET.CREATED_AT));
        preset.setUpdatedAt(r.get(TS_FILTER_PRESET.UPDATED_AT));
        return preset;
    }
}
