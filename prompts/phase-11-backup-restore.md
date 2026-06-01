# Phase 11 - Backup, Restore, Dry-Run

```text
Setze Phase 11 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Backup-Download als ZIP implementieren
- Backup-Manifest implementieren
- Secrets in app_settings.json nicht im Klartext exportieren
- Restore-Dry-Run implementieren
- Full Restore transaktional implementieren
- Schreiboperationen waehrend Backup/Restore sperren
- Restore-Runbook unter docs/restore-runbook.md erstellen
- Backup UI vervollstaendigen

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-09, F-16, UC-11, UC-12, 8 Backup & Restore, 12, 13, 17.3.
- Verwende die Skills .codex/skills/ebon-backend, .codex/skills/ebon-frontend und .codex/skills/ebon-qa.
- Restore muss bei Fehler vollstaendig rollbacken.
- Dry-Run darf keine Daten veraendern.
- Keine Secrets im Klartext in Backups.
- Restore UI muss eine deutliche Bestaetigung verlangen.

Pruefkommandos:
- cd backend && mvn verify
- cd frontend && npm run build
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- Backup-Format
- Restore-Schutzmechanismen
- Tests
- ausgefuehrte Pruefkommandos
- wie ich Backup/Dry-Run/Restore teste
- offene Punkte
```

