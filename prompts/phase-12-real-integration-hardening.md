# Phase 12 - Echte Integration und Hardening

```text
Setze Phase 12 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Gesamtsystem per docker compose lauffaehig machen
- Echte Paperless-Konfiguration ueber .env unterstuetzen
- Optional echte OpenRouter-Konfiguration ueber .env unterstuetzen
- Frontend-Dev-Setup final pruefen: Devcontainer-Port 5173, Vite-Proxy fuer `/api`, keine unnoetige Backend-CORS-Abhaengigkeit
- Frontend-UX final pruefen:
  - aktiver Navigationspunkt/Breadcrumb passt auf allen Seiten zur Route
  - Paperless-Dokumentlinks funktionieren mit einer im Browser erreichbaren Public-URL oder dokumentierter URL-Vorlage
  - eBon-Web nutzt ein stimmiges Logo/eine stimmige Wortmarke statt eines provisorischen Platzhalters
  - Dashboard-Begriffe sind eindeutig: "Letzte Bons" als Schnellnavigation, "Bonus" als neu gesammelte Punkte/Guthaben im gewaehlten Zeitraum
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
- Frontend darf keine Secrets hartcodieren; API-Tokens nur ueber lokale UI/Env-Konfiguration oder nicht versionierte Entwicklungseinstellungen verwenden.
- Pruefe, dass Standardlisten/Reports geloeschte Bons ausblenden.
- Pruefe, dass Datenwartungsfunktionen wie Re-Parse aller Bons und Reset importierter Bon-Daten nur bewusst ausloesbar sind, transaktional arbeiten und in README/Runbook als destruktive lokale Admin-Funktion dokumentiert sind.

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
