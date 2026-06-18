ALTER TABLE receipt
    ADD COLUMN parse_source VARCHAR(32) NULL;

ALTER TABLE receipt
    ADD CONSTRAINT chk_receipt_parse_source
        CHECK (parse_source IS NULL OR parse_source IN ('RULE', 'AI', 'MANUAL_CORRECTED'));

CREATE TABLE ai_parsing_log (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NULL REFERENCES receipt(id) ON DELETE CASCADE,
    trigger VARCHAR(32) NOT NULL CHECK (trigger IN ('SYNC_AUTO', 'MANUAL_REPARSE', 'MANUAL_REPARSE_FORCE_FULL_TEXT', 'BULK_REPARSE', 'SETTINGS_TEST')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED', 'SKIPPED_LIMIT', 'INVALID_RESPONSE', 'LOW_CONFIDENCE', 'DISABLED', 'NO_API_KEY')),
    model_used VARCHAR(128) NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ NULL,
    duration_ms INTEGER NULL,
    prompt_tokens INTEGER NULL,
    completion_tokens INTEGER NULL,
    total_tokens INTEGER NULL,
    parse_error_before TEXT NULL,
    failure_reason TEXT NULL,
    overall_confidence NUMERIC(4,3) NULL CHECK (overall_confidence IS NULL OR (overall_confidence >= 0 AND overall_confidence <= 1)),
    field_confidence_json JSONB NULL,
    warnings_json JSONB NULL,
    prompt_snippet TEXT NULL,
    response_snippet TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE parse_rule_suggestion (
    id BIGSERIAL PRIMARY KEY,
    ai_parsing_log_id BIGINT NOT NULL REFERENCES ai_parsing_log(id) ON DELETE CASCADE,
    receipt_id BIGINT NULL REFERENCES receipt(id) ON DELETE CASCADE,
    store_name VARCHAR(255) NULL,
    rule_type VARCHAR(32) NOT NULL CHECK (rule_type IN ('DATE_PATTERN', 'STORE_PATTERN', 'ITEM_PATTERN', 'TOTAL_PATTERN', 'BONUS_PATTERN')),
    match_regex VARCHAR(1024) NOT NULL,
    extract_group VARCHAR(64) NULL,
    confidence NUMERIC(4,3) NULL CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    trigger VARCHAR(32) NOT NULL CHECK (trigger IN ('SYNC_AUTO', 'MANUAL_REPARSE', 'MANUAL_REPARSE_FORCE_FULL_TEXT', 'BULK_REPARSE', 'SETTINGS_TEST')),
    problem_description TEXT NOT NULL,
    solution_rationale TEXT NOT NULL,
    validation_status VARCHAR(32) NOT NULL CHECK (validation_status IN ('VALID', 'INVALID_REGEX', 'NO_MATCH', 'WRONG_EXTRACTION', 'COLLISION_RISK')),
    validation_message TEXT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED')),
    rejection_reason TEXT NULL,
    accepted_parse_rule_id BIGINT NULL REFERENCES parse_rule(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_receipt_parse_source ON receipt(parse_source);
CREATE INDEX idx_ai_parsing_log_receipt_id ON ai_parsing_log(receipt_id);
CREATE INDEX idx_ai_parsing_log_trigger ON ai_parsing_log(trigger);
CREATE INDEX idx_ai_parsing_log_status ON ai_parsing_log(status);
CREATE INDEX idx_parse_rule_suggestion_status ON parse_rule_suggestion(status);
CREATE INDEX idx_parse_rule_suggestion_receipt_id ON parse_rule_suggestion(receipt_id);

INSERT INTO app_settings (key, value, description) VALUES
    ('ai_parsing_fallback_enabled', 'true', 'OpenRouter KI-Parsing-Fallback aktivieren'),
    ('ai_parsing_model', 'google/gemini-flash-1.5', 'OpenRouter Modell fuer KI-Parsing'),
    ('ai_parsing_max_tokens', '2500', 'Max Tokens fuer KI-Parsing'),
    ('ai_parsing_temperature', '0.0', 'Temperature fuer KI-Parsing'),
    ('ai_parsing_min_confidence', '0.900', 'Minimale KI-Konfidenz fuer automatische Parser-Uebernahme'),
    ('ai_parsing_sync_call_limit', '25', 'Maximale KI-Parsing-Calls pro Sync-Lauf'),
    ('ai_parsing_text_mode', 'MINIMIZED', 'Textmodus fuer KI-Parsing: MINIMIZED oder FULL_TEXT'),
    ('ai_parsing_store_debug_snippets', 'false', 'Nur lokale Entwicklung: gekuerzte und maskierte KI-Prompt-/Antwort-Snippets speichern')
ON CONFLICT (key) DO NOTHING;
