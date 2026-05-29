-- Flyway V1: initial schema for eBon backend
-- Creates core domain tables and spec-related tables

CREATE TABLE category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    color_hex VARCHAR(50),
    icon VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE receipt (
    id BIGSERIAL PRIMARY KEY,
    paperless_document_id INTEGER NOT NULL UNIQUE,
    imported_at TIMESTAMP WITH TIME ZONE,
    receipt_date DATE,
    receipt_time TIME,
    store_name VARCHAR(255),
    store_branch VARCHAR(255),
    total_amount NUMERIC(19,4),
    currency VARCHAR(10),
    raw_text TEXT NOT NULL,
    bonus_balance NUMERIC(19,4),
    bonus_points NUMERIC(19,4),
    bonus_type VARCHAR(100),
    parse_status VARCHAR(50) NOT NULL,
    parse_error_message TEXT,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE receipt_item (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES receipt(id) ON DELETE CASCADE,
    position_index INTEGER NOT NULL,
    description TEXT NOT NULL,
    quantity NUMERIC(19,4),
    unit VARCHAR(50),
    unit_price NUMERIC(19,4),
    total_price NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4),
    category_id BIGINT REFERENCES category(id),
    category_source VARCHAR(50),
    is_manually_edited BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Rules and AI logs
CREATE TABLE parse_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    store_name_pattern VARCHAR(255),
    regex TEXT,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE categorization_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    pattern TEXT,
    category_id BIGINT REFERENCES category(id),
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE ai_categorization_log (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT REFERENCES receipt(id),
    request_payload TEXT,
    response_payload TEXT,
    model VARCHAR(255),
    cost NUMERIC(10,4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Sync audit tables
CREATE TABLE sync_log (
    id BIGSERIAL PRIMARY KEY,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    finished_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50),
    total_documents INTEGER,
    succeeded INTEGER,
    failed INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE sync_log_entry (
    id BIGSERIAL PRIMARY KEY,
    sync_log_id BIGINT REFERENCES sync_log(id) ON DELETE CASCADE,
    paperless_document_id INTEGER,
    action VARCHAR(50),
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Generic key/value for runtime settings
CREATE TABLE app_settings (
    key VARCHAR(255) PRIMARY KEY,
    value TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
