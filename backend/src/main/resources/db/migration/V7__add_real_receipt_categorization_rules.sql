INSERT INTO category (name, color_hex, icon, sort_order, is_active)
VALUES ('Fotos & Bilder', '#3949AB', 'image', 75, true)
ON CONFLICT (name) DO NOTHING;

WITH seed_rules (category_name, match_value, match_type, priority) AS (
    VALUES
        ('Pfand und Rabatte', '3x Bonduelle', 'CONTAINS', 5),
        ('Pfand und Rabatte', 'L CC grat', 'CONTAINS', 5),

        ('Fleisch und Wurst', 'SERVICE GEW', 'EXACT', 15),
        ('Fleisch und Wurst', 'KASSELER LACHS', 'CONTAINS', 15),
        ('Fleisch und Wurst', 'KASSLER LACHS', 'CONTAINS', 15),
        ('Fleisch und Wurst', 'GRILLSAFTSCHINKE', 'CONTAINS', 15),
        ('Fleisch und Wurst', 'DEL. KOCHSCHINK', 'CONTAINS', 20),
        ('Fleisch und Wurst', 'BAUCHSCHEIBEN', 'CONTAINS', 20),
        ('Fleisch und Wurst', 'WACHOLDERSCHINK', 'CONTAINS', 20),
        ('Fleisch und Wurst', 'JB-HUEFTE', 'CONTAINS', 20),
        ('Fleisch und Wurst', 'RD-CEVAPCICI', 'CONTAINS', 20),
        ('Fleisch und Wurst', 'SCHAELRIPPCHEN', 'CONTAINS', 20),
        ('Fleisch und Wurst', 'SCHW.KERNSCHINKE', 'CONTAINS', 20),

        ('Baby und Kind', 'HIPP', 'CONTAINS', 15),
        ('Baby und Kind', 'FR.KAROTT+KARTOF', 'CONTAINS', 15),
        ('Baby und Kind', 'KAROT.KART.RIND', 'CONTAINS', 15),
        ('Baby und Kind', 'NUK', 'CONTAINS', 15),
        ('Baby und Kind', 'TD5 WINDELBADEHOSE', 'CONTAINS', 15),
        ('Baby und Kind', 'GLUECKSKIND GEBURT', 'CONTAINS', 15),
        ('Baby und Kind', 'WELEDA CALENDULA', 'CONTAINS', 15),

        ('Obst und Gemuese', 'DIE FEINEN KART', 'CONTAINS', 30),
        ('Obst und Gemuese', 'MANDARIN ORANGEN', 'CONTAINS', 30),
        ('Obst und Gemuese', 'CHAMPIGN', 'CONTAINS', 30),
        ('Obst und Gemuese', 'KIRSCHEN', 'EXACT', 30),
        ('Obst und Gemuese', 'KOHLRABI', 'CONTAINS', 30),
        ('Obst und Gemuese', 'PILZ CHAMP', 'CONTAINS', 30),
        ('Obst und Gemuese', 'ROSMARIN', 'CONTAINS', 30),
        ('Obst und Gemuese', 'SCHALOTTEN', 'CONTAINS', 30),
        ('Obst und Gemuese', 'STAUDENSELLERIE', 'CONTAINS', 30),

        ('Suesswaren und Snacks', 'KS HARRY HASE', 'CONTAINS', 35),
        ('Suesswaren und Snacks', 'TOFFIFEE', 'CONTAINS', 35),
        ('Suesswaren und Snacks', 'RITTER PEANUT', 'CONTAINS', 35),
        ('Suesswaren und Snacks', 'KIND. MINI', 'CONTAINS', 35),
        ('Suesswaren und Snacks', 'FRIT-STICKS', 'CONTAINS', 35),
        ('Suesswaren und Snacks', 'YES CARAMEL', 'CONTAINS', 35),

        ('Milchprodukte und Eier', 'JOGH', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'WALDM.M.VANILLE', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'VANILLE-SOSSE', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'KAKAO-MOUSSE', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'MOUSSE VANILLE', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'MOZZ.MINI', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'BURRATA', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'DU VLA', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'VLA VANILLE', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'FR.-ZWERGE', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'MUELLER GRIESS', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'PAULA', 'CONTAINS', 45),
        ('Milchprodukte und Eier', 'SAHNEPUD', 'CONTAINS', 45),

        ('Brot und Backwaren', 'BISQ.TORTENBODEN', 'CONTAINS', 50),
        ('Brot und Backwaren', 'FLAEMISCHE TOERT', 'CONTAINS', 50),

        ('Getraenke', 'CC Z 0,33L', 'CONTAINS', 55),
        ('Getraenke', 'CC EW 0,33L', 'CONTAINS', 55),
        ('Getraenke', 'CC SRITE ZERO', 'CONTAINS', 55),
        ('Getraenke', 'KALKSTEIN RIESL', 'CONTAINS', 55),
        ('Getraenke', 'MONIN SIRUP', 'CONTAINS', 55),

        ('Vorrat und Fertiggerichte', 'ERDBEER KONFITUE', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'ANTIPASTITELLER', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'BAKED POTATOES', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'OLIVEN', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'BACKAROMA', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'CURRY PULVER', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'DRESS AMERICAN', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'DRESS JOG', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'HEINZ KETCHUP', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'KOKOSM', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'KOKOSRASPEL', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'LASAGNE', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'OLIV KNOBL', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'PENNE RIGATE', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'SAMT ERDBEER', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'SAUERKIRSCHEN', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'SOFORT GELATINE', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'SPEISFETT', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'TEXASBBQCHICKBUR', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'TORTILLIAS', 'CONTAINS', 60),
        ('Vorrat und Fertiggerichte', 'TORTILLI', 'CONTAINS', 60),

        ('Koerperpflege', 'FA', 'EXACT', 70),
        ('Koerperpflege', 'SH 2IN1', 'CONTAINS', 70),
        ('Koerperpflege', 'LA RIVE', 'CONTAINS', 70),
        ('Koerperpflege', 'LABELLO', 'CONTAINS', 70),
        ('Koerperpflege', 'WATTESTAEB', 'CONTAINS', 70),

        ('Gesundheit', 'LADY GARD', 'CONTAINS', 80),
        ('Gesundheit', 'MIVOLIS INHAL', 'CONTAINS', 80),
        ('Gesundheit', 'MIVOLIS OMEGA', 'CONTAINS', 80),
        ('Gesundheit', 'O.B. ORIGINAL', 'CONTAINS', 80),

        ('Haushalt', 'RPP TASCHE', 'CONTAINS', 90),
        ('Haushalt', 'TORTENGLOCKE', 'CONTAINS', 90),
        ('Haushalt', 'ULTRA WINDFRISCH', 'CONTAINS', 90),
        ('Haushalt', 'DRANO', 'CONTAINS', 90),
        ('Haushalt', 'FARBFANGTUCH', 'CONTAINS', 90),
        ('Haushalt', 'FROSCH WC', 'CONTAINS', 90),
        ('Haushalt', 'FROSCH WSP', 'CONTAINS', 90),
        ('Haushalt', 'PAPPTELLER', 'CONTAINS', 90),
        ('Haushalt', 'PERMANENTTRAGETASCHE', 'CONTAINS', 90),
        ('Haushalt', 'SOFT&SICHER TASCHENT', 'CONTAINS', 90),
        ('Haushalt', 'SOMAT', 'CONTAINS', 90),
        ('Haushalt', 'TORTENSPITZEN', 'CONTAINS', 90),
        ('Haushalt', 'TORTENUNTERLAGEN', 'CONTAINS', 90),
        ('Haushalt', 'WC ENTE', 'CONTAINS', 90),
        ('Haushalt', 'WS SOMMERWIND', 'CONTAINS', 90),

        ('Freizeit', 'ABZIEHBILDER', 'CONTAINS', 100),
        ('Freizeit', 'PLUESCH HASE', 'CONTAINS', 100),
        ('Freizeit', 'PLUESCHKISSEN', 'CONTAINS', 100),
        ('Freizeit', 'FABRIZIO BIKINIBAG', 'CONTAINS', 100),
        ('Freizeit', 'FASHY BADESCHUHE', 'CONTAINS', 100),
        ('Freizeit', 'FENSTERBILD', 'CONTAINS', 100),
        ('Freizeit', 'FILZKORB', 'CONTAINS', 100),
        ('Freizeit', 'HOLZAUFSTELLER', 'CONTAINS', 100),

        ('Fotos & Bilder', 'FOTOAUFTRAG', 'CONTAINS', 105),
        ('Fotos & Bilder', 'FOTOEXPRESS', 'CONTAINS', 105)
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
