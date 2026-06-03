INSERT INTO app_settings (key, value, description) VALUES
    ('currency', 'EUR', 'Anzuzeigende Standardwaehrung')
ON CONFLICT (key) DO NOTHING;
