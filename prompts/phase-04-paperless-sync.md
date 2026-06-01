# Phase 04 - Paperless Sync mit Mock-Tests

```text
Setze Phase 4 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Paperless-NGX Client implementieren
- Sync-Service implementieren
- Neue Dokumente importieren
- Idempotenz sicherstellen
- Sync-Lock gegen parallele Syncs implementieren
- TAG_REMOVED als Soft-Delete implementieren
- Sync-Log und Sync-Status implementieren
- Tests mit gemocktem Paperless-NGX schreiben

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte 5.1, F-01, F-17, UC-01, UC-02, 8 Sync-Endpunkte, 13, 17.
- Verwende die Skills .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- Keine echten Paperless-NGX-Calls in Tests.
- TAG_REMOVED darf nur nach vollstaendig erfolgreicher Pagination angewendet werden.
- Bei leerem oder fehlerhaftem Paperless-Ergebnis keine lokalen Bons entfernen.
- Parser darf fuer importierte Bons noch minimal/stubbed bleiben, wenn Phase 5 noch nicht umgesetzt ist.

Pruefkommandos:
- cd backend && mvn verify
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- Sync-Verhalten
- getestete Faelle
- geaenderte Dateien
- ausgefuehrte Pruefkommandos
- wie ich Sync lokal/mockbasiert pruefe
- offene Punkte
```

