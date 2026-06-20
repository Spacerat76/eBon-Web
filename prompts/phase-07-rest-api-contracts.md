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
- Manuelles Zuruecksetzen auf "Ohne Kategorie" implementieren: Wenn ein Receipt-Item-Update `categoryId = null` fuer die Kategorieaenderung sendet, muss der Controller `CategorizationService.manuallyClearItemCategory(...)` verwenden. Nicht einfach Felder direkt auf null setzen, damit `is_manually_edited = true` gesetzt wird und spaetere Regeln/Bulk-Apply diese Nutzerentscheidung nicht still ueberschreiben.
- SettingsDTO um `aiCategorizationMinConfidence` ergaenzen. Wert aus `app_settings.ai_categorization_min_confidence` lesen/speichern, Wertebereich 0.000 bis 1.000 validieren, Default 0.900 dokumentieren.
- ReceiptItemDTO um optionales `aiSuggestion` ergaenzen, damit nicht uebernommene KI-Vorschlaege fuer "Ohne Kategorie"-Positionen in der UI sichtbar sind.
- Fuer Einzel-Reparse einen geschuetzten Status-Endpunkt `GET /api/receipts/{id}/paperless-raw-text-status` bereitstellen. Die DTO-Antwort darf nur `UNCHANGED`, `CHANGED` oder `UNAVAILABLE` enthalten, niemals Rohtext, Hashes, Secrets oder Paperless-Fehlerdetails. `POST /api/receipts/{id}/reparse` um `rawTextSource=STORED|PAPERLESS` mit Default `STORED` ergaenzen; Bulk-Reparse bleibt immer `STORED`.

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte 8, 13, 14, 16, 17.
- Verwende die Skills .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- Keine JPA-Entities direkt serialisieren.
- Alle Endpunkte ausser GET /api/health schuetzen.
- includeDeleted nur dort unterstuetzen, wo spezifiziert.
- Secrets in SettingsDTO maskieren; ******** nie persistieren.
- "Ohne Kategorie" als expliziten Zustand in DTOs/OpenAPI abbilden, damit die UI diese Positionen spaeter bearbeiten kann.
- Fuer "Ohne Kategorie" in Request/Response dokumentieren: `categoryId = null`, `categorySource = null`; kein RULE/AI/MANUAL-Badge vortaeuschen.
- Fuer `aiCategorizationMinConfidence` OpenAPI-Schema mit `minimum: 0`, `maximum: 1`, sinnvollem Beispiel `0.900` und Validierungsfehlern dokumentieren.
- `aiSuggestion` aus dem letzten strukturierten KI-Log pro Position ableiten: `categoryId`, `categoryName`, `confidence`, `rejectionReason`. Nur nicht uebernommene Vorschlaege anzeigen; bei bereits gesetzter Kategorie `aiSuggestion = null`.
- Contract-Tests fuer LOW_CONFIDENCE/UNKNOWN_CATEGORY-Suggestions schreiben, damit die UI keine rohe KI-JSON-Antwort parsen muss.
- Contract-Tests fuer Rohtextstatus, Authentifizierung, Zeilenenden-Normalisierung, geaenderten Text und `UNAVAILABLE` ohne sensible Details schreiben.

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
