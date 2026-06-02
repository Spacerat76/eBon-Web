DO $$
DECLARE
    target_category_id BIGINT;
    old_category_id BIGINT;
    salad_category_id BIGINT;
BEGIN
    SELECT id INTO target_category_id
    FROM category
    WHERE name = 'Salat, Obst & Gemüse';

    SELECT id INTO old_category_id
    FROM category
    WHERE name = 'Obst und Gemuese';

    IF target_category_id IS NULL AND old_category_id IS NOT NULL THEN
        UPDATE category
        SET name = 'Salat, Obst & Gemüse',
            color_hex = '#43A047',
            icon = 'apple',
            sort_order = 11,
            is_active = TRUE
        WHERE id = old_category_id;

        target_category_id := old_category_id;
    ELSIF target_category_id IS NOT NULL
        AND old_category_id IS NOT NULL
        AND target_category_id <> old_category_id THEN
        UPDATE receipt_item
        SET category_id = target_category_id
        WHERE category_id = old_category_id;

        UPDATE categorization_rule
        SET category_id = target_category_id
        WHERE category_id = old_category_id;

        UPDATE ai_categorization_log
        SET assigned_category_id = target_category_id
        WHERE assigned_category_id = old_category_id;

        UPDATE ai_categorization_log
        SET suggested_category_id = target_category_id
        WHERE suggested_category_id = old_category_id;

        UPDATE category
        SET is_active = FALSE
        WHERE id = old_category_id;
    END IF;

    IF target_category_id IS NULL THEN
        RAISE EXCEPTION 'Target category Salat, Obst & Gemüse could not be resolved';
    END IF;

    SELECT id INTO salad_category_id
    FROM category
    WHERE name = 'Salat';

    IF salad_category_id IS NOT NULL AND salad_category_id <> target_category_id THEN
        UPDATE receipt_item
        SET category_id = target_category_id
        WHERE category_id = salad_category_id;

        UPDATE categorization_rule
        SET category_id = target_category_id
        WHERE category_id = salad_category_id;

        UPDATE ai_categorization_log
        SET assigned_category_id = target_category_id
        WHERE assigned_category_id = salad_category_id;

        UPDATE ai_categorization_log
        SET suggested_category_id = target_category_id
        WHERE suggested_category_id = salad_category_id;

        DELETE FROM category
        WHERE id = salad_category_id
          AND NOT EXISTS (
              SELECT 1
              FROM receipt_item
              WHERE category_id = salad_category_id
          )
          AND NOT EXISTS (
              SELECT 1
              FROM categorization_rule
              WHERE category_id = salad_category_id
          )
          AND NOT EXISTS (
              SELECT 1
              FROM ai_categorization_log
              WHERE assigned_category_id = salad_category_id
                 OR suggested_category_id = salad_category_id
          );

        UPDATE category
        SET is_active = FALSE
        WHERE id = salad_category_id;
    END IF;
END $$;
