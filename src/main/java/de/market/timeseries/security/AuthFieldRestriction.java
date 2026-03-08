package de.market.timeseries.security;

public class AuthFieldRestriction {
    private int restrictionId;
    private int groupId;
    private String resourceKey;
    private String fieldKey;
    private Integer objectTypeId; // nullable

    public int getRestrictionId() { return restrictionId; }
    public void setRestrictionId(int restrictionId) { this.restrictionId = restrictionId; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public Integer getObjectTypeId() { return objectTypeId; }
    public void setObjectTypeId(Integer objectTypeId) { this.objectTypeId = objectTypeId; }
}
