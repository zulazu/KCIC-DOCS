-- Phase 6: fulfillment external-link metadata and request code sequence.
-- The workflow tables themselves are created by V7.
ALTER TABLE workflow.request_fulfillments ADD COLUMN external_url VARCHAR(2048);
ALTER TABLE workflow.request_fulfillments ADD COLUMN external_link_title VARCHAR(200);

CREATE SEQUENCE workflow.request_code_seq START WITH 1 INCREMENT BY 1;

CREATE INDEX requests_target_unit_idx ON workflow.document_requests (target_organization_unit_id);
CREATE INDEX requests_target_document_idx ON workflow.document_requests (target_document_id);
