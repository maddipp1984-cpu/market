package de.market.timeseries.security;

public class AuthPermission {
    private int permissionId;
    private int groupId;
    private String resourceKey;
    private Integer objectTypeId; // nullable
    private boolean canRead;
    private boolean canWrite;
    private boolean canDelete;

    public int getPermissionId() { return permissionId; }
    public void setPermissionId(int permissionId) { this.permissionId = permissionId; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
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
}
