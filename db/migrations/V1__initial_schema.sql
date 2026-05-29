-- V1: Initial schema for eBon Expense Tracker
-- Creates core tables and indexes as described in the specification

-- Categories
CREATE TABLE category (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL UNIQUE,
  color_hex CHAR(7),
  icon VARCHAR(64),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INTEGER NOT NULL DEFAULT 0
);

-- Categorization rules
CREATE TABLE categorization_rule (
  id BIGSERIAL PRIMARY KEY,
  category_id BIGINT NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
  match_field VARCHAR(32) NOT NULL,
  match_type VARCHAR(32) NOT NULL,
  match_value VARCHAR(512) NOT NULL,
  priority INTEGER NOT NULL DEFAULT 100,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Parse rules (auto-learned)
CREATE TABLE parse_rule (
  id BIGSERIAL PRIMARY KEY,
  store_name VARCHAR(255),
  rule_type VARCHAR(32) NOT NULL,
  match_regex VARCHAR(1024) NOT NULL,
  extract_group VARCHAR(64),
  confidence NUMERIC(4,3),
  hit_count INTEGER NOT NULL DEFAULT 0,
  last_used_at TIMESTAMPTZ,
  source VARCHAR(32) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Receipts
CREATE TABLE receipt (
  id BIGSERIAL PRIMARY KEY,
  paperless_document_id INTEGER NOT NULL UNIQUE,
  imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  receipt_date DATE,
  receipt_time TIME,
  store_name VARCHAR(255),
  store_branch VARCHAR(255),
  total_amount NUMERIC(10,2),
  currency CHAR(3) NOT NULL DEFAULT 'EUR',
  raw_text TEXT NOT NULL,
  bonus_balance NUMERIC(10,2),
  bonus_points NUMERIC(10,2),
  bonus_type VARCHAR(64),
  parse_status VARCHAR(32) NOT NULL,
  parse_error_message TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Receipt items
CREATE TABLE receipt_item (
  id BIGSERIAL PRIMARY KEY,
  receipt_id BIGINT NOT NULL REFERENCES receipt(id) ON DELETE CASCADE,
  position_index INTEGER NOT NULL,
  description VARCHAR(512) NOT NULL,
  quantity NUMERIC(10,3),
  unit VARCHAR(32),
  unit_price NUMERIC(10,2),
  total_price NUMERIC(10,2) NOT NULL,
  discount_amount NUMERIC(10,2),
  category_id BIGINT REFERENCES category(id),
  category_source VARCHAR(32),
  is_manually_edited BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- AI categorization log
CREATE TABLE ai_categorization_log (
  id BIGSERIAL PRIMARY KEY,
  receipt_item_id BIGINT NOT NULL REFERENCES receipt_item(id) ON DELETE CASCADE,
  prompt_sent TEXT NOT NULL,
  response_received TEXT NOT NULL,
  assigned_category_id BIGINT REFERENCES category(id),
  ai_confidence NUMERIC(4,3),
  model_used VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Application settings
CREATE TABLE app_settings (
  key VARCHAR(128) PRIMARY KEY,
  value TEXT NOT NULL,
  description TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sync logs
CREATE TABLE sync_log (
  id BIGSERIAL PRIMARY KEY,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,
  status VARCHAR(32) NOT NULL,
  new_documents_count INTEGER NOT NULL DEFAULT 0,
  removed_documents_count INTEGER NOT NULL DEFAULT 0,
  error_message TEXT
);

CREATE TABLE sync_log_entry (
  id BIGSERIAL PRIMARY KEY,
  sync_log_id BIGINT NOT NULL REFERENCES sync_log(id) ON DELETE CASCADE,
  paperless_document_id INTEGER,
  action VARCHAR(32) NOT NULL,
  receipt_id BIGINT,
  details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_receipt_receipt_date ON receipt(receipt_date);
CREATE INDEX idx_receipt_store_name ON receipt(store_name);
CREATE INDEX idx_receipt_item_receipt_id ON receipt_item(receipt_id);
-- Fulltext GIN index for receipt_item.description
CREATE INDEX idx_receipt_item_description_tsv ON receipt_item USING GIN (to_tsvector('simple', description));
CREATE INDEX idx_parse_rule_store_name ON parse_rule(store_name);
CREATE INDEX idx_parse_rule_rule_type ON parse_rule(rule_type);
CREATE INDEX idx_parse_rule_is_active ON parse_rule(is_active);
CREATE INDEX idx_sync_log_started_at ON sync_log(started_at);
CREATE INDEX idx_sync_log_entry_sync_log_id ON sync_log_entry(sync_log_id);
