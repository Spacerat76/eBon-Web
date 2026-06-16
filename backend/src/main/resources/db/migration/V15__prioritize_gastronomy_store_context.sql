UPDATE categorization_rule
SET priority = 25
WHERE category_id = (SELECT id FROM category WHERE name = 'Gastronomie')
    AND match_field = 'STORE_NAME'
    AND priority > 25;

WITH seed_rules(category_name, match_field, match_type, match_value, priority) AS (
    VALUES
        ('Gastronomie', 'DESCRIPTION', 'CONTAINS', 'CHEESEBURGER', 25),
        ('Gastronomie', 'DESCRIPTION', 'CONTAINS', 'HAMBURGER ROYAL', 25),
        ('Gastronomie', 'DESCRIPTION', 'CONTAINS', 'BIG MAC', 25),
        ('Gastronomie', 'DESCRIPTION', 'CONTAINS', 'MCCHICKEN', 25),
        ('Gastronomie', 'DESCRIPTION', 'CONTAINS', 'MCMENU', 25)
)
INSERT INTO categorization_rule (category_id, match_field, match_type, match_value, priority)
SELECT category.id, seed_rules.match_field, seed_rules.match_type, seed_rules.match_value, seed_rules.priority
FROM seed_rules
JOIN category ON category.name = seed_rules.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM categorization_rule existing_rule
    WHERE existing_rule.category_id = category.id
        AND existing_rule.match_field = seed_rules.match_field
        AND existing_rule.match_type = seed_rules.match_type
        AND existing_rule.match_value = seed_rules.match_value
        AND existing_rule.priority = seed_rules.priority
);
