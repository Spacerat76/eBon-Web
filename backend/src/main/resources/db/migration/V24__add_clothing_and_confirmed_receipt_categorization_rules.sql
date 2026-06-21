INSERT INTO category (name, color_hex, icon, sort_order, is_active)
VALUES ('Kleidung und Schuhe', '#7B1FA2', 'tag', 65, true)
ON CONFLICT (name) DO NOTHING;

WITH seed_rules (category_name, match_value, match_type, priority) AS (
    VALUES
        ('Baby und Kind', 'B-OUTDOOR', 'CONTAINS', 15),
        ('Baby und Kind', 'B-WAESCHE', 'CONTAINS', 15),
        ('Fleisch und Wurst', 'E.REGIO.PAPR.LYON', 'CONTAINS', 15),
        ('Fleisch und Wurst', 'Versch.Sorten', 'EXACT', 15),
        ('Milchprodukte und Eier', 'G&G B.O.Butte', 'CONTAINS', 20),
        ('Suesswaren und Snacks', 'Bounty Minis', 'CONTAINS', 20),
        ('Suesswaren und Snacks', 'Super Dickmanns', 'CONTAINS', 20),
        ('Baumarkt und Garten', 'Chlortaß-Super', 'CONTAINS', 40),
        ('Baumarkt und Garten', 'Edelgeranie', 'CONTAINS', 40),
        ('Baumarkt und Garten', 'Windrad bunt', 'CONTAINS', 40),
        ('Haushalt', 'Wellenbox', 'CONTAINS', 40),
        ('Kleidung und Schuhe', 'Da/He Bio Pantolette', 'CONTAINS', 40)
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

WITH confirmed_rules (category_name, match_value, match_type) AS (
    VALUES
        ('Baby und Kind', 'B-OUTDOOR', 'CONTAINS'),
        ('Baby und Kind', 'B-WAESCHE', 'CONTAINS'),
        ('Fleisch und Wurst', 'E.REGIO.PAPR.LYON', 'CONTAINS'),
        ('Fleisch und Wurst', 'Versch.Sorten', 'EXACT'),
        ('Milchprodukte und Eier', 'G&G B.O.Butte', 'CONTAINS'),
        ('Suesswaren und Snacks', 'Bounty Minis', 'CONTAINS'),
        ('Suesswaren und Snacks', 'Super Dickmanns', 'CONTAINS'),
        ('Baumarkt und Garten', 'Chlortaß-Super', 'CONTAINS'),
        ('Baumarkt und Garten', 'Edelgeranie', 'CONTAINS'),
        ('Baumarkt und Garten', 'Windrad bunt', 'CONTAINS'),
        ('Haushalt', 'Wellenbox', 'CONTAINS'),
        ('Kleidung und Schuhe', 'Da/He Bio Pantolette', 'CONTAINS')
)
UPDATE receipt_item item
SET category_id = category.id,
    category_source = 'RULE'
FROM confirmed_rules rule
JOIN category ON category.name = rule.category_name
WHERE item.category_id IS NULL
  AND item.category_source IS NULL
  AND item.is_manually_edited = false
  AND (
      (rule.match_type = 'EXACT' AND lower(item.description) = lower(rule.match_value))
      OR (rule.match_type = 'CONTAINS' AND lower(item.description) LIKE '%' || lower(rule.match_value) || '%')
  );
