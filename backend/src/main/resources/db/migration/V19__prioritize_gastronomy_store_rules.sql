UPDATE categorization_rule
SET priority = 10
WHERE category_id = (SELECT id FROM category WHERE name = 'Gastronomie')
    AND match_field = 'STORE_NAME'
    AND priority > 10;
