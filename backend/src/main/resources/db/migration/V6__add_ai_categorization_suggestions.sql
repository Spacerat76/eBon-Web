ALTER TABLE ai_categorization_log
    ADD COLUMN suggested_category_id BIGINT NULL REFERENCES category(id) ON DELETE SET NULL,
    ADD COLUMN suggested_category_name VARCHAR(128) NULL,
    ADD COLUMN rejection_reason VARCHAR(32) NULL;

ALTER TABLE ai_categorization_log
    ADD CONSTRAINT chk_ai_categorization_log_rejection_reason
        CHECK (
            rejection_reason IS NULL
            OR rejection_reason IN ('LOW_CONFIDENCE', 'UNKNOWN_CATEGORY', 'INVALID_RESPONSE')
        );
