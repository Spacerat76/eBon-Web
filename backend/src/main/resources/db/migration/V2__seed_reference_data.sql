INSERT INTO category (name, color_hex, icon, sort_order) VALUES
    ('Lebensmittel', '#2E7D32', 'shopping-basket', 10),
    ('Getraenke', '#0277BD', 'cup-soda', 20),
    ('Drogerie', '#6A1B9A', 'sparkles', 30),
    ('Haushalt', '#455A64', 'home', 40),
    ('Tierbedarf', '#8D6E63', 'paw-print', 50),
    ('Gesundheit', '#C62828', 'heart-pulse', 60),
    ('Freizeit', '#EF6C00', 'ticket', 70),
    ('Sonstiges', '#616161', 'circle-help', 999)
ON CONFLICT (name) DO NOTHING;

INSERT INTO app_settings (key, value, description) VALUES
    ('sync_interval_minutes', '60', 'Intervall fuer Paperless-NGX-Synchronisierung in Minuten'),
    ('ai_model', 'google/gemini-flash-1.5', 'OpenRouter-Modell fuer optionale KI-Funktionen'),
    ('ai_max_tokens', '500', 'Maximale Tokenanzahl fuer KI-Antworten'),
    ('ai_temperature', '0.1', 'Temperatur fuer deterministische KI-Antworten')
ON CONFLICT (key) DO NOTHING;
