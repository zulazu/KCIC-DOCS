CREATE TABLE distribution.distributions (
    id                 UUID PRIMARY KEY,
    distribution_code  VARCHAR(50) NOT NULL UNIQUE,
    sender_user_id     UUID NOT NULL,
    title              VARCHAR(200) NOT NULL,
    message            TEXT,
    distribution_status VARCHAR(30) NOT NULL,
    sent_at            TIMESTAMPTZ,
    expires_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    row_version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT distributions_status_chk CHECK (distribution_status IN ('DRAFT', 'SENT', 'PARTIALLY_REVOKED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT distributions_expiry_chk CHECK (expires_at IS NULL OR sent_at IS NULL OR expires_at > sent_at),
    CONSTRAINT distributions_sender_fk FOREIGN KEY (sender_user_id) REFERENCES identity.users (id)
);

CREATE INDEX distributions_sender_idx ON distribution.distributions (sender_user_id, created_at DESC);
CREATE INDEX distributions_status_idx ON distribution.distributions (distribution_status, sent_at DESC);

CREATE TABLE distribution.distribution_items (
    id                  UUID PRIMARY KEY,
    distribution_id     UUID NOT NULL,
    document_id         UUID NOT NULL,
    document_version_id UUID NOT NULL,
    permission_id       UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT distribution_items_distribution_fk FOREIGN KEY (distribution_id) REFERENCES distribution.distributions (id),
    CONSTRAINT distribution_items_document_fk FOREIGN KEY (document_id) REFERENCES repository.documents (id),
    CONSTRAINT distribution_items_version_fk FOREIGN KEY (document_version_id) REFERENCES repository.document_versions (id),
    CONSTRAINT distribution_items_permission_fk FOREIGN KEY (permission_id) REFERENCES "authorization".permissions (id),
    CONSTRAINT distribution_items_unique_uq UNIQUE (distribution_id, document_id, document_version_id)
);

CREATE INDEX distribution_items_document_idx ON distribution.distribution_items (document_id, document_version_id);

CREATE TABLE distribution.distribution_recipients (
    id                UUID PRIMARY KEY,
    distribution_id   UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    recipient_status  VARCHAR(20) NOT NULL,
    sent_at           TIMESTAMPTZ NOT NULL,
    read_at           TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT distribution_recipients_status_chk CHECK (recipient_status IN ('SENT', 'READ', 'REVOKED', 'EXPIRED')),
    CONSTRAINT distribution_recipients_unique_uq UNIQUE (distribution_id, recipient_user_id),
    CONSTRAINT distribution_recipients_distribution_fk FOREIGN KEY (distribution_id) REFERENCES distribution.distributions (id),
    CONSTRAINT distribution_recipients_user_fk FOREIGN KEY (recipient_user_id) REFERENCES identity.users (id)
);

CREATE INDEX distribution_recipients_received_idx ON distribution.distribution_recipients (recipient_user_id, recipient_status, sent_at DESC);

CREATE TABLE distribution.distribution_access_grants (
    id                     UUID PRIMARY KEY,
    distribution_recipient_id UUID NOT NULL,
    distribution_item_id   UUID NOT NULL,
    resource_permission_id UUID NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT distribution_access_grants_unique_uq UNIQUE (distribution_recipient_id, distribution_item_id, resource_permission_id),
    CONSTRAINT distribution_access_grants_recipient_fk FOREIGN KEY (distribution_recipient_id) REFERENCES distribution.distribution_recipients (id),
    CONSTRAINT distribution_access_grants_item_fk FOREIGN KEY (distribution_item_id) REFERENCES distribution.distribution_items (id),
    CONSTRAINT distribution_access_grants_permission_fk FOREIGN KEY (resource_permission_id) REFERENCES "authorization".resource_permissions (id)
);

CREATE TABLE distribution.distribution_events (
    id                      UUID PRIMARY KEY,
    distribution_id         UUID NOT NULL,
    distribution_recipient_id UUID,
    actor_user_id           UUID,
    event_type              VARCHAR(30) NOT NULL,
    event_note              TEXT,
    occurred_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT distribution_events_distribution_fk FOREIGN KEY (distribution_id) REFERENCES distribution.distributions (id),
    CONSTRAINT distribution_events_recipient_fk FOREIGN KEY (distribution_recipient_id) REFERENCES distribution.distribution_recipients (id),
    CONSTRAINT distribution_events_actor_fk FOREIGN KEY (actor_user_id) REFERENCES identity.users (id)
);

CREATE INDEX distribution_events_distribution_idx ON distribution.distribution_events (distribution_id, occurred_at DESC);
