package com.kcic.connect.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_fulfillment_items", schema = "workflow")
public class RequestFulfillmentItem {

    @Id
    private UUID id;

    @Column(name = "fulfillment_id", nullable = false)
    private UUID fulfillmentId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_version_id")
    private UUID documentVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RequestFulfillmentItem() {
    }

    public RequestFulfillmentItem(UUID id, UUID fulfillmentId, UUID documentId, UUID documentVersionId,
                                  Instant now) {
        this.id = id;
        this.fulfillmentId = fulfillmentId;
        this.documentId = documentId;
        this.documentVersionId = documentVersionId;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFulfillmentId() {
        return fulfillmentId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getDocumentVersionId() {
        return documentVersionId;
    }
}
