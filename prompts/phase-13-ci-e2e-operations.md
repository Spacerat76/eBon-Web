# Phase 13 - CI, E2E, Rollierende Backups, Icons, Versionierung

```text
Setze Phase 13 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- GitHub-Actions-CI fuer Backend, Frontend und Docker-Konfiguration anlegen
- Selenium-basierte Frontend-Smoke-Tests einfuehren
- Automatische rollierende Backups implementieren
- Kategorie-Icons in UI und Validierung vervollstaendigen
- Software-Versionierung zentral und konsistent sichtbar machen

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-09, F-11, F-12, F-16, F-18, 12, 13, 14, 16 und 17.
- Verwende die Skills .codex/skills/ebon-backend, .codex/skills/ebon-frontend und .codex/skills/ebon-qa.
- Keine echten Paperless-NGX- oder OpenRouter.ai-Calls in Tests oder CI.
- Keine echten Tokens, Passwoerter oder privaten Bon-Daten ins Repository schreiben.
- CI muss ohne lokale .env und ohne echte externe Services laufen.
- E2E-Tests muessen reproduzierbar sein und duerfen nicht von echten Bons abhaengen.
- Rollierende Backups muessen dasselbe ZIP-Format und dasselbe Secret-Masking wie manuelle Backups verwenden.
- Rollierende Backups duerfen manuell heruntergeladene Backups nicht loeschen.
- Kategorie-Icons duerfen nur aus einer erlaubten festen Icon-Liste stammen; keine beliebigen HTML/SVG-Fragmente persistieren.
- Versionierung muss zentral gepflegt werden und in Backend, UI, OpenAPI/Backup-Manifest oder Build-Metadaten konsistent sein.

CI-Anforderungen:
- Workflow unter .github/workflows/ anlegen.
- Mindestens ausfuehren:
  - cd backend && mvn verify
  - cd frontend && npm ci
  - cd frontend && npm run build
  - docker compose config
- Fuer Testcontainers sicherstellen, dass Docker im GitHub-Runner verwendbar ist.
- Keine echten Secrets in GitHub Actions voraussetzen.

Selenium/E2E-Anforderungen:
- Selenium-Test-Setup im Frontend oder in einem dedizierten e2e/ Ordner anlegen.
- Mindestens einen Smoke-Test fuer zentrale UI-Flows schreiben:
  - App startet und Dashboard/Navigation rendert.
  - Einstellungen-Seite ist erreichbar.
  - Backup-Tab zeigt Backup-Download, Dry-Run und Restore-Bestaetigung.
  - Receipt- oder Suchseite rendert mit Testdaten oder Mockdaten.
- Dokumentiere, wie E2E lokal und in CI ausgefuehrt wird.

Rollierende-Backup-Anforderungen:
- Konfiguration fuer automatische Backups einfuehren:
  - enabled/default false oder sicherer Default
  - Zielverzeichnis/Volume
  - Intervall oder Cron-Ausdruck
  - maximale Anzahl aufzubewahrender automatischer Backups
- Scheduler darf nicht parallel zu Restore oder manuellem Backup laufen.
- Backup-Dateinamen muessen eindeutig sein und automatische Backups als automatisch erkennbar machen.
- Retention loescht nur alte automatische Backups aus dem konfigurierten Zielverzeichnis.
- Tests fuer Scheduler, Retention, Secret-Masking und Lock-Verhalten schreiben.

Kategorie-Icon-Anforderungen:
- Feste erlaubte Icon-Liste definieren.
- Backend validiert Kategorie-Icon-Werte.
- Frontend bietet eine klare Icon-Auswahl statt Freitext, soweit sinnvoll.
- Icons in Kategorien, Regeln, Receipt-Ansichten oder Reports anzeigen, ohne die Farbkodierung zu ersetzen.

Versionierungs-Anforderungen:
- Zentrale Version aus Build-/Projektmetadaten ableiten.
- Version im Backend abrufbar machen, z. B. per geschuetztem API-Endpunkt oder Health-/Info-DTO.
- Version in UI anzeigen, z. B. im Settings- oder Footer-Bereich.
- Backup-Manifest und OpenAPI-Info sollen dieselbe Version verwenden.
- README dokumentiert, wie Versionen gesetzt oder Releases gebaut werden.

Pruefkommandos:
- cd backend && mvn verify
- cd frontend && npm run build
- E2E-Testkommando gemaess Implementierung
- docker compose config
- git diff --check

Wenn moeglich:
- GitHub-Actions-Workflow lokal oder durch syntaktische Validierung pruefen.
- docker compose up --build smoke-testen.

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- angelegte CI-Workflows
- E2E-Testumfang und Ausfuehrung
- Rollierende-Backup-Konfiguration und Retention-Verhalten
- Kategorie-Icon-Verhalten
- Versionierungsquelle und sichtbare Versionen
- ausgefuehrte Pruefkommandos
- bekannte Restrisiken
- offene Punkte
```
