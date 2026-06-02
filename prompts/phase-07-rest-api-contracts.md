# Phase 07 - REST API, DTOs, Validierung

```text
Setze Phase 7 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- REST-Endpunkte gemaess ebon-specification.md Abschnitt 8 vervollstaendigen
- DTOs aus Abschnitt 8.4 verwenden
- Validation fuer Request-Bodies und Query-Parameter implementieren
- Einheitliches Pagination-Format implementieren
- Einheitliches Fehlerformat implementieren
- OpenAPI-Dokumentation aktualisieren
- Controller- oder Contract-Tests schreiben
- ReceiptItemDTO/Requests validieren: categorySource = AI/RULE/MANUAL nur mit gesetzter categoryId; ohne Kategorie muessen categoryId und categorySource null bleiben

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte 8, 13, 14, 16, 17.
- Verwende die Skills .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- Keine JPA-Entities direkt serialisieren.
- Alle Endpunkte ausser GET /api/health schuetzen.
- includeDeleted nur dort unterstuetzen, wo spezifiziert.
- Secrets in SettingsDTO maskieren; ******** nie persistieren.
- "Ohne Kategorie" als expliziten Zustand in DTOs/OpenAPI abbilden, damit die UI diese Positionen spaeter bearbeiten kann.

Pruefkommandos:
- cd backend && mvn verify
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- implementierte Endpunkte
- DTOs
- Validierungsregeln
- ausgefuehrte Pruefkommandos
- wie ich Swagger/OpenAPI pruefe
- offene Punkte
```
