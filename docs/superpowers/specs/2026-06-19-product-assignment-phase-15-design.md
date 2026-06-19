# Produktzuordnung und Preisvergleich Phase 15 Design

Datum: 2026-06-19

## Ziel

Phase 15 erweitert eBon-Web um eine Produktschicht oberhalb der Bon-Positionen. Mehrere unterschiedliche Bontexte sollen demselben realen Produkt zugeordnet werden können, ohne Produktvarianten zu vermischen. Beispiel: `CC Zero` und `Coca Cola Zero` können dieselbe Produktfamilie meinen; `Coca Cola Zero 0,33l` und `Coca Cola Zero 0,5l` sind unterschiedliche Varianten.

Das Ziel ist ein belastbarer Preisvergleich:

- Variantenvergleich für konkrete Kaufentscheidungen, z.B. kleine `0,33l`-Flaschen für unterwegs.
- Familienvergleich über Einheitenpreise, z.B. `€/l` für `Coca Cola Zero` unabhängig von der gekauften Gebindegröße.
- Kontrollierte Automatisierung über Regeln, klare Historie und KI-Vorschläge.
- Review- und Korrektur-Workflows, damit falsche Produkt-Merges Preisreports nicht dauerhaft verfälschen.

## Teilphasen

### Phase 15a: Produktfundament

Phase 15a liefert Datenmodell, Backend-Services und Grundintegration:

- Produktfamilien und Produktvarianten.
- Produktzuordnung an `receipt_item`.
- Produktregeln und Synonyme.
- Automatische Zuordnung nach Sync, Reparse und manuellem Neustart.
- Backup/Restore- und Reset-Regeln für Produktdaten.

### Phase 15b: Review und Pflege

Phase 15b liefert die Arbeitsoberflächen für Datenqualität:

- Prüfliste für unsichere Produktzuordnungen.
- Manuelle Korrektur einzelner Positionen.
- Produktfamilien und Varianten erstellen, bearbeiten, zusammenführen und trennen.
- Regelvorschläge aus manuellen Zuordnungen.
- Rückwirkendes Anwenden mit Vorschau und Bestätigung.

### Phase 15c: Preisvergleich und Exports

Phase 15c liefert die Auswertung:

- Produktseiten für Familien und Varianten.
- Händlervergleich mit letztem Preis, historischem Minimum, Durchschnitt, Median und Verlauf.
- Einheitenpreise für `€/l`, `€/kg` und `€/Stück`.
- Ausreißerhandling.
- Erweiterung von Suche, Reports und CSV-Exports um Produktdaten.

## Zentrale Entscheidungen

- Das Modell besteht aus Produktfamilie und Produktvariante.
- Marke wird nicht separat gespeichert; sie bleibt Teil des Namens.
- Pfand kann als eigene Produktfamilie/Variante geführt werden.
- Eine Bon-Position kann maximal einem Produkt zugeordnet werden.
- Mehrfachpackungen speichern Einzelgebinde und Packungsgröße, z.B. `6x0,33l`.
- Externe Produktdatenbanken sind nicht Teil von Phase 15. EAN/GTIN wird nur vorbereitet.
- Produktregeln bleiben getrennt von Kategorisierungsregeln.
- Produktfamilien können eine Standard-Kategorie haben und damit leere Kategorien füllen.
- Bestehende oder manuell gesetzte Kategorien werden durch Produktzuordnung nicht überschrieben.
- Store-Gruppierung wird vorbereitet, aber kein eigenes Store-Stammdatenmodell eingeführt.
- KI-Produktzuordnung nutzt die bestehende OpenRouter-/KI-Kategorisierungsintegration und bekommt in Phase 15 kein eigenes Sync-Call-Limit.

## Datenmodell

### `product_family`

Eine Produktfamilie beschreibt das fachlich vergleichbare Produkt auf Mengenebene.

Wichtige Felder:

- `id`
- `name`, z.B. `Coca Cola Zero`
- `default_category_id`, optionaler FK auf `category`
- `is_active`
- `created_at`
- `updated_at`

`default_category_id` darf nur genutzt werden, um bei zugeordneten Positionen eine leere Kategorie zu füllen. Es darf keine bestehende Kategorie überschreiben.

### `product_variant`

Eine Produktvariante beschreibt das konkrete Gebinde oder die konkrete Kaufvariante.

Wichtige Felder:

- `id`
- `product_family_id`
- `name`, z.B. `Coca Cola Zero 0,33l Flasche`
- `unit_quantity`, z.B. `0.33`
- `unit`, z.B. `l`, `ml`, `kg`, `g`, `piece`
- `package_quantity`, z.B. `6` bei `6x0,33l`
- `package_description`, z.B. `Flasche`, `Dose`, `Packung`
- `total_quantity`, berechnet oder gespeichert für Preisvergleich
- `gtin`, optional vorbereitet, aber ohne externe Abfrage
- `is_active`
- `created_at`
- `updated_at`

Bekannte Einheiten werden normalisiert:

- `ml` und `l` auf Liter-Vergleich.
- `g` und `kg` auf Kilogramm-Vergleich.
- Stückzahlen auf `€/Stück`.

Unbekannte Einheiten dürfen gespeichert werden, sind aber nicht automatisch mit anderen Einheiten vergleichbar.

### `receipt_item`-Erweiterung

Bon-Positionen erhalten optionale Produktfelder:

- `product_family_id`, nullable FK.
- `product_variant_id`, nullable FK.
- `product_assignment_source`: `RULE`, `AI`, `MANUAL`, `HISTORY`.
- `product_assignment_status`: `CONFIRMED`, `AUTO_ASSIGNED`, `NEEDS_REVIEW`, `REJECTED`, `NO_PRODUCT`.
- `product_assignment_confidence`, nullable.
- `product_assignment_updated_at`.
- `exclude_from_product_price_comparison`, Default `false`.
- `product_price_exclusion_reason`, nullable.

Reine Rabatte, Coupons, Zahlungszeilen und Rundungsdifferenzen werden standardmäßig als `NO_PRODUCT` geführt. Pfand und Tüten können normale Produktfamilien sein, wenn sie ausgewertet werden sollen.

## Produktregeln und Historie

Produktregeln ordnen Bontexte einer Produktfamilie und optional einer Variante zu.

Wichtige Regelmerkmale:

- Match gegen Positionsbeschreibung.
- Optionaler Store-Kontext über `store_name`.
- Globale Regeln gelten für alle Stores.
- Store-spezifische Regeln können globale Regeln übersteuern.
- Match-Typen orientieren sich an bestehenden Kategorisierungsregeln: `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `EXACT`, `REGEX`.
- Beim Speichern kann der Nutzer wählen, ob die Regel nur künftig oder auch auf bestehende Positionen angewendet wird.
- Vor rückwirkendem Anwenden zeigt das Backend eine Vorschau mit betroffenen Positionen.

Klare Historie darf eine automatische Zuordnung stützen, wenn aus dem Bontext keine Variante sicher erkennbar ist. Die Schwellen sind konfigurierbar. Konservative Defaults:

- mindestens 3 frühere vertrauenswürdige Treffer für denselben normalisierten Positions- und Store-Kontext;
- mindestens 90 Prozent derselben Variante.

Als vertrauenswürdige Historie zählen:

- manuelle Zuordnungen;
- akzeptierte Vorschläge und Regeln;
- regelbasierte automatische Treffer.

Reine KI-Treffer zählen nicht allein als vertrauenswürdige Historie für spätere unsichere Variantenentscheidungen.

## Automatische Zuordnung

Produktzuordnung läuft automatisch nach Parsing und Kategorisierung:

1. Produktregel oder Synonym.
2. Klare Historie.
3. KI-Fallback.
4. Sonst `NEEDS_REVIEW` oder `NO_PRODUCT`, je nach erkannter Positionsart.

Der Workflow kann zusätzlich manuell gestartet werden:

- für einen Bon;
- für alle offenen Positionen;
- nach dem Erstellen oder Ändern einer Produktregel;
- nach Merge/Split-Korrekturen.

KI darf Produktfamilie und Variante automatisch setzen, wenn Konfidenz, erkannte Einheit, Größe und Preislogik plausibel sind. Unsichere KI-Ergebnisse landen in der Prüfliste. KI-Produktzuordnung darf normalisierte Positionsdaten, Store, Preis und Menge verwenden. Vollständige Raw-Receipt-Texte werden nicht gesendet.

## Review und Pflege

Phase 15b führt eine Prüfliste `Produktzuordnung prüfen` ein.

Die Liste zeigt Positionen mit:

- `NEEDS_REVIEW`;
- niedriger oder widersprüchlicher Konfidenz;
- unklarer Größe;
- Konflikten zwischen Regel, Historie und KI;
- auffälligen Preisen.

Standard-Sortierung ist nach Nutzen: häufige und teure Positionen zuerst. Filter:

- Unsicherheit;
- Store;
- Produktfamilie;
- Kategorie;
- Zeitraum;
- Zuordnungsquelle;
- Status.

Jeder Eintrag zeigt:

- Bon-Datum;
- Store und optional Filiale;
- Positionsbeschreibung;
- Menge, Einheit, Einzelpreis und Gesamtpreis;
- vorgeschlagene Familie;
- vorgeschlagene Variante;
- Konfidenz;
- Begründung des Vorschlags;
- Vorschau möglicher rückwirkender Auswirkungen.

Aktionen:

- Vorschlag akzeptieren.
- Produktfamilie/Variante korrigieren.
- Neue Produktfamilie oder Variante anlegen.
- Vorschlag ablehnen.
- Position als `NO_PRODUCT` markieren.
- Regel aus manueller Zuordnung vorschlagen und mit Vorschau bestätigen.

## Korrekturen

Korrektur-Workflows sind Pflicht:

- Zwei Produktfamilien oder Varianten zusammenführen.
- Falsch zusammengelegte Familien oder Varianten trennen.
- Einzelne Bon-Positionen auf ein anderes Produkt setzen.
- Produktzuordnung einer Position entfernen.
- Position als `NO_PRODUCT` markieren.

Rückwirkende Änderungen brauchen immer Vorschau und Bestätigung. Die Vorschau nennt Anzahl betroffener Positionen, betroffene Stores, Zeitraum und mögliche Report-Auswirkungen.

Beim Reparse werden Produktzuordnungen neu abgeleitet. Bestätigte Zuordnungen sollen bestmöglich übertragen werden, wenn Beschreibung, Preis und Menge plausibel passen. Konflikte werden sichtbar gemacht, statt still überschrieben zu werden.

## Preisvergleich

Produktpreisreports nutzen bestätigte und automatisch zugeordnete Preisbeobachtungen. Unsichere oder ausgeschlossene Beobachtungen werden nicht still in Vergleichszahlen gemischt.

Für Produktfamilien:

- Vergleich über normalisierten Einheitenpreis, z.B. `€/l`, `€/kg`, `€/Stück`.
- Verlauf über Zeit.
- Vergleich nach `store_name`.
- Umschaltbar auf `store_name + store_branch`.

Für Produktvarianten:

- konkreter Positionspreis;
- Einheitenpreis;
- letzter bekannter Preis je Store;
- historisches Minimum je Store;
- Durchschnitt und Median;
- Preisverlauf;
- zugrunde liegende Bon-Positionen.

Preislogik:

- Standard ist der effektiv gezahlte Preis inklusive Rabatten und Aktionen.
- Wenn ableitbar, wird zusätzlich ein regulärer Preis ohne Rabatt angezeigt.
- Reguläre Preise dürfen aus `unit_price`, `discount_amount` oder erkennbaren Rabattpositionen abgeleitet werden.
- Bei Mehrfachpackungen werden Einzelgebinde und Gesamtmenge berücksichtigt.

Ausreißerhandling:

- Auffällige Preise werden markiert.
- Nutzer kann einzelne Preisbeobachtungen vom Produktpreisvergleich ausschließen.
- Ausgeschlossene Beobachtungen bleiben auditierbar und können wieder eingeschlossen werden.

## Frontend

Navigation wird geteilt:

- Hauptnavigation `Produkte` für Prüfliste, Produktseiten und Preisvergleich.
- Einstellungen oder Verwaltungs-Tab für Produktfamilien, Varianten und Produktregeln.

Bon-Detailansicht:

- zeigt Produktfamilie und Variante pro Position;
- zeigt Quelle und Prüfstatus;
- zeigt berechneten Einheitenpreis kompakt;
- erlaubt schnelle Korrektur der Produktzuordnung.

Suche, Reports und Exports:

- Filter nach Produktfamilie und Variante;
- Anzeige von Produktfamilie, Variante, Einheitenpreis und Zuordnungsquelle;
- bestehende CSV-Exports werden erweitert;
- Produktpreisvergleiche bekommen eigene CSV-Exports.

## API

Neue API-Bereiche:

- Produktfamilien verwalten.
- Produktvarianten verwalten.
- Produktregeln verwalten und Vorschau berechnen.
- Produktzuordnung für einzelne Bon-Positionen setzen.
- Produktzuordnung für Bon oder offene Positionen neu ausführen.
- Prüfliste abrufen und Aktionen ausführen.
- Merge/Split/Korrektur-Operationen.
- Produktpreisreports und CSV-Exports.
- Produktdaten-Reset als separate, explizit bestätigte Wartungsaktion.

Alle Endpunkte außer `GET /api/health` bleiben durch `Authorization: Bearer <APP_API_TOKEN>` geschützt. DTOs, OpenAPI und Frontend-Typen müssen konsistent bleiben.

## KI, Datenschutz und Logging

KI-Produktzuordnung orientiert sich an der bestehenden KI-Kategorisierung:

- Erlaubt sind normalisierte Positionsdaten, Store, Preis und Menge.
- Vollständige Bon-Rohtexte werden nicht gesendet.
- Tests verwenden ausschließlich Mocks oder Test-Doubles.
- Es gibt in Phase 15 kein eigenes Produkt-KI-Sync-Call-Limit.
- Produkt-KI nutzt die bestehende OpenRouter-Konfiguration der Kategorisierung.

KI-Versuche werden nachvollziehbar protokolliert, ohne Secrets zu speichern. Vollständige Prompts und Rohantworten werden standardmäßig nicht gespeichert.

## Backup, Restore und Datenwartung

Backup/Restore umfasst:

- Produktfamilien;
- Produktvarianten;
- Produktregeln;
- Produktzuordnungen;
- Reviewstatus;
- ausgeschlossene Preisbeobachtungen;
- KI-/Review-Auditdaten, soweit vorhanden und ohne vollständige Prompts/Rohantworten.

Der bestehende Reset importierter Bon-Daten behält Produktstammdaten und Produktregeln. Zuordnungen an gelöschten Bon-Positionen verschwinden mit den importierten Bon-Daten.

Zusätzlich gibt es eine separate Wartungsaktion zum Zurücksetzen von Produktdaten. Diese Aktion braucht eine deutliche Bestätigung und ist getrennt vom bestehenden Reset importierter Bon-Daten.

## Verifikation

### Phase 15a

Mindestnachweis:

- Flyway-Migrationen für Produktdatenmodell.
- Repository- und Service-Tests für Produktfamilien, Varianten, Regeln und Zuordnung.
- Tests für automatische Zuordnung über Regel, Historie und KI-Mock.
- Tests, dass Produktfamilien nur leere Kategorien füllen.
- Tests für Backup/Restore- und Reset-Semantik.
- `cd backend && mvn verify`.

### Phase 15b

Mindestnachweis:

- API-Contract-Tests für Prüfliste, Akzeptieren, Ablehnen, `NO_PRODUCT`, manuelle Korrektur und Regelvorschau.
- Tests für Merge, Split und rückwirkendes Anwenden mit Vorschau.
- Frontend-Build.
- Fokussierter UI-Test oder manuelle Prüfliste für Produktreview-Flows.
- `cd backend && mvn verify`.
- `cd frontend && npm run build`.

### Phase 15c

Mindestnachweis:

- Tests für Einheitenumrechnung und Mehrfachpackungen.
- Report-Tests für letzten Preis, historisches Minimum, Durchschnitt und Median.
- Tests für Rabattlogik und effektiv gezahlten Preis.
- Tests für Ausreißerausschluss.
- Tests für CSV-Export.
- Frontend-Build.
- Fokussierter UI- oder E2E-Smoke-Test für Produktvergleich.
- `cd backend && mvn verify`.
- `cd frontend && npm run build`.

## Nicht Teil von Phase 15

- Externe Produktdatenbank-Integration.
- Barcode-Scan-Workflow.
- Eigenes Store-Stammdatenmodell.
- Mehrfachprodukt-Zuordnung einer einzelnen Bon-Position.
- Separate KI-Produktsettings oder eigenes Produkt-KI-Sync-Call-Limit.
