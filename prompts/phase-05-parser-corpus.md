# Phase 05 - Parser und Test-Corpus

```text
Setze Phase 5 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Regelbasierten Bon-Parser implementieren
- Parser-Test-Corpus unter backend/src/test/resources/corpus/ anlegen
- Mindestens diese Fixtures anlegen:
  - rewe_simple.txt / .expected.json
  - aldi_discount.txt / .expected.json
  - dm_bonus.txt / .expected.json
  - lidl_multiline_items.txt / .expected.json
  - parse_error_missing_total.txt / .expected.json
- KI-Parsing-Fallback als Interface/Service mit Mock-Tests vorbereiten
- Valides KI-JSON akzeptieren, invalides KI-JSON ablehnen

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-02, F-13, 17.2 und 17.3.
- Verwende die Skills .codex/skills/ebon-parser, .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- Keine echten OpenRouter.ai-Calls.
- Deutsche Zahlenformate korrekt normalisieren.
- Summentoleranz von 0.02 beachten.
- Mehrzeilige Artikelbezeichnungen zusammenfuehren.
- Position-Indizes fortlaufend halten.

Pruefkommandos:
- cd backend && mvn verify
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- unterstuetzte Bon-Formate
- Corpus-Dateien
- Parser-Grenzen
- ausgefuehrte Pruefkommandos
- wie ich neue Bon-Fixtures hinzufuege
- offene Punkte
```

