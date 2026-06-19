UPDATE app_settings
SET value = COALESCE(
        NULLIF((SELECT value FROM app_settings WHERE key = 'openrouter_model'), ''),
        'openai/gpt-oss-20b'
    ),
    description = 'OpenRouter Modell fuer KI-Parsing'
WHERE key = 'ai_parsing_model'
  AND (value IS NULL OR value = '' OR value = 'google/gemini-flash-1.5');

INSERT INTO app_settings (key, value, description)
SELECT
    'ai_parsing_model',
    COALESCE(NULLIF((SELECT value FROM app_settings WHERE key = 'openrouter_model'), ''), 'openai/gpt-oss-20b'),
    'OpenRouter Modell fuer KI-Parsing'
WHERE NOT EXISTS (
    SELECT 1 FROM app_settings WHERE key = 'ai_parsing_model'
);
