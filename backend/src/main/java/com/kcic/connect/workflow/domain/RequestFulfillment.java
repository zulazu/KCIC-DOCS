package com.kcic.connect.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_fulfillments", schema = "workflow")
public class RequestFulfillment {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 30)
    private FulfillmentType fulfillmentType;

    @Column(name = "fulfilled_by_user_id", nullable = false)
    private UUID fulfilledByUserId;

    @Column(name = "fulfillment_note", columnDefinition = "text")
    private String fulfillmentNote;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(name = "external_link_title", length = 200)
    private String externalLinkTitle;

    @Column(name = "fulfilled_at", nullable = false)
    private Instant fulfilledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RequestFulfillment() {
    }

    public RequestFulfillment(UUID id, UUID requestId, FulfillmentType fulfillmentType,
                              UUID fulfilledByUserId, String fulfillmentNote, String externalUrl,
                              String externalLinkTitle, Instant now) {
        this.id = id;
        this.requestId = requestId;
        this.fulfillmentType = fulfillmentType;
        this.fulfilledByUserId = fulfilledByUserId;
        this.fulfillmentNote = fulfillmentNote;
        this.externalUrl = externalUrl;
        this.externalLinkTitle = externalLinkTitle;
        this.fulfilledAt = now;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public FulfillmentType getFulfillmentType() {
        return fulfillmentType;
    }

    public UUID getFulfilledByUserId() {
        return fulfilledByUserId;
    }

    public String getFulfillmentNote() {
        return fulfillmentNote;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getExternalLinkTitle() {
        return externalLinkTitle;
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }
}
