# Phase 09 - Bons UI

```text
Setze Phase 9 um.

Ziel:
- Bon-Liste implementieren
- Bon-Detailansicht implementieren
- Rohtextansicht ausklappbar anzeigen
- Editiermodus fuer Bon-Metadaten und Positionen implementieren
- Kategorie-Badges und Parse-Status-Badges anzeigen
- Unkategorisierte Positionen mit category_id = NULL und category_source = NULL als "Ohne Kategorie" anzeigen und bearbeitbar machen
- Re-Parse-Button mit Konflikthinweis fuer manuelle Aenderungen vorbereiten
- Loeschen/Soft-Delete Verhalten im UI korrekt abbilden

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-06, UC-03, UC-04, UC-09, 8 Receipts/Receipt Items, 9.2.2 und 9.2.3.
- Verwende die Skills .codex/skills/ebon-frontend, .codex/skills/ebon-backend falls API-Anpassungen noetig sind, und .codex/skills/ebon-qa.
- UI auf Deutsch.
- Keine Secrets anzeigen.
- Manuell editierte Positionen duerfen nicht stillschweigend ueberschrieben werden.
- categorySource-Badge nur anzeigen, wenn eine Kategorie gesetzt ist; fuer "Ohne Kategorie" kein RULE/AI/MANUAL-Badge vortaeuschen.

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
