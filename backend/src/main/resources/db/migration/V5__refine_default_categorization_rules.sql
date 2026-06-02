INSERT INTO category (name, color_hex, icon, sort_order) VALUES
    ('Fisch und Meeresfruechte', '#006064', 'fish', 13)
ON CONFLICT (name) DO NOTHING;

INSERT INTO app_settings (key, value, description) VALUES
    (
        'ai_categorization_min_confidence',
        '0.900',
        'Minimale KI-Konfidenz fuer automatische Kategorisierung; Wertebereich 0.000 bis 1.000'
    )
ON CONFLICT (key) DO NOTHING;

UPDATE categorization_rule
SET is_active = FALSE
WHERE match_field = 'STORE_NAME'
    AND match_type = 'CONTAINS'
    AND priority = 900
    AND match_value IN (
        'REWE',
        'ALDI',
        'LIDL',
        'EDEKA',
        'KAUFLAND',
        'NETTO',
        'PENNY',
        'DM',
        'ROSSMANN',
        'MUELLER'
    );

WITH seed_rules(category_name, match_field, match_type, match_value, priority) AS (
    VALUES
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'PFIRSICH', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'CHIQUITA', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'MANGO', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'BASIL', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'PETERSIL', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'INGWER', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'KNOBLAUCH', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'KUERB', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'HIMBEER', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'CLEMENT', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'ROSENKOHL', 30),
        ('Obst und Gemuese', 'DESCRIPTION', 'CONTAINS', 'WEISSKRAUT', 30),

        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'BRUSTFILET', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'BRUST', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'HAEHN', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'PUTE', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'RIND', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'SCHWEIN', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'RAEUCHER', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'VESPER', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'ROASTBEEF', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'SPIESS', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'GRILLTALER', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'RUECKEN', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'PASTRAMI', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'GYROS', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'WIENER', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'SERRANO', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'NEUBURGER', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'KRUST', 20),
        ('Fleisch und Wurst', 'DESCRIPTION', 'CONTAINS', 'LEICHTSCHIN', 20),

        ('Fisch und Meeresfruechte', 'DESCRIPTION', 'CONTAINS', 'FISCH', 18),
        ('Fisch und Meeresfruechte', 'DESCRIPTION', 'CONTAINS', 'LACHS', 18),
        ('Fisch und Meeresfruechte', 'DESCRIPTION', 'CONTAINS', 'SHRIMP', 18),
        ('Fisch und Meeresfruechte', 'DESCRIPTION', 'CONTAINS', 'GARNELE', 18),
        ('Fisch und Meeresfruechte', 'DESCRIPTION', 'CONTAINS', 'GARNELEN', 18),

        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'RACLETTE', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'MOZZAR', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'BUFALA', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'FRUCHTZWERG', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'DESSERT', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'GRANA', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'RUSTIQUE', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'GOUDA', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'MILKANA', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'ZAZIKI', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'CHESTER', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'GOETTERSP', 45),
        ('Milchprodukte und Eier', 'DESCRIPTION', 'CONTAINS', 'GRUETZE', 45),

        ('Brot und Backwaren', 'DESCRIPTION', 'CONTAINS', 'BROET', 50),
        ('Brot und Backwaren', 'DESCRIPTION', 'CONTAINS', 'BAECK', 50),
        ('Brot und Backwaren', 'DESCRIPTION', 'CONTAINS', 'CROISSANT', 50),
        ('Brot und Backwaren', 'DESCRIPTION', 'CONTAINS', 'SANDWICH', 50),
        ('Brot und Backwaren', 'DESCRIPTION', 'CONTAINS', 'VKB', 50),
        ('Brot und Backwaren', 'DESCRIPTION', 'CONTAINS', 'KUCHEN', 50),
        ('Brot und Backwaren', 'DESCRIPTION', 'CONTAINS', 'HAMBURGER', 50),

        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'TEE', 55),
        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'LIMON', 55),
        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'VITAMALZ', 55),
        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'RED BULL', 55),
        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'STILLEQUELLE', 55),
        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'MOCCA', 55),
        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'SMOOTHIE', 55),
        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'KAFFEE', 55),
        ('Getraenke', 'DESCRIPTION', 'CONTAINS', 'ROTBKLASSIK', 55),

        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'NUDEL', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'ZUCKER', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'SALZ', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'HAFER', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'MUESLI', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'FROSTIES', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'ZITRONENSCH', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'BRUEHE', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'BACKIN', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'OREGANO', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'FLAKES', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'KNUSPERFLAKES', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'KONSERVE', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'FERTIG', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'SUPPE', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', '5MIN', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'SPINRIC', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'LEGERE', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'HOLLAND', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'PFIFFER', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'WACHSBRECH', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'MOEHRCHEN', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'MEERETT', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'WILLCHRIST', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'LINSEN', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'EINTOPF', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'CREMESUP', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'TEXMEX', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'HEINZSAUCE', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'PESTO', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'NAPOLET', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'ARRABBIATA', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'TOMMARK', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'PIZZAKIT', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'SAUERKRAUT', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'HONEYPEPPER', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'PFEFFERON', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'WUERZSAUCE', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'BURGERDRESSING', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'OEL', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'STREICH', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'MARGARINE', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'REMOUL', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'MAYO', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'PFLANZENCREME', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'HONIG', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'NUTELLA', 65),
        ('Vorrat und Fertiggerichte', 'DESCRIPTION', 'CONTAINS', 'RAHMSPINAT', 65),

        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'PICKUP', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'KEKS', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'BAHLSEN', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'SNACK', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'KITKAT', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'RIEGEL', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'UEEI', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'UE-EI', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'WEIH', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'TENDER', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'LINDOR', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'CROSSIES', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'WAFFEL', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'MIGNON', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'OSTER', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'MINI BTL', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'MINI-BTL', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'CELEBR', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'GOLDBAER', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'GLASUR', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'GEBAECK', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'SPRITZGEB', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'NUSS', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'TAFELSCH', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'ADVENT', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'FERRERO', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'NUES', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'BUENO', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'BREZEL', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'YOGURETTE', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'MILKA', 35),
        ('Suesswaren und Snacks', 'DESCRIPTION', 'CONTAINS', 'MARZIPAN', 35),

        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'SPONTEX', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'SPUEL', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'WASCH', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'TASCHENTUCH', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'KUECHENROLLE', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'TOILET', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'Q-TIPS', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'SIDOLIN', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'UNSTOPPABLES', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'BECHER', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'SERVIET', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'TEXTILERFR', 90),
        ('Haushalt', 'DESCRIPTION', 'CONTAINS', 'KRAFTGEL', 90),

        ('Baby und Kind', 'DESCRIPTION', 'CONTAINS', 'WINDELN', 75),
        ('Baby und Kind', 'DESCRIPTION', 'CONTAINS', 'BABYNAHRUNG', 75),
        ('Baby und Kind', 'DESCRIPTION', 'CONTAINS', 'QUETSCHIE', 75),

        ('Tierbedarf', 'DESCRIPTION', 'CONTAINS', 'TIERNAHRUNG', 150),

        ('Freizeit', 'DESCRIPTION', 'CONTAINS', 'BATTERIE', 100),
        ('Freizeit', 'DESCRIPTION', 'CONTAINS', 'KERZE', 100),
        ('Freizeit', 'DESCRIPTION', 'CONTAINS', 'FEUERZEUG', 100)
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
);
