# Phase 12 - Echte Integration und Hardening

```text
Setze Phase 12 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Gesamtsystem per docker compose lauffaehig machen
- Echte Paperless-Konfiguration ueber .env unterstuetzen
- Optional echte OpenRouter-Konfiguration ueber .env unterstuetzen
- Logging, Fehlerbehandlung und Secret-Masking final pruefen
- README finalisieren
- Smoke-Test-Anleitung dokumentieren
- Keine Testdaten oder Secrets ins Repository schreiben

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte 10, 11, 13, 14, 16 und 17.
- Verwende die Skills .codex/skills/ebon-devcontainer, .codex/skills/ebon-backend, .codex/skills/ebon-frontend und .codex/skills/ebon-qa.
- Keine echten Tokens in Dateien schreiben.
- Keine echten Bons als Fixtures einchecken, ausser anonymisiert.
- Swagger/OpenAPI muss geschuetzt oder deaktivierbar sein.
- Pruefe, dass Standardlisten/Reports geloeschte Bons ausblenden.

Pruefkommandos:
- cd backend && mvn verify
- cd frontend && npm run build
- docker compose config
- docker compose up --build
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- wie das Gesamtsystem gestartet wird
- welche .env Werte noetig sind
- welche Smoke-Tests erfolgreich waren
- bekannte Restrisiken
- offene Punkte
```

