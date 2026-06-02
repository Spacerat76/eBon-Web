UPDATE receipt_item
SET category_source = NULL
WHERE category_id IS NULL;

ALTER TABLE receipt_item
    ADD CONSTRAINT chk_receipt_item_category_source_requires_category
    CHECK (
        (category_id IS NULL AND category_source IS NULL)
        OR (category_id IS NOT NULL AND category_source IS NOT NULL)
    );
