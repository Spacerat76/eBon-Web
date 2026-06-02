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
- Bonus-Handling nach Spezifikation absichern:
  - bonus_balance speichert nur neu in diesem Einkauf gesammeltes Bonusguthaben
  - bonus_points speichert nur neu in diesem Einkauf gesammelte Punkte
  - aktuelle Bonuskonto-/Punktestaende duerfen nicht als Bon-Wert gespeichert werden

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-02, F-13, 17.2 und 17.3.
- Verwende die Skills .codex/skills/ebon-parser, .codex/skills/ebon-backend und .codex/skills/ebon-qa.
- Keine echten OpenRouter.ai-Calls.
- Deutsche Zahlenformate korrekt normalisieren.
- Summentoleranz von 0.02 beachten.
- Mehrzeilige Artikelbezeichnungen zusammenfuehren.
- Position-Indizes fortlaufend halten.
- Mindestens je ein Fixture fuer REWE-Bonus und DM-Payback anlegen, das aktuelle Kontostandszeilen enthaelt und zeigt, dass nur im Einkauf neu gesammeltes Guthaben/Punkte gespeichert werden.
- Payback-Euro-Gegenwerte eines Punktestands duerfen nicht als bonus_balance uebernommen werden.

Pruefkommandos:
- cd backend && mvn verify
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- unterstuetzte Bon-Formate
- Bonus-Handling, insbesondere ignorierte Kontostands-/Punktestandszeilen
- Corpus-Dateien
- Parser-Grenzen
- ausgefuehrte Pruefkommandos
- wie ich neue Bon-Fixtures hinzufuege
- offene Punkte
```
