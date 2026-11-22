CREATE TABLE workflow.document_requests (
    id                         UUID PRIMARY KEY,
    request_code               VARCHAR(50) NOT NULL UNIQUE,
    request_type               VARCHAR(30) NOT NULL,
    requester_user_id          UUID NOT NULL,
    title                      VARCHAR(200) NOT NULL,
    description                TEXT,
    reason                     TEXT NOT NULL,
    target_document_id         UUID,
    target_owner_user_id       UUID,
    target_organization_unit_id UUID,
    assigned_to_user_id        UUID,
    needed_by_date             DATE,
    request_status             VARCHAR(30) NOT NULL,
    submitted_at               TIMESTAMPTZ,
    closed_at                  TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    row_version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT requests_type_chk CHECK (request_type IN ('ACCESS', 'DOCUMENT_DELIVERY', 'TEMPLATE')),
    CONSTRAINT requests_status_chk CHECK (request_status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'IN_PROGRESS', 'FULFILLED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT requests_reason_nonblank_chk CHECK (LENGTH(BTRIM(reason)) > 0),
    CONSTRAINT requests_access_target_chk CHECK (request_type <> 'ACCESS' OR target_document_id IS NOT NULL),
    CONSTRAINT requests_requester_fk FOREIGN KEY (requester_user_id) REFERENCES identity.users (id),
    CONSTRAINT requests_target_document_fk FOREIGN KEY (target_document_id) REFERENCES repository.documents (id),
    CONSTRAINT requests_target_owner_fk FOREIGN KEY (target_owner_user_id) REFERENCES identity.users (id),
    CONSTRAINT requests_target_unit_fk FOREIGN KEY (target_organization_unit_id) REFERENCES organization.organization_units (id),
    CONSTRAINT requests_assigned_to_fk FOREIGN KEY (assigned_to_user_id) REFERENCES identity.users (id)
);

CREATE INDEX requests_requester_idx ON workflow.document_requests (requester_user_id, created_at DESC);
CREATE INDEX requests_assigned_idx ON workflow.document_requests (assigned_to_user_id, request_status);
CREATE INDEX requests_status_idx ON workflow.document_requests (request_status, created_at DESC);

CREATE TABLE workflow.request_attachments (
    id                UUID PRIMARY KEY,
    request_id        UUID NOT NULL,
    storage_object_id UUID NOT NULL,
    uploaded_by       UUID NOT NULL,
    attached_at       TIMESTAMPTZ NOT NULL,
    attachment_status VARCHAR(20) NOT NULL,
    CONSTRAINT request_attachments_status_chk CHECK (attachment_status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT request_attachments_request_fk FOREIGN KEY (request_id) REFERENCES workflow.document_requests (id),
    CONSTRAINT request_attachments_storage_fk FOREIGN KEY (storage_object_id) REFERENCES repository.storage_objects (id),
    CONSTRAINT request_attachments_uploaded_by_fk FOREIGN KEY (uploaded_by) REFERENCES identity.users (id)
);

CREATE INDEX request_attachments_request_idx ON workflow.request_attachments (request_id, attached_at DESC);

CREATE TABLE workflow.request_approvals (
    id                UUID PRIMARY KEY,
    request_id        UUID NOT NULL,
    approver_user_id  UUID NOT NULL,
    decision          VARCHAR(20) NOT NULL,
    decision_reason   TEXT,
    assigned_at       TIMESTAMPTZ NOT NULL,
    decided_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT request_approvals_decision_chk CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT request_approvals_request_fk FOREIGN KEY (request_id) REFERENCES workflow.document_requests (id),
    CONSTRAINT request_approvals_approver_fk FOREIGN KEY (approver_user_id) REFERENCES identity.users (id)
);

CREATE INDEX request_approvals_inbox_idx ON workflow.request_approvals (approver_user_id, decision, assigned_at DESC);
CREATE INDEX request_approvals_request_idx ON workflow.request_approvals (request_id);

CREATE TABLE workflow.request_fulfillments (
    id                  UUID PRIMARY KEY,
    request_id          UUID NOT NULL,
    fulfillment_type    VARCHAR(30) NOT NULL,
    fulfilled_by_user_id UUID NOT NULL,
    fulfillment_note    TEXT,
    fulfilled_at        TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT request_fulfillments_type_chk CHECK (fulfillment_type IN ('ACCESS_GRANTED', 'DOCUMENT_LINKED', 'DOCUMENT_UPLOADED', 'EXTERNAL_LINK_REGISTERED')),
    CONSTRAINT request_fulfillments_request_fk FOREIGN KEY (request_id) REFERENCES workflow.document_requests (id),
    CONSTRAINT request_fulfillments_fulfilled_by_fk FOREIGN KEY (fulfilled_by_user_id) REFERENCES identity.users (id)
);

CREATE TABLE workflow.request_fulfillment_items (
    id                  UUID PRIMARY KEY,
    fulfillment_id      UUID NOT NULL,
    document_id         UUID NOT NULL,
    document_version_id UUID,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT request_fulfillment_items_fulfillment_fk FOREIGN KEY (fulfillment_id) REFERENCES workflow.request_fulfillments (id),
    CONSTRAINT request_fulfillment_items_document_fk FOREIGN KEY (document_id) REFERENCES repository.documents (id),
    CONSTRAINT request_fulfillment_items_version_fk FOREIGN KEY (document_version_id) REFERENCES repository.document_versions (id)
);

CREATE TABLE workflow.request_histories (
    id            UUID PRIMARY KEY,
    request_id    UUID NOT NULL,
    actor_user_id UUID,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30) NOT NULL,
    comment       TEXT,
    metadata      JSONB,
    occurred_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT request_histories_request_fk FOREIGN KEY (request_id) REFERENCES workflow.document_requests (id),
    CONSTRAINT request_histories_actor_fk FOREIGN KEY (actor_user_id) REFERENCES identity.users (id)
);

CREATE INDEX request_histories_request_idx ON workflow.request_histories (request_id, occurred_at);
