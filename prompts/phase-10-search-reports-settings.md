# Phase 10 - Suche, Reports, Einstellungen

```text
Setze Phase 10 um.

Ziel:
- Suche mit Filtern, Sortierung und Pagination implementieren
- Reports nach Kategorie, Zeitraum, Geschaeft, Top-Artikel und Bonus implementieren
- CSV-Export fuer Reports implementieren
- Einstellungen UI implementieren
- KI-Kategorisierung-Konfidenz in den Einstellungen steuerbar machen (`aiCategorizationMinConfidence`, 0.000 bis 1.000, Default/Initialwert 0.900)
- Verbindungstest fuer Paperless und OpenRouter implementieren
- Secret-Masking in der UI korrekt behandeln
- Kategorien- und Regelverwaltung UI vervollstaendigen
- Suche/Filter muessen "Ohne Kategorie" fuer Items mit category_id = NULL und category_source = NULL unterstuetzen
- Backend-Test-Hardening vorbereiten:
  - Mockito explizit als Java-Agent konfigurieren, damit keine Self-Attaching-Warnung entsteht
  - JaCoCo integrieren und Coverage-Regeln aus ebon-specification.md F-12 beruecksichtigen
  - Maven Surefire/JaCoCo argLine so konfigurieren, dass beide Java-Agenten kompatibel bleiben

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-04, F-05, F-07, F-08, F-11, F-12, UC-08, UC-10, UC-13, 8 Suche/Reports/Einstellungen, 9.2.4 bis 9.2.8.
- Verwende die Skills .codex/skills/ebon-frontend, .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- UI auf Deutsch.
- `aiCategorizationMinConfidence` als Prozent-/Slider- oder Number-Control darstellen, mit klarer Beschriftung, dass niedrigere Werte mehr automatische KI-Zuordnungen erlauben und hoehere Werte mehr Positionen als "Ohne Kategorie" offen lassen.
- ******** darf beim Speichern nie als Secret persistiert werden.
- Inaktive Kategorien standardmaessig ausblenden, aber bei includeInactive anzeigen.
- Regelverwaltung/Bulk-Apply darf manuelle Kategorien nicht stillschweigend ueberschreiben und soll unkategorisierte Items gezielt adressieren koennen.

Pruefkommandos:
- cd frontend && npm run build
- cd backend && mvn verify
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- implementierte Seiten
- Report-/Suchparameter
- Secret-Handling
- ausgefuehrte Pruefkommandos
- wie ich die Flows teste
- offene Punkte
```
