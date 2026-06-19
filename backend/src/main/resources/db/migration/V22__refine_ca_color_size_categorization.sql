UPDATE categorization_rule rule
SET is_active = false
FROM category category
WHERE category.id = rule.category_id
  AND category.name = 'Baumarkt und Garten'
  AND rule.match_field = 'DESCRIPTION'
  AND rule.match_type = 'CONTAINS'
  AND upper(rule.match_value) = 'FARBE';

WITH seed_rules (category_name, match_value, match_type, priority) AS (
    VALUES
        ('Baby und Kind', 'BABY-HOSE', 'CONTAINS', 15),
        ('Baby und Kind', 'J BB TROUSERS', 'CONTAINS', 15)
)
INSERT INTO categorization_rule (category_id, match_field, match_value, match_type, priority, is_active)
SELECT category.id, 'DESCRIPTION', seed_rules.match_value, seed_rules.match_type, seed_rules.priority, true
FROM seed_rules
JOIN category ON category.name = seed_rules.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM categorization_rule existing
    WHERE existing.category_id = category.id
      AND existing.match_field = 'DESCRIPTION'
      AND lower(existing.match_value) = lower(seed_rules.match_value)
      AND existing.match_type = seed_rules.match_type
);

WITH ca_baby_item_patterns (match_value) AS (
    VALUES
        ('KI-TAGESW'),
        ('BABY-TOPS'),
        ('BABY-COMBI'),
        ('B-ACCESS'),
        ('J BB TOPS'),
        ('BABY-HOSE'),
        ('J BB TROUSERS')
)
UPDATE receipt_item item
SET category_id = category.id,
    category_source = 'RULE'
FROM receipt receipt,
     category category
WHERE item.receipt_id = receipt.id
  AND category.name = 'Baby und Kind'
  AND item.category_source = 'RULE'
  AND item.is_manually_edited = false
  AND lower(receipt.store_name) = 'c&a'
  AND EXISTS (
      SELECT 1
      FROM ca_baby_item_patterns pattern
      WHERE upper(item.description) LIKE '%' || pattern.match_value || '%'
  );
