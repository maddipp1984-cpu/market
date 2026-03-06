package de.projekt.timeseries.security;

import java.util.HashSet;
import java.util.Set;

public class EffectivePermission {
    private String resourceKey;
    private Integer objectTypeId; // nullable = resource-level
    private boolean canRead;
    private boolean canWrite;
    private boolean canDelete;
    private Set<String> restrictedFields = new HashSet<>();

    public EffectivePermission() {}

    public EffectivePermission(String resourceKey, Integer objectTypeId) {
        this.resourceKey = resourceKey;
        this.objectTypeId = objectTypeId;
    }

    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    public Integer getObjectTypeId() { return objectTypeId; }
    public void setObjectTypeId(Integer objectTypeId) { this.objectTypeId = objectTypeId; }
    public boolean isCanRead() { return canRead; }
    public void setCanRead(boolean canRead) { this.canRead = canRead; }
    public boolean isCanWrite() { return canWrite; }
    public void setCanWrite(boolean canWrite) { this.canWrite = canWrite; }
    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }
    public Set<String> getRestrictedFields() { return restrictedFields; }
    public void setRestrictedFields(Set<String> restrictedFields) { this.restrictedFields = restrictedFields; }
}
