# Phase 10 - Suche, Reports, Einstellungen

```text
Setze Phase 10 um.

Ziel:
- Suche mit Filtern, Sortierung und Pagination implementieren
- Reports nach Kategorie, Zeitraum, Geschaeft, Top-Artikel und Bonus implementieren
- CSV-Export fuer Reports implementieren
- Einstellungen UI implementieren
- Verbindungstest fuer Paperless und OpenRouter implementieren
- Secret-Masking in der UI korrekt behandeln
- Kategorien- und Regelverwaltung UI vervollstaendigen
- Backend-Test-Hardening vorbereiten:
  - Mockito explizit als Java-Agent konfigurieren, damit keine Self-Attaching-Warnung entsteht
  - JaCoCo integrieren und Coverage-Regeln aus ebon-specification.md F-12 beruecksichtigen
  - Maven Surefire/JaCoCo argLine so konfigurieren, dass beide Java-Agenten kompatibel bleiben

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-04, F-05, F-07, F-08, F-11, F-12, UC-08, UC-10, UC-13, 8 Suche/Reports/Einstellungen, 9.2.4 bis 9.2.8.
- Verwende die Skills .codex/skills/ebon-frontend, .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- UI auf Deutsch.
- ******** darf beim Speichern nie als Secret persistiert werden.
- Inaktive Kategorien standardmaessig ausblenden, aber bei includeInactive anzeigen.

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
