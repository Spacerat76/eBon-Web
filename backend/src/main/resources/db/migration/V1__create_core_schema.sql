CREATE TABLE category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    color_hex CHAR(7) NULL CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    icon VARCHAR(64) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE receipt (
    id BIGSERIAL PRIMARY KEY,
    paperless_document_id INTEGER NOT NULL UNIQUE,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    receipt_date DATE NULL,
    receipt_time TIME NULL,
    store_name VARCHAR(255) NULL,
    store_branch VARCHAR(255) NULL,
    total_amount NUMERIC(10,2) NULL,
    currency CHAR(3) NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$'),
    raw_text TEXT NOT NULL,
    bonus_balance NUMERIC(10,2) NULL,
    bonus_points NUMERIC(10,2) NULL,
    bonus_type VARCHAR(64) NULL,
    parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (parse_status IN ('PENDING', 'PARSED', 'PARSE_ERROR', 'MANUALLY_EDITED')),
    parse_error_message TEXT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL,
    delete_reason VARCHAR(32) NULL CHECK (delete_reason IS NULL OR delete_reason IN ('USER_DELETED', 'TAG_REMOVED')),
    CHECK (
        (deleted_at IS NULL AND delete_reason IS NULL)
        OR (deleted_at IS NOT NULL AND delete_reason IS NOT NULL)
    )
);

CREATE TABLE receipt_item (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES receipt(id) ON DELETE CASCADE,
    position_index INTEGER NOT NULL,
    description VARCHAR(512) NOT NULL,
    quantity NUMERIC(10,3) NULL,
    unit VARCHAR(32) NULL,
    unit_price NUMERIC(10,2) NULL,
    total_price NUMERIC(10,2) NOT NULL,
    discount_amount NUMERIC(10,2) NULL,
    category_id BIGINT NULL REFERENCES category(id) ON DELETE SET NULL,
    category_source VARCHAR(32) NULL CHECK (category_source IS NULL OR category_source IN ('RULE', 'AI', 'MANUAL')),
    is_manually_edited BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (receipt_id, position_index)
);

CREATE TABLE categorization_rule (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    match_field VARCHAR(32) NOT NULL CHECK (match_field IN ('DESCRIPTION', 'STORE_NAME')),
    match_type VARCHAR(32) NOT NULL CHECK (match_type IN ('CONTAINS', 'STARTS_WITH', 'ENDS_WITH', 'EXACT', 'REGEX')),
    match_value VARCHAR(512) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_categorization_log (
    id BIGSERIAL PRIMARY KEY,
    receipt_item_id BIGINT NOT NULL REFERENCES receipt_item(id) ON DELETE CASCADE,
    prompt_sent TEXT NOT NULL,
    response_received TEXT NOT NULL,
    assigned_category_id BIGINT NULL REFERENCES category(id) ON DELETE SET NULL,
    ai_confidence NUMERIC(4,3) NULL CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 1)),
    model_used VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE app_settings (
    key VARCHAR(128) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE parse_rule (
    id BIGSERIAL PRIMARY KEY,
    store_name VARCHAR(255) NULL,
    rule_type VARCHAR(32) NOT NULL
        CHECK (rule_type IN ('DATE_PATTERN', 'STORE_PATTERN', 'ITEM_PATTERN', 'TOTAL_PATTERN', 'BONUS_PATTERN')),
    match_regex VARCHAR(1024) NOT NULL,
    extract_group VARCHAR(64) NULL,
    confidence NUMERIC(4,3) NULL CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    hit_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMPTZ NULL,
    source VARCHAR(32) NOT NULL CHECK (source IN ('MANUAL', 'AI_ADAPTED')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sync_log (
    id BIGSERIAL PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    new_documents_count INTEGER NOT NULL DEFAULT 0,
    removed_documents_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NULL
);

CREATE TABLE sync_log_entry (
    id BIGSERIAL PRIMARY KEY,
    sync_log_id BIGINT NOT NULL REFERENCES sync_log(id) ON DELETE CASCADE,
    paperless_document_id INTEGER NULL,
    action VARCHAR(32) NOT NULL CHECK (action IN ('IMPORTED', 'TAG_REMOVED', 'SKIPPED')),
    receipt_id BIGINT NULL REFERENCES receipt(id) ON DELETE SET NULL,
    details TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_receipt_receipt_date ON receipt(receipt_date);
CREATE INDEX idx_receipt_store_name ON receipt(store_name);
CREATE INDEX idx_receipt_bonus_type ON receipt(bonus_type);
CREATE INDEX idx_receipt_item_receipt_id ON receipt_item(receipt_id);
CREATE INDEX idx_receipt_item_description_fts ON receipt_item USING GIN (to_tsvector('simple', description));
CREATE INDEX idx_receipt_item_category_id ON receipt_item(category_id);
CREATE INDEX idx_categorization_rule_priority ON categorization_rule(priority);
CREATE INDEX idx_parse_rule_store_name ON parse_rule(store_name);
CREATE INDEX idx_parse_rule_rule_type ON parse_rule(rule_type);
CREATE INDEX idx_parse_rule_is_active ON parse_rule(is_active);
CREATE INDEX idx_sync_log_started_at ON sync_log(started_at);
CREATE INDEX idx_sync_log_entry_sync_log_id ON sync_log_entry(sync_log_id);
