# Phase 03 - Datenmodell, Flyway, Repositories

```text
Setze Phase 3 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- PostgreSQL-Anbindung konfigurieren
- Flyway-Migrationen fuer das Datenmodell aus ebon-specification.md Abschnitt 4 anlegen
- JPA-Entities und Repositories anlegen
- Basisdaten fuer Standard-Kategorien vorbereiten, falls sinnvoll
- Migrationstests oder Repository-Smoke-Tests anlegen

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte 4, 10, 12, 14, 16 und 17.
- Verwende die Skills .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- Beachte Soft-Delete fuer receipts: deleted_at, delete_reason.
- Beachte Kategorie-Deaktivierung statt unsicherem Hard-Delete.
- Keine REST-CRUD-Funktionalitaet ueber das Minimum hinaus bauen.
- Keine Parser-, Sync- oder Frontend-Features implementieren.

Pruefkommandos:
- cd backend && mvn verify
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- angelegte Tabellen/Migrationen
- geaenderte Dateien
- ausgefuehrte Pruefkommandos
- wie ich die Migration pruefe
- offene Punkte
```

