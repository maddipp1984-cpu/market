package de.market.timeseries.rest.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.market.timeseries.model.FilterPreset;

import java.time.OffsetDateTime;

public class FilterPresetResponse {

    private long presetId;
    private String pageKey;
    private String userId;
    private String name;
    private Object conditions;
    private String scope;
    private boolean isDefault;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static FilterPresetResponse from(FilterPreset preset, ObjectMapper mapper) {
        FilterPresetResponse r = new FilterPresetResponse();
        r.presetId = preset.getPresetId();
        r.pageKey = preset.getPageKey();
        r.userId = preset.getUserId();
        r.name = preset.getName();
        r.scope = preset.getScope();
        r.isDefault = preset.isDefault();
        r.createdAt = preset.getCreatedAt();
        r.updatedAt = preset.getUpdatedAt();

        // Parse JSON string to Object for proper JSON serialization
        try {
            r.conditions = mapper.readValue(preset.getConditions(), Object.class);
        } catch (Exception e) {
            r.conditions = preset.getConditions();
        }

        return r;
    }

    public long getPresetId() { return presetId; }
    public String getPageKey() { return pageKey; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public Object getConditions() { return conditions; }
    public String getScope() { return scope; }
    public boolean isDefault() { return isDefault; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
