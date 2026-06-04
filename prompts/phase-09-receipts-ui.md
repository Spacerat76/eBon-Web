# Phase 09 - Bons UI

```text
Setze Phase 9 um.

Ziel:
- Bon-Liste implementieren
- Bon-Detailansicht implementieren
- Rohtextansicht ausklappbar anzeigen
- Paperless-Dokument-ID in Liste/Detail sichtbar machen und als Link zur Paperless-Weboberflaeche anbieten, wenn `paperlessDocumentUrl` vorhanden ist
- Editiermodus fuer Bon-Metadaten und Positionen implementieren
- Kategorie-Badges und Parse-Status-Badges anzeigen
- Unkategorisierte Positionen mit category_id = NULL und category_source = NULL als "Ohne Kategorie" anzeigen und bearbeitbar machen
- Nicht uebernommene KI-Vorschlaege bei "Ohne Kategorie"-Positionen anzeigen, z.B. "KI-Vorschlag: Drogerie (82 %)"; Ablehnungsgrund nutzerfreundlich erklaeren, etwa niedrige Konfidenz oder unbekannte Kategorie
- Re-Parse-Button mit Konflikthinweis fuer manuelle Aenderungen vorbereiten
- Loeschen/Soft-Delete Verhalten im UI korrekt abbilden
- Import-Datum in der Bon-Liste mit Datum und Uhrzeit lesbar anzeigen; bei schmaler Spalte Uhrzeit in einer zweiten Zeile unter dem Datum
- Aktiven Navigationspunkt/Breadcrumb korrekt setzen: Bon-Liste und Bon-Detail muessen "Bons" zeigen, nicht "Dashboard"
- Speichern/Abbrechen im Editiermodus als sticky Action-Bar oder gleichwertig sichtbar halten, damit lange Bons ohne Scrollen nach oben gespeichert werden koennen

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-06, UC-03, UC-04, UC-09, 8 Receipts/Receipt Items, 9.2.2 und 9.2.3.
- Verwende die Skills .codex/skills/ebon-frontend, .codex/skills/ebon-backend falls API-Anpassungen noetig sind, und .codex/skills/ebon-qa.
- UI auf Deutsch.
- Keine Secrets anzeigen.
- Manuell editierte Positionen duerfen nicht stillschweigend ueberschrieben werden.
- categorySource-Badge nur anzeigen, wenn eine Kategorie gesetzt ist; fuer "Ohne Kategorie" kein RULE/AI/MANUAL-Badge vortaeuschen.
- Wenn `aiSuggestion` vorhanden ist, soll die UI eine schnelle Uebernahme des Vorschlags anbieten und alternativ die normale Kategorieauswahl offen halten. Die Uebernahme ist eine manuelle Kategorieentscheidung (`MANUAL`), keine nachtraegliche AI-Zuweisung.
- Paperless-Links duerfen keine Secrets enthalten. Wenn das Backend noch kein `paperlessDocumentUrl` liefert, ergaenze DTO/API gemaess ebon-specification.md und nutze eine konfigurierbare Public-URL/Vorlage statt die interne API-URL blind im Frontend zusammenzubauen.
- Verwende fuer die Bon-Liste die fachlich neueste Sortierung (`receiptDate desc`, danach `receiptTime desc`, danach `importedAt desc`), sofern der Nutzer keine andere Sortierung waehlt.

Pruefkommandos:
- cd frontend && npm run build
- falls Backend geaendert wurde: cd backend && mvn verify
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- implementierte UI-Flows
- benoetigte API-Endpunkte
- ausgefuehrte Pruefkommandos
- wie ich Bons lokal pruefe
- offene Punkte
```
