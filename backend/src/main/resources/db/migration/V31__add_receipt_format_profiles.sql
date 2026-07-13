CREATE TABLE receipt_format_profile (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(32) NOT NULL CHECK (scope IN ('STORE', 'BRANCH')),
    store_name_key VARCHAR(255) NOT NULL,
    store_branch_key VARCHAR(255) NOT NULL DEFAULT '',
    fingerprint VARCHAR(128) NOT NULL,
    fingerprint_version INTEGER NOT NULL CHECK (fingerprint_version > 0),
    profile_schema_version INTEGER NOT NULL CHECK (profile_schema_version = 1),
    version INTEGER NOT NULL CHECK (version > 0),
    predecessor_id BIGINT NULL REFERENCES receipt_format_profile(id) ON DELETE RESTRICT,
    profile_definition JSONB NOT NULL CHECK (
        jsonb_typeof(profile_definition) = 'object'
        AND profile_definition @> '{"schemaVersion": 1}'::jsonb
    ),
    state VARCHAR(32) NOT NULL DEFAULT 'QUARANTINE'
        CHECK (state IN ('QUARANTINE', 'ACTIVE', 'SUSPENDED', 'RETIRED')),
    source VARCHAR(32) NOT NULL
        CHECK (source IN ('AI_GENERATED', 'LEGACY_BOOTSTRAP', 'USER_CORRECTED')),
    activated_at TIMESTAMPTZ NULL,
    suspended_at TIMESTAMPTZ NULL,
    replaced_at TIMESTAMPTZ NULL,
    suspension_reason TEXT NULL,
    hit_count INTEGER NOT NULL DEFAULT 0 CHECK (hit_count >= 0),
    monitored_hit_count INTEGER NOT NULL DEFAULT 0 CHECK (monitored_hit_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_receipt_format_profile_version UNIQUE (
        scope,
        store_name_key,
        store_branch_key,
        fingerprint,
        fingerprint_version,
        version
    ),
    CONSTRAINT uq_receipt_format_profile_id_version UNIQUE (id, version),
    CONSTRAINT chk_receipt_format_profile_scope_branch CHECK (
        (scope = 'STORE' AND store_branch_key = '')
        OR (scope = 'BRANCH' AND store_branch_key <> '')
    ),
    CONSTRAINT chk_receipt_format_profile_predecessor CHECK (
        (version = 1 AND predecessor_id IS NULL)
        OR (version > 1 AND predecessor_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_receipt_format_profile_active_identity
    ON receipt_format_profile (scope, store_name_key, store_branch_key, fingerprint, fingerprint_version)
    WHERE state = 'ACTIVE';

CREATE INDEX idx_receipt_format_profile_lookup
    ON receipt_format_profile (
        state,
        store_name_key,
        store_branch_key,
        fingerprint,
        fingerprint_version,
        version DESC
    );

CREATE OR REPLACE FUNCTION validate_receipt_format_profile_predecessor()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    predecessor receipt_format_profile%ROWTYPE;
BEGIN
    IF NEW.predecessor_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT * INTO predecessor
    FROM receipt_format_profile
    WHERE id = NEW.predecessor_id;

    IF NOT FOUND
        OR predecessor.version <> NEW.version - 1
        OR predecessor.scope IS DISTINCT FROM NEW.scope
        OR predecessor.store_name_key IS DISTINCT FROM NEW.store_name_key
        OR predecessor.store_branch_key IS DISTINCT FROM NEW.store_branch_key
        OR predecessor.fingerprint IS DISTINCT FROM NEW.fingerprint
        OR predecessor.fingerprint_version IS DISTINCT FROM NEW.fingerprint_version THEN
        RAISE EXCEPTION 'receipt format profile predecessor must be the previous version of the same identity'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_receipt_format_profile_predecessor_valid
    BEFORE INSERT OR UPDATE ON receipt_format_profile
    FOR EACH ROW
    EXECUTE FUNCTION validate_receipt_format_profile_predecessor();

CREATE OR REPLACE FUNCTION reject_receipt_format_profile_definition_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.scope IS DISTINCT FROM OLD.scope
        OR NEW.store_name_key IS DISTINCT FROM OLD.store_name_key
        OR NEW.store_branch_key IS DISTINCT FROM OLD.store_branch_key
        OR NEW.fingerprint IS DISTINCT FROM OLD.fingerprint
        OR NEW.fingerprint_version IS DISTINCT FROM OLD.fingerprint_version
        OR NEW.profile_schema_version IS DISTINCT FROM OLD.profile_schema_version
        OR NEW.version IS DISTINCT FROM OLD.version
        OR NEW.predecessor_id IS DISTINCT FROM OLD.predecessor_id
        OR NEW.profile_definition IS DISTINCT FROM OLD.profile_definition
        OR NEW.source IS DISTINCT FROM OLD.source THEN
        RAISE EXCEPTION 'receipt format profile definitions are immutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_receipt_format_profile_definition_immutable
    BEFORE UPDATE ON receipt_format_profile
    FOR EACH ROW
    EXECUTE FUNCTION reject_receipt_format_profile_definition_update();

ALTER TABLE receipt
    DROP CONSTRAINT IF EXISTS receipt_parse_status_check;

ALTER TABLE receipt
    DROP CONSTRAINT IF EXISTS chk_receipt_parse_status;

ALTER TABLE receipt
    ADD CONSTRAINT chk_receipt_parse_status
        CHECK (parse_status IN ('PENDING', 'PARSED', 'PARSE_REVIEW', 'PARSE_ERROR', 'MANUALLY_EDITED')),
    ADD COLUMN format_profile_id BIGINT NULL,
    ADD COLUMN format_profile_version INTEGER NULL CHECK (format_profile_version IS NULL OR format_profile_version > 0),
    ADD CONSTRAINT chk_receipt_format_profile_pair CHECK (
        (format_profile_id IS NULL AND format_profile_version IS NULL)
        OR (format_profile_id IS NOT NULL AND format_profile_version IS NOT NULL)
    ),
    ADD CONSTRAINT fk_receipt_format_profile_version
        FOREIGN KEY (format_profile_id, format_profile_version)
        REFERENCES receipt_format_profile(id, version)
        ON DELETE RESTRICT;

ALTER TABLE receipt_item
    ADD COLUMN extraction_status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED'
        CHECK (extraction_status IN ('CONFIRMED', 'NEEDS_REVIEW'));

CREATE TABLE receipt_parse_trace (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES receipt(id) ON DELETE CASCADE,
    format_profile_id BIGINT NULL,
    format_profile_version INTEGER NULL CHECK (format_profile_version IS NULL OR format_profile_version > 0),
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    line_type VARCHAR(32) NOT NULL
        CHECK (line_type IN ('POSITION', 'METADATA', 'PAYMENT', 'TOTAL', 'TAX', 'IGNORED_SAFE', 'UNRESOLVED')),
    position_index INTEGER NULL CHECK (position_index IS NULL OR position_index >= 0),
    extracted_fields JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(extracted_fields) = 'object'),
    reason TEXT NULL,
    needs_review BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (receipt_id, line_number),
    CONSTRAINT chk_receipt_parse_trace_format_profile_pair CHECK (
        (format_profile_id IS NULL AND format_profile_version IS NULL)
        OR (format_profile_id IS NOT NULL AND format_profile_version IS NOT NULL)
    ),
    CONSTRAINT fk_receipt_parse_trace_format_profile_version
        FOREIGN KEY (format_profile_id, format_profile_version)
        REFERENCES receipt_format_profile(id, version)
        ON DELETE RESTRICT
);

CREATE INDEX idx_receipt_format_profile_predecessor_id ON receipt_format_profile(predecessor_id);
CREATE INDEX idx_receipt_format_profile_state ON receipt_format_profile(state);
CREATE INDEX idx_receipt_format_profile_id ON receipt(format_profile_id);
CREATE INDEX idx_receipt_item_extraction_status ON receipt_item(extraction_status);
CREATE INDEX idx_receipt_parse_trace_receipt_id ON receipt_parse_trace(receipt_id);
CREATE INDEX idx_receipt_parse_trace_profile_id ON receipt_parse_trace(format_profile_id);
