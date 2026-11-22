ALTER TABLE "authorization".resource_permissions
    ADD COLUMN include_descendants BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE "authorization".resource_permissions
    ADD CONSTRAINT resource_permissions_descendants_unit_chk
        CHECK (NOT include_descendants OR principal_type = 'ORGANIZATION_UNIT');

ALTER TABLE "authorization".user_role_assignments
    ADD COLUMN include_descendants BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE "authorization".user_role_assignments
    ADD CONSTRAINT user_role_assignments_descendants_unit_chk
        CHECK (NOT include_descendants OR scope_type = 'ORGANIZATION_UNIT');