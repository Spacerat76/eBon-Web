WITH seed_rules (category_name, match_value, match_type, priority) AS (
    VALUES
        ('Suesswaren und Snacks', 'FF Ungarisch 175g', 'CONTAINS', 20),
        ('Suesswaren und Snacks', 'Chocr Sahne&amp;Weiße', 'CONTAINS', 20),
        ('Suesswaren und Snacks', 'ORIGINAL NFB', 'EXACT', 20),

        ('Baby und Kind', 'Schlupfhose', 'CONTAINS', 15),
        ('Haushalt', 'KQRo XXL', 'CONTAINS', 90),
        ('Brot und Backwaren', 'Büsch auf 30Die.', 'EXACT', 50),

        ('Fleisch und Wurst', 'HINTERSCHINK', 'CONTAINS', 15),
        ('Milchprodukte und Eier', 'Parmigiano', 'CONTAINS', 20),
        ('Milchprodukte und Eier', 'GRAN DUETT', 'CONTAINS', 20),

        ('Vorrat und Fertiggerichte', 'Erdb.Konfi', 'CONTAINS', 20),
        ('Vorrat und Fertiggerichte', 'SENF MITTELSCH.', 'EXACT', 20),
        ('Vorrat und Fertiggerichte', 'MIRACEL WHIP', 'EXACT', 20),
        ('Vorrat und Fertiggerichte', 'CORNICHONS CHILI', 'EXACT', 20),

        ('Getraenke', 'ALT BV', 'EXACT', 55)
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
