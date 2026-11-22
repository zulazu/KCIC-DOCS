CREATE TABLE identity.users (
    id                  UUID PRIMARY KEY,
    username            VARCHAR(100) NOT NULL,
    email               VARCHAR(254),
    display_name        VARCHAR(160) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    account_status      VARCHAR(20) NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_count  INTEGER NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    deactivated_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    row_version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT users_account_status_chk CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    CONSTRAINT users_failed_login_chk CHECK (failed_login_count >= 0),
    CONSTRAINT users_actor_created_fk FOREIGN KEY (created_by) REFERENCES identity.users (id),
    CONSTRAINT users_actor_updated_fk FOREIGN KEY (updated_by) REFERENCES identity.users (id)
);

CREATE UNIQUE INDEX users_username_lower_uq ON identity.users (LOWER(username));
CREATE UNIQUE INDEX users_email_lower_uq ON identity.users (LOWER(email)) WHERE email IS NOT NULL;
CREATE INDEX users_account_status_idx ON identity.users (account_status);
CREATE INDEX users_last_login_idx ON identity.users (last_login_at);

-- Spring Session JDBC official PostgreSQL schema (technical tables, not product domain data).
CREATE TABLE spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT NOT NULL,
    last_access_time      BIGINT NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time           BIGINT NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
