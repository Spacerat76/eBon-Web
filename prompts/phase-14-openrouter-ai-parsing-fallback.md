# Phase 14 - OpenRouter KI-Parsing-Fallback

```text
Setze Phase 14 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Echten OpenRouter-Client fuer den KI-Parsing-Fallback implementieren
- Kontrollierten Hybrid-Fallback umsetzen: KI darf einen Bon nur bei strenger Validierung automatisch als PARSED uebernehmen
- Separate KI-Parsing-Settings implementieren
- receipt.parse_source einfuehren und in API/UI sichtbar machen
- ai_parsing_log einfuehren
- parse_rule_suggestion als eigenen Workflow einfuehren
- Parser-Regelvorschlaege in der UI sichtbar, pruefbar, editierbar, akzeptierbar und ablehnbar machen
- Akzeptierte Vorschlaege als aktive parse_rule uebernehmen
- Export akzeptierter Parser-Regeln als Flyway-Migrationsentwurf implementieren
- Anonymisierte Fixture-Vorschau und lokalen Export ausserhalb des Corpus vorbereiten
- Tests mit gemocktem OpenRouter schreiben

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-02, F-11, F-12, 4.1.1, 4.1.5a, 4.1.5b, 4.1.7, 8, 9.2.3, 9.2.6, 9.2.9, 12, 13, 14, 16 und 17.
- Verwende die Skills .codex/skills/ebon-backend, .codex/skills/ebon-parser, .codex/skills/ebon-frontend und .codex/skills/ebon-qa.
- Keine echten OpenRouter.ai-Calls in Tests oder CI.
- Keine echten Tokens, privaten Bontexte, Prompt-Rohdaten oder KI-Rohantworten ins Repository schreiben.
- Bestehende KI-Kategorisierung nicht mit KI-Parsing vermischen.
- Bestehende Regelparser-Tests und Corpus-Fixtures muessen gruen bleiben.

Fachliche Anforderungen:
- Regelparser bleibt primaer.
- KI-Parsing-Fallback wird nur bei PARSE_ERROR oder explizitem Reparse-Override genutzt.
- Globaler Fallback-Schalter: Default aktiv.
- Sync-Limit: konfigurierbar, Default 25 KI-Parsing-Calls pro Sync-Lauf.
- Wenn das Sync-Limit erreicht ist: Bon bleibt PARSE_ERROR mit erklaerendem Grund.
- Textmodus: MINIMIZED als Default, FULL_TEXT nur mit expliziter Bestaetigung bei manuellem Reparse.
- OpenRouter-Prompt enthaelt raw_text/minimized_text, Regelparser-Teilparse und Fehlergrund.
- KI muss vollstaendiges finales JSON liefern.
- Automatische Uebernahme nur wenn:
  - JSON syntaktisch valide ist
  - Schema valide ist
  - Pflichtfelder vorhanden sind
  - positionIndex fortlaufend ist
  - Zahlen und Datumswerte valide sind
  - Summentoleranz 0.02 eingehalten ist
  - overallConfidence >= aiParsingMinConfidence ist
- Bei erfolgreicher KI-Uebernahme: parse_status = PARSED, parse_source = AI.
- Bei spaeterem validem Regelparser-Reparse darf parse_source wieder RULE werden.
- Prompt- und Rohantwortdaten werden standardmaessig nicht gespeichert.
- Optionale lokale Debug-Snippets muessen gekuerzt und maskiert sein.
- KI-generierte parseRuleSuggestions duerfen den Parse-Erfolg nicht blockieren.
- Parser-Regelvorschlaege muessen vor Speicherung validiert werden:
  - Regex syntaktisch valide
  - Regex passt auf den Beispiel-Bon
  - erwartetes Feld wird extrahiert
  - keine offensichtlichen Steuer-/TSE-/Zahlungszeilen als Items
- Vorschlaege werden nicht automatisch aktiv.
- Abgelehnte Vorschlaege bleiben mit Status REJECTED und Ablehnungsgrund erhalten.
- Akzeptierte Vorschlaege erzeugen aktive parse_rule mit source = AI_ADAPTED.
- Beim Akzeptieren muss der Nutzer waehlen koennen:
  - kein sofortiger Reparse
  - aktueller Bon
  - alle PARSE_ERROR-Bons desselben Stores
  - alle PARSE_ERROR-Bons
- Export akzeptierter Regeln erzeugt einen Flyway-Migrationsentwurf, aber keinen automatischen Commit.
- Fixture-Vorschau anonymisiert sensible Daten und exportiert lokal ausserhalb backend/src/test/resources/corpus/.

API/UI-Anforderungen:
- ReceiptDTO enthaelt parseSource und aiParsingSummary.
- Bon-Detail zeigt Badge "per KI geparst", KI-Parsing-Status und aufklappbares technisches Log.
- Bon-Detail zeigt relevante Parser-Regelvorschlaege mit Ausloeser, Problem und Loesungsbegruendung.
- Einstellungen enthalten KI-Parsing-Fallback-Konfiguration.
- Einstellungen enthalten zentrale Parser-Regelvorschlagsverwaltung.
- FULL_TEXT-Reparse verlangt sichtbare Zusatzbestaetigung.
- Secrets und OpenRouter API-Key bleiben maskiert.

Tests:
- OpenRouter-Client mit MockWebServer/WireMock oder vergleichbarem Testdouble.
- Valides KI-JSON wird akzeptiert.
- Invalides JSON wird abgelehnt.
- Schema-invalides JSON wird abgelehnt.
- Zu niedrige overallConfidence wird abgelehnt.
- Summentoleranz-Verletzung wird abgelehnt.
- Fehlender API-Key fuehrt zu sauberem Skip.
- Deaktivierter Fallback fuehrt zu sauberem Skip.
- Sync-Limit wird eingehalten.
- FULL_TEXT ohne Bestaetigung wird abgelehnt.
- ai_parsing_log wird fuer Success, Failed, Skipped Limit und Invalid Response geschrieben.
- Prompt-/Antwort-Snippets werden standardmaessig nicht gespeichert.
- Parser-Regelvorschlaege werden validiert, gespeichert, bearbeitet, akzeptiert, abgelehnt.
- Akzeptieren erzeugt aktive parse_rule.
- Migrationsexport erzeugt erwarteten SQL-Entwurf.
- Backup/Restore enthaelt ai_parsing_log und parse_rule_suggestion ohne Klartext-Prompt/Rohantwort.
- Frontend-Build und relevante UI-Tests fuer Badge, Log, Vorschlagsliste und Bestaetigungsdialog.

Pruefkommandos:
- cd backend && mvn verify
- cd frontend && npm run build
- docker compose config
- git diff --check

Wenn moeglich:
- docker compose up --build smoke-testen
- Manuell einen bekannten PARSE_ERROR-Bon mit gemockter oder lokaler OpenRouter-Testkonfiguration reparsen

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- implementiertes KI-Parsing-Verhalten
- neue Datenbanktabellen/Felder
- neue/angepasste Endpunkte und DTOs
- UI-Flows
- getestete Faelle
- ausgefuehrte Pruefkommandos
- bekannte Restrisiken
- offene Punkte
```
