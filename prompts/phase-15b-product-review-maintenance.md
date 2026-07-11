# Phase 15b - Produktreview und Pflege

```text
Setze Phase 15b aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Produktreview-Queue fuer unsichere Produktzuordnungen implementieren
- Manuelle Korrektur einzelner Produktzuordnungen ermoeglichen
- Produktfamilien und Varianten pflegen
- Produktfamilien/Varianten zusammenfuehren und trennen
- Regelvorschlaege aus manuellen Zuordnungen erzeugen
- Rueckwirkendes Anwenden von Regeln und Korrekturen immer mit Vorschau und Bestaetigung umsetzen
- Frontend-Flows fuer Review und Pflege bauen

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-19, 4, 6, 8, 9, 12, 13, 14, 15, 16 und 17.
- Verwende die Skills .codex/skills/ebon-backend, .codex/skills/ebon-frontend und .codex/skills/ebon-qa.
- Phase 15a muss als Grundlage vorhanden sein. Falls Teile fehlen, ergaenze nur die minimal noetigen Grundlagen fuer 15b und dokumentiere das.
- Keine echten Paperless-NGX- oder OpenRouter.ai-Calls in Tests oder CI.
- Keine echten Tokens, privaten Bontexte, vollstaendigen KI-Prompts oder KI-Rohantworten ins Repository schreiben.
- Keine Produktkorrektur darf historische Daten ohne Vorschau und explizite Bestaetigung veraendern.

Fachliche Anforderungen:
- Neue Arbeitsflaeche "Produktzuordnung pruefen" im Produktbereich.
- Review-Queue zeigt Positionen mit:
  - NEEDS_REVIEW
  - niedriger oder widerspruechlicher Konfidenz
  - unklarer Groesse
  - Konflikt zwischen Regel, Historie und KI
  - auffaelligem Preis, soweit in 15b bereits erkennbar
- Standard-Sortierung nach Nutzen:
  - haeufige Positionen zuerst
  - teure Positionen zuerst
  - neuere offene Faelle sichtbar halten
- Filter:
  - Unsicherheit/Konfidenz
  - Store
  - Produktfamilie
  - Kategorie
  - Zeitraum
  - Zuordnungsquelle
  - Status

Review-Queue-Anzeige:
- Bon-Datum
- Store und optional Filiale
- Positionsbeschreibung
- Menge, Einheit, Einzelpreis, Gesamtpreis
- vorgeschlagene Produktfamilie
- vorgeschlagene Produktvariante
- Konfidenz
- Grund des Vorschlags
- moegliche rueckwirkende Auswirkungen
- Link zum Bon

Review-Aktionen:
- Vorschlag akzeptieren.
- Produktfamilie/Variante korrigieren.
- Neue Produktfamilie direkt im Korrekturdialog anlegen; Nutzer duerfen fuer typische Prueflistenfaelle nicht erst in die Stammdatenpflege wechseln muessen.
- Neue Produktvariante anlegen.
- Optional gleiche offene Positionen mit gleicher normalisierter Beschreibung im gleichen Store mit derselben Zuordnung uebernehmen; manuell bestaetigte Zuordnungen bleiben geschuetzt.
- Vorschlag ablehnen.
- Position als NO_PRODUCT markieren.
- Produktzuordnung entfernen.
- Regel aus manueller Zuordnung vorschlagen.
- Regelvorschlag mit Vorschau bestaetigen und optional rueckwirkend anwenden.

Produktpflege:
- Produktfamilien anlegen, bearbeiten, deaktivieren/aktivieren.
- Produktvarianten anlegen, bearbeiten, deaktivieren/aktivieren.
- Produktfamilie zeigt optional default_category.
- Variante zeigt Einheit, Menge, Packungsstruktur und optional GTIN.
- Produktregeln anzeigen, anlegen, bearbeiten, deaktivieren und Vorschau berechnen.
- Produktregeln bleiben getrennt von Kategorisierungsregeln.

Korrektur-Workflows:
- Produktfamilien zusammenfuehren.
- Produktvarianten zusammenfuehren.
- Falsch zusammengelegte Familien oder Varianten trennen.
- Einzelne receipt_item-Zuordnungen aendern.
- Einzelne receipt_item-Zuordnungen entfernen.
- Position als NO_PRODUCT markieren.
- Jede rueckwirkende Aktion braucht Vorschau und Bestaetigung.
- Vorschau zeigt mindestens:
  - Anzahl betroffener Positionen
  - betroffene Stores
  - Zeitraum
  - bisherige Familie/Variante
  - neue Familie/Variante
  - moegliche Report-Auswirkung, soweit in 15b ableitbar
- Aktionen muessen transaktional sein.
- Fehler muessen als strukturierte API-Fehler zurueckkommen, keine Stacktraces in UI.

Reparse-Konflikte:
- Beim Reparse duerfen bestaetigte Produktzuordnungen nicht still verloren gehen.
- Bestaetigte Zuordnungen sollen bestmoeglich uebertragen werden, wenn Beschreibung, Preis und Menge plausibel passen.
- Konflikte muessen in Review oder Bon-Detail sichtbar werden.

Frontend-Anforderungen:
- Navigation:
  - Hauptbereich "Produkte" fuer Review und spaeter Preisvergleich.
  - Verwaltungs-Tab oder Einstellungen fuer Familien, Varianten und Produktregeln.
- Bon-Detail:
  - Produktfamilie und Variante pro Position anzeigen.
  - assignmentSource und assignmentStatus anzeigen.
  - Konfidenz/Reviewstatus kompakt zeigen.
  - Schnelle Korrektur der Produktzuordnung ermoeglichen.
- Review-Queue als dichte, scannbare Tabelle bauen.
- Lange Listen muessen filterbar und paginiert sein.
- Bulk-Aktionen nur mit Vorschau und Bestaetigung.
- UI-Sprache Deutsch.
- Keine sichtbaren Roh-Secrets, Stacktraces oder vollstaendigen KI-Prompts/Rohantworten.

API/DTO-Anforderungen:
- Endpunkte fuer Review-Queue mit Pagination, Sortierung und Filtern.
- Endpunkte fuer accept, correct, reject, NO_PRODUCT und clear assignment.
- Endpunkte fuer Produktfamilien/Varianten CRUD.
- Endpunkte fuer Produktregeln CRUD und Vorschau.
- Endpunkte fuer Merge/Split/Korrekturen mit Preview und Apply.
- DTOs und OpenAPI aktuell halten.
- Frontend-Typen an Backend-DTOs ausrichten.
- Alle Endpunkte ausser GET /api/health bleiben Bearer-Token-geschuetzt.

Tests:
- API-Contract-Tests fuer Review-Queue:
  - Filter
  - Sortierung
  - Pagination
  - accept
  - correct
  - reject
  - NO_PRODUCT
  - clear assignment
- Service-Tests fuer:
  - Regelvorschlag aus manueller Zuordnung
  - Vorschau vor rueckwirkendem Anwenden
  - Apply nur nach explizitem Apply-Aufruf
  - Merge mit Vorschau
  - Split mit Vorschau
  - transaktionalen Rollback bei Fehler
  - Reparse-Konflikt-/Uebertragungslogik soweit implementiert
- Frontend-Build.
- Fokussierter UI-Test oder dokumentierter manueller Test fuer:
  - Review-Queue laden
  - Vorschlag akzeptieren
  - Zuordnung korrigieren
  - NO_PRODUCT markieren
  - Regelvorschau anzeigen
  - Bulk-Aktion bestaetigen/abbrechen

Pruefkommandos:
- cd backend && mvn verify
- cd frontend && npm run build
- docker compose config
- git diff --check

Wenn moeglich:
- Review-Flow mit Mockdaten im Browser manuell pruefen.
- Einen Merge- oder Regel-Apply-Flow abbrechen und bestaetigen, dass nichts persistiert wurde.

Stoppe nach Phase 15b. Implementiere keine vollstaendigen Produktpreisvergleichs-Reports und keine CSV-Erweiterungen aus Phase 15c, ausser minimale Platzhalter/Links fuer Navigation.

Am Ende bitte zusammenfassen:
- Review-Queue-Verhalten
- Produktpflege-Flows
- Merge/Split/Korrektur-Verhalten
- Regelvorschlag- und Vorschau-Verhalten
- neue/angepasste Endpunkte und DTOs
- UI-Flows
- getestete Faelle
- ausgefuehrte Pruefkommandos
- bekannte Restrisiken
- offene Punkte fuer Phase 15c
```
