WITH seed_rules (category_name, match_value, store_name, priority) AS (
    VALUES
        ('Fleisch und Wurst', 'SPARERIBS', NULL, 15),
        ('Fleisch und Wurst', 'RD HUEFTE', NULL, 15),
        ('Fleisch und Wurst', 'ROULADE FRZ', NULL, 15),
        ('Vorrat und Fertiggerichte', 'KIPA GEF. VEGAN', NULL, 65),
        ('Vorrat und Fertiggerichte', 'Nasi Goreng', NULL, 65),
        ('Vorrat und Fertiggerichte', 'Baml Goreng', NULL, 65),
        ('Vorrat und Fertiggerichte', 'CORNICHONS KRAEU', NULL, 65),
        ('Vorrat und Fertiggerichte', 'DELIKATESS SENF', NULL, 65),
        ('Vorrat und Fertiggerichte', 'TAFELMEERRETTICH', NULL, 65),
        ('Suesswaren und Snacks', 'TRIOLADE', NULL, 35),
        ('Suesswaren und Snacks', 'Verano Vanilla', NULL, 35),
        ('Haushalt', 'FH-DOSE 450ML', NULL, 90),
        ('Haushalt', 'TIEFKUEHLTASCHE', NULL, 90),
        ('Haushalt', 'Paradies Baby C Power', NULL, 90),
        ('Haushalt', 'Paradies Micro AAA 4 St', NULL, 90),
        ('Baby und Kind', 'Mayben B&K Sonnencreme 100ml', NULL, 75),
        ('Baby und Kind', 'SauBär Badezubehör Pad', NULL, 75),
        ('Koerperpflege', 'essence Nagelkleber fix it!', NULL, 80),
        ('Koerperpflege', 'o.b.ExtraProtect Super 42St', NULL, 80),
        ('Lebensmittel', 'LEBENSMITTEL', NULL, 120),
        ('Fleisch und Wurst', 'BEDIENUNGSTHEKE', 'REWE', 15)
)
INSERT INTO categorization_rule (
    category_id, match_field, match_value, match_type, store_name, priority, is_active
)
SELECT category.id, 'DESCRIPTION', seed.match_value, 'EXACT', seed.store_name, seed.priority, true
FROM seed_rules seed
JOIN category ON category.name = seed.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM categorization_rule existing
    WHERE existing.category_id = category.id
      AND existing.match_field = 'DESCRIPTION'
      AND existing.match_type = 'EXACT'
      AND lower(existing.match_value) = lower(seed.match_value)
      AND lower(COALESCE(existing.store_name, '')) = lower(COALESCE(seed.store_name, ''))
);

WITH seed_rules (category_name, match_value, store_name) AS (
    VALUES
        ('Fleisch und Wurst', 'SPARERIBS', NULL),
        ('Fleisch und Wurst', 'RD HUEFTE', NULL),
        ('Fleisch und Wurst', 'ROULADE FRZ', NULL),
        ('Vorrat und Fertiggerichte', 'KIPA GEF. VEGAN', NULL),
        ('Vorrat und Fertiggerichte', 'Nasi Goreng', NULL),
        ('Vorrat und Fertiggerichte', 'Baml Goreng', NULL),
        ('Vorrat und Fertiggerichte', 'CORNICHONS KRAEU', NULL),
        ('Vorrat und Fertiggerichte', 'DELIKATESS SENF', NULL),
        ('Vorrat und Fertiggerichte', 'TAFELMEERRETTICH', NULL),
        ('Suesswaren und Snacks', 'TRIOLADE', NULL),
        ('Suesswaren und Snacks', 'Verano Vanilla', NULL),
        ('Haushalt', 'FH-DOSE 450ML', NULL),
        ('Haushalt', 'TIEFKUEHLTASCHE', NULL),
        ('Haushalt', 'Paradies Baby C Power', NULL),
        ('Haushalt', 'Paradies Micro AAA 4 St', NULL),
        ('Baby und Kind', 'Mayben B&K Sonnencreme 100ml', NULL),
        ('Baby und Kind', 'SauBär Badezubehör Pad', NULL),
        ('Koerperpflege', 'essence Nagelkleber fix it!', NULL),
        ('Koerperpflege', 'o.b.ExtraProtect Super 42St', NULL),
        ('Lebensmittel', 'LEBENSMITTEL', NULL),
        ('Fleisch und Wurst', 'BEDIENUNGSTHEKE', 'REWE')
)
UPDATE receipt_item item
SET category_id = category.id,
    category_source = 'RULE'
FROM receipt,
     seed_rules seed
JOIN category ON category.name = seed.category_name
WHERE receipt.id = item.receipt_id
  AND receipt.deleted_at IS NULL
  AND item.category_id IS NULL
  AND item.category_source IS NULL
  AND item.is_manually_edited = false
  AND lower(item.description) = lower(seed.match_value)
  AND (seed.store_name IS NULL OR lower(trim(receipt.store_name)) = lower(seed.store_name));
