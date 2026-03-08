package de.market.timeseries.model;

import java.time.OffsetDateTime;

public class TsObject {

    private Long objectId;
    private ObjectType objectType;
    private String objectKey;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public TsObject() {
    }

    public TsObject(ObjectType objectType, String objectKey) {
        this.objectType = objectType;
        this.objectKey = objectKey;
    }

    public TsObject(ObjectType objectType, String objectKey, String description) {
        this.objectType = objectType;
        this.objectKey = objectKey;
        this.description = description;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(ObjectType objectType) {
        this.objectType = objectType;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        return "TsObject{objectId=" + objectId + ", type=" + objectType
                + ", key='" + objectKey + "'" + ", description='" + description + "'}";
    }
}
