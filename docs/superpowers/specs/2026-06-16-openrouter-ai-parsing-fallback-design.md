# OpenRouter KI-Parsing-Fallback Design

Datum: 2026-06-16

## Ziel

Der OpenRouter KI-Parsing-Fallback rettet Bons, die der regelbasierte Parser nicht vollständig verarbeiten kann. Er ist kein Ersatz für den Regelparser. Der Regelparser bleibt primär; OpenRouter wird nur bei `PARSE_ERROR` oder explizitem Reparse-Override verwendet.

Der Fallback arbeitet als kontrollierter Hybrid:

- KI darf einen Bon automatisch als `PARSED` speichern, wenn harte Backend-Validierung erfolgreich ist.
- KI-generierte Parser-Regelvorschläge werden nie automatisch aktiv.
- Regelvorschläge sind in UI und Admin-Bereich sichtbar, erklärbar, editierbar, akzeptierbar und ablehnbar.
- Akzeptierte Vorschläge werden sofort als aktive `parse_rule` gespeichert und können zusätzlich als Flyway-Migration exportiert werden.

## Zentrale Entscheidungen

- Fallback ist konfigurierbar und standardmäßig aktiv, wenn OpenRouter eingerichtet ist.
- Manueller Reparse bietet einen Override für KI-Fallback.
- Bei `FULL_TEXT` muss der Nutzer ausdrücklich bestätigen, dass vollständiger Bontext an OpenRouter gesendet wird.
- Automatischer Sync hat ein konfigurierbares KI-Call-Limit, Default `25` pro Sync-Lauf.
- Wird das Limit erreicht, bleibt der Bon `PARSE_ERROR` mit explizitem Grund.
- KI-Parsing und KI-Kategorisierung bleiben getrennte Workflows.
- Parsing bekommt eigene Settings, eigenes Log und eigene UI.
- Erfolgreiche KI-Parses setzen `receipt.parse_source = AI`.
- Spätere valide Regelparser-Ergebnisse dürfen KI-Parses ersetzen und `parse_source = RULE` setzen.
- Prompt- und Rohantwortdaten werden standardmäßig nicht gespeichert.
- Gekürzte/maskierte Snippets sind nur für lokale Entwicklung zulässig.

## Datenmodell

### `receipt.parse_source`

Neues Feld zur Herkunft des aktuellen Parsergebnisses:

- `RULE`
- `AI`
- optional später `MANUAL_CORRECTED`

### `ai_parsing_log`

Eigenes Audit-Log für KI-Parsing, getrennt von `ai_categorization_log`.

Wichtige Felder:

- `receipt_id`
- `trigger`: `SYNC_AUTO`, `MANUAL_REPARSE`, `MANUAL_REPARSE_FORCE_FULL_TEXT`, `BULK_REPARSE`, `SETTINGS_TEST`
- `status`: `SUCCESS`, `FAILED`, `SKIPPED_LIMIT`, `INVALID_RESPONSE`, `LOW_CONFIDENCE`, `DISABLED`, `NO_API_KEY`
- Modell, Start/Ende, Dauer, Token-/Usage-Daten soweit verfügbar
- Fehlergrund vor dem KI-Fallback
- Fehlergrund nach gescheitertem Fallback
- Gesamtkonfidenz
- Feld-Konfidenzen
- Warnungen
- optionale gekürzte/maskierte Snippets für lokale Entwicklung

### `parse_rule_suggestion`

Eigener Workflow für KI-vorgeschlagene Parser-Regeln.

Wichtige Felder:

- Bezug zu `ai_parsing_log` und optional `receipt`
- `store_name`
- `rule_type`
- `match_regex`
- `extract_group`
- `confidence`
- `trigger`
- `problem_description`
- `solution_rationale`
- `validation_status`
- `validation_message`
- `status`: `OPEN`, `ACCEPTED`, `REJECTED`
- `rejection_reason`
- `accepted_parse_rule_id`

## OpenRouter-Aufruf

Der Client nutzt OpenRouter Chat Completions unter `/chat/completions`. Soweit Modell und API es unterstützen, wird `response_format` mit JSON-Schema verwendet. Das Backend validiert jede Antwort trotzdem selbst.

Der Prompt enthält:

- minimierten oder vollständigen Bontext
- Regelparser-Teilparse
- Fehlergrund des Regelparsers
- Pflichtfelder und Summenvalidierung
- Bonusdefinition: nur im Einkauf neu gesammelte Punkte/Guthaben
- Anweisung, ausschließlich JSON zurückzugeben
- optional die Bitte um Parser-Regelvorschläge

## Validierung

Automatische Übernahme als `PARSED` nur wenn:

- JSON syntaktisch valide ist
- Schema valide ist
- Pflichtfelder vorhanden sind
- `overallConfidence >= aiParsingMinConfidence`
- `receiptDate`, `storeName`, `totalAmount` gültig sind
- mindestens eine Position mit `description` und `totalPrice` vorhanden ist
- `positionIndex` fortlaufend ist
- Zahlenformate korrekt normalisiert werden
- Summentoleranz `0.02` eingehalten ist

Ungültige `parseRuleSuggestions` blockieren einen ansonsten validen KI-Parse nicht.

## Regelvorschläge

Parser-Regelvorschläge werden vor Speicherung validiert:

- Regex syntaktisch valide
- Regex passt auf den Beispiel-Bon
- erwartetes Feld wird extrahiert
- keine offensichtlichen Steuer-, TSE-, Signatur- oder Zahlungszeilen als Item-Kandidaten

Vorschläge bleiben inaktiv, bis der Nutzer sie akzeptiert. Abgelehnte Vorschläge bleiben mit Status `REJECTED` und Ablehnungsgrund erhalten.

Beim Akzeptieren:

- Nutzer kann Regex, Regeltyp, Extract-Group und Store-Kontext bearbeiten.
- Backend validiert erneut.
- Aktive `parse_rule` mit `source = AI_ADAPTED` wird erzeugt.
- Nutzer wählt Reparse-Scope: keiner, aktueller Bon, gleicher Store, alle `PARSE_ERROR`.
- Akzeptierte Regeln können als Flyway-Migrationsentwurf exportiert werden.

## UI

Bon-Detail:

- Badge „per KI geparst" bei `parse_source = AI`
- kompakter KI-Parsing-Status
- aufklappbares technisches Log
- relevante Parser-Regelvorschläge mit Auslöser, Problem und Lösungsbegründung

Settings/Admin:

- KI-Parsing-Konfiguration
- zentrale Liste für Parser-Regelvorschläge
- Bearbeiten, Akzeptieren, Ablehnen
- Migrationsexport
- anonymisierte Fixture-Vorschau und lokaler Export außerhalb des Test-Corpus

## Tests

Pflichttests:

- OpenRouter-Client mit gemockter API
- valides KI-JSON wird akzeptiert
- invalides JSON wird abgelehnt
- schema-invalides JSON wird abgelehnt
- niedrige Konfidenz wird abgelehnt
- Summenvalidierung wird erzwungen
- fehlender API-Key und deaktivierter Fallback werden sauber geloggt
- Sync-Limit wird eingehalten
- `FULL_TEXT` ohne Bestätigung wird abgelehnt
- `ai_parsing_log` wird korrekt geschrieben
- Prompt-/Antwort-Snippets sind standardmäßig leer
- Parser-Regelvorschläge werden validiert, gespeichert, bearbeitet, akzeptiert und abgelehnt
- Akzeptieren erzeugt aktive `parse_rule`
- Migrationsexport erzeugt SQL-Entwurf
- Backup/Restore enthält `ai_parsing_log` und `parse_rule_suggestion`
- UI zeigt Badge, Log, Vorschläge und Bestätigungsdialog

## Nicht-Ziele

- Keine echten OpenRouter-Calls in Tests oder CI.
- Keine automatische Aktivierung KI-generierter Parser-Regeln.
- Keine automatische Erstellung von `categorization_rule` durch KI-Parsing.
- Keine Speicherung vollständiger Prompts oder KI-Rohantworten im Default.
- Keine Modell-Fallback-Kette in der ersten Umsetzung.
