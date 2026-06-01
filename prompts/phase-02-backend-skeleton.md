# Phase 02 - Backend Skeleton, Security, Health, OpenAPI

```text
Setze Phase 2 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Spring Boot Backend unter backend/ anlegen
- Maven-Projekt konfigurieren
- GET /api/health oeffentlich bereitstellen
- Bearer-Token-Security fuer alle anderen /api-Endpunkte vorbereiten
- OpenAPI/Swagger integrieren und schuetzen oder konfigurierbar deaktivierbar machen
- Grundlegende Fehlerresponse-Struktur vorbereiten

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte 3, 8, 13, 14, 16 und 17.
- Verwende die Skills .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- Keine Datenbank-Entities implementieren, ausser minimal fuer Start noetig.
- Keine Paperless- oder OpenRouter-Integration implementieren.
- Keine echten externen API-Calls.

Pruefkommandos:
- cd backend && mvn verify
- git diff --check

Wenn eine lokale Ausfuehrung moeglich ist:
- cd backend && mvn spring-boot:run
- GET /api/health muss ohne Auth { "status": "UP" } liefern
- ein geschuetzter Beispiel-Endpunkt muss ohne Token 401 liefern, falls vorhanden

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- geaenderte Dateien
- wichtige Endpunkte
- ausgefuehrte Pruefkommandos
- wie ich das Backend teste
- offene Punkte
```

