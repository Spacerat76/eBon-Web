# Phase 06 - Kategorisierung

```text
Setze Phase 6 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Regelbasierte Kategorisierung implementieren
- Prioritaetslogik: niedrigste priority gewinnt
- Kategoriequelle RULE, AI, MANUAL korrekt setzen
- Manuellen Override implementieren
- Bulk-Apply fuer Regeln vorbereiten oder implementieren
- KI-Kategorisierung mit Mock-Tests implementieren
- Verhalten ohne OPENROUTER_API_KEY: Items bleiben unkategorisiert

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-03, F-07, F-08, UC-05, UC-06, UC-07, 8 Kategorien und Regeln, 17.3.
- Verwende die Skills .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- Keine echten OpenRouter.ai-Calls in Tests.
- KI darf Kategorien vorschlagen, aber categorization_rule nur nach Nutzerbestaetigung anlegen.
- Category hard-delete nur wenn unreferenziert; sonst deaktivieren.

Pruefkommandos:
- cd backend && mvn verify
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- Kategorisierungsregeln
- getestete Faelle
- API/Service-Nutzung
- ausgefuehrte Pruefkommandos
- offene Punkte
```

