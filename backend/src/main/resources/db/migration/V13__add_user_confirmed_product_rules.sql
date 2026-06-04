WITH seed_rules (category_name, match_value, match_type, priority) AS (
    VALUES
        ('Milchprodukte und Eier', 'JOGHURT SCHOKOL.', 'EXACT', 20),
        ('Milchprodukte und Eier', 'CREME LEGERE', 'EXACT', 20),
        ('Milchprodukte und Eier', 'HIMBEER M.VANIL.', 'EXACT', 20),
        ('Milchprodukte und Eier', 'STREICHGUT UNGES', 'EXACT', 20),

        ('Suesswaren und Snacks', 'SUPER GURKEN', 'EXACT', 20),
        ('Suesswaren und Snacks', 'ALPEN-MILCHCREME', 'EXACT', 20),
        ('Suesswaren und Snacks', 'ALPENMILCH 90G', 'EXACT', 20),

        ('Vorrat und Fertiggerichte', 'SAMT ERDBEERE', 'EXACT', 20)
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
