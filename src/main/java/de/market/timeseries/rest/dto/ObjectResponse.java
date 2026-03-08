package de.market.timeseries.rest.dto;

import de.market.timeseries.model.TsObject;

import java.time.OffsetDateTime;

public class ObjectResponse {

    private long objectId;
    private String type;
    private String objectKey;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ObjectResponse from(TsObject obj) {
        ObjectResponse r = new ObjectResponse();
        r.objectId = obj.getObjectId();
        r.type = obj.getObjectType().name();
        r.objectKey = obj.getObjectKey();
        r.description = obj.getDescription();
        r.createdAt = obj.getCreatedAt();
        r.updatedAt = obj.getUpdatedAt();
        return r;
    }

    public long getObjectId() { return objectId; }
    public String getType() { return type; }
    public String getObjectKey() { return objectKey; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
