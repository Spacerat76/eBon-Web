INSERT INTO category (name, color_hex, icon, sort_order, is_active)
VALUES ('Salat', '#7CB342', 'salad', 11, true)
ON CONFLICT (name) DO NOTHING;

WITH seed_rules (category_name, match_value, match_type, priority) AS (
    VALUES
        ('Salat', 'COUNTRY MIX', 'EXACT', 25)
)
INSERT INTO categorization_rule (category_id, match_field, match_value, match_type, priority, is_active)
SELECT c.id, 'DESCRIPTION', seed_rules.match_value, seed_rules.match_type, seed_rules.priority, true
FROM seed_rules
JOIN category c ON c.name = seed_rules.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM categorization_rule existing
    WHERE existing.category_id = c.id
      AND existing.match_field = 'DESCRIPTION'
      AND lower(existing.match_value) = lower(seed_rules.match_value)
      AND existing.match_type = seed_rules.match_type
);
