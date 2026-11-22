-- Domain namespaces from SCHEMA.md §2. PostgreSQL 13+ provides gen_random_uuid();
-- application code assigns UUID primary keys explicitly.
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS organization;
CREATE SCHEMA IF NOT EXISTS "authorization";
CREATE SCHEMA IF NOT EXISTS master;
CREATE SCHEMA IF NOT EXISTS repository;
CREATE SCHEMA IF NOT EXISTS workflow;
CREATE SCHEMA IF NOT EXISTS distribution;
CREATE SCHEMA IF NOT EXISTS template;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS audit;
