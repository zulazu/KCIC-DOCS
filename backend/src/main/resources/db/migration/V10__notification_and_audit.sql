CREATE TABLE notification.notifications (
    id                   UUID PRIMARY KEY,
    recipient_user_id    UUID NOT NULL,
    notification_type    VARCHAR(60) NOT NULL,
    title                VARCHAR(180) NOT NULL,
    message              TEXT NOT NULL,
    target_resource_type VARCHAR(40),
    target_resource_id   UUID,
    read_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    expires_at           TIMESTAMPTZ,
    safe_metadata        JSONB,
    CONSTRAINT notifications_recipient_fk FOREIGN KEY (recipient_user_id) REFERENCES identity.users (id)
);

CREATE INDEX notifications_recipient_idx ON notification.notifications (recipient_user_id, created_at DESC);
CREATE INDEX notifications_unread_idx ON notification.notifications (recipient_user_id, read_at, created_at DESC);

CREATE TABLE audit.audit_events (
    id            UUID PRIMARY KEY,
    actor_user_id UUID,
    action_code   VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id   UUID,
    outcome       VARCHAR(20) NOT NULL,
    source        VARCHAR(40) NOT NULL,
    request_id    VARCHAR(100),
    safe_metadata JSONB,
    occurred_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT audit_events_outcome_chk CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT audit_events_source_chk CHECK (source IN ('WEB', 'API', 'SYSTEM')),
    CONSTRAINT audit_events_actor_fk FOREIGN KEY (actor_user_id) REFERENCES identity.users (id)
);

CREATE INDEX audit_events_occurred_idx ON audit.audit_events (occurred_at DESC);
CREATE INDEX audit_events_actor_idx ON audit.audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX audit_events_resource_idx ON audit.audit_events (resource_type, resource_id, occurred_at DESC);
CREATE INDEX audit_events_action_idx ON audit.audit_events (action_code, occurred_at DESC);
CREATE INDEX audit_events_outcome_idx ON audit.audit_events (outcome, occurred_at DESC);
