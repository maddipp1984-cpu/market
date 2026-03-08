package de.market.timeseries.model;

import java.time.OffsetDateTime;

public class FilterPreset {

    private Long presetId;
    private String pageKey;
    private String userId;
    private String name;
    private String conditions; // raw JSON string
    private String scope;
    private boolean isDefault;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public FilterPreset() {
    }

    public Long getPresetId() {
        return presetId;
    }

    public void setPresetId(Long presetId) {
        this.presetId = presetId;
    }

    public String getPageKey() {
        return pageKey;
    }

    public void setPageKey(String pageKey) {
        this.pageKey = pageKey;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "FilterPreset{presetId=" + presetId + ", pageKey='" + pageKey + "'"
                + ", name='" + name + "', scope='" + scope + "', isDefault=" + isDefault + "}";
    }
}
