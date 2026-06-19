# Phase 15c - Produktpreisvergleich und Exports

```text
Setze Phase 15c aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Produktseiten fuer Familien und Varianten implementieren
- Preisvergleich nach Store und optional Store+Filiale umsetzen
- Einheitenpreise fuer l, kg und Stueck berechnen und anzeigen
- Letzten bekannten Preis, historisches Minimum, Durchschnitt, Median und Verlauf auswerten
- Effektiv gezahlten Preis als Standard nutzen und regulären Preis nur anzeigen, wenn sicher ableitbar
- Ausreisser erkennen, markieren und reversibel vom Produktpreisvergleich ausschliessen
- Suche, Reports und CSV-Exports um Produktdaten erweitern

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-04, F-05, F-06, F-09, F-19, 4, 8, 9, 12, 13, 14, 15, 16 und 17.
- Verwende die Skills .codex/skills/ebon-backend, .codex/skills/ebon-frontend und .codex/skills/ebon-qa.
- Phase 15a und 15b muessen als Grundlage vorhanden sein. Falls Teile fehlen, ergaenze nur die minimal noetigen Grundlagen fuer 15c und dokumentiere das.
- Keine echten Paperless-NGX- oder OpenRouter.ai-Calls in Tests oder CI.
- Keine echten Tokens, privaten Bontexte, vollstaendigen KI-Prompts oder KI-Rohantworten ins Repository schreiben.
- Produktpreisreports duerfen unsichere oder ausgeschlossene Beobachtungen nicht still in Vergleichszahlen mischen.

Fachliche Anforderungen:
- Produktfamilienseite:
  - Vergleich ueber normalisierten Einheitenpreis
  - Verlauf ueber Zeit
  - Vergleich nach store_name
  - Umschaltbar auf store_name + store_branch
  - Anzeige zugrunde liegender Varianten und Bon-Positionen
- Produktvariantenseite:
  - konkreter Positionspreis
  - Einheitenpreis
  - letzter bekannter Preis je Store
  - historisches Minimum je Store
  - Durchschnitt je Store
  - Median je Store
  - Preisverlauf
  - zugrunde liegende Bon-Positionen
- Standardansicht zeigt:
  - letzter bekannter Preis
  - historisches Minimum
- Detailansicht zeigt:
  - Durchschnitt
  - Median
  - Verlauf
  - einzelne Preisbeobachtungen

Preislogik:
- Standard ist effektiv gezahlter Preis inklusive Rabatten und Aktionen.
- Regulärer Preis wird nur angezeigt, wenn er sicher aus unit_price, discount_amount oder erkennbaren Rabattpositionen ableitbar ist.
- Wenn regulaerer Preis nicht sicher ableitbar ist, bleibt er null/ausgeblendet und wird nicht geraten.
- Familienvergleich nutzt normalisierten Einheitenpreis:
  - EUR/l fuer ml/l
  - EUR/kg fuer g/kg
  - EUR/Stueck fuer Stueck/piece
- Variantenvergleich zeigt zusaetzlich konkreten Positionspreis.
- Mehrfachpackungen muessen korrekt berechnet werden:
  - Einzelgebinde, z.B. 0.33l
  - package_quantity, z.B. 6
  - Gesamtmenge, z.B. 1.98l
  - Einheitenpreis auf Gesamtmenge
- Unbekannte Einheiten duerfen angezeigt werden, aber nicht mit bekannten Einheiten in normalisierte Vergleiche gemischt werden.

Preisbeobachtungen:
- Reports sollen nur geeignete Beobachtungen verwenden:
  - product_assignment_status CONFIRMED oder AUTO_ASSIGNED
  - nicht NEEDS_REVIEW
  - nicht REJECTED
  - nicht NO_PRODUCT
  - exclude_from_product_price_comparison = false
- Ausgeschlossene Beobachtungen bleiben in Detailtabellen sichtbar, aber klar markiert.
- Nutzer kann Beobachtungen ausschliessen und wieder einschliessen.
- Ausschluss braucht Grund oder optionalen Kommentar.
- Ausschluss ist reversible und audit-freundlich.

Ausreisser:
- Auffaellige Preise markieren, aber nicht automatisch loeschen.
- Ausreisserwarnung kann einfache robuste Regeln verwenden, z.B. starke Abweichung von Median oder auffaellig unplausibler Einheitenpreis.
- Automatische Markierung darf keinen Preis ohne Nutzerentscheidung ausschliessen, ausser die Spezifikation im Code begruendet eine rein visuelle Warnung.
- Tests muessen die Ausreisserlogik deterministisch abdecken.

Store-Gruppierung:
- Kein neues Store-Stammdatenmodell einfuehren.
- Reports muessen Gruppierung nach store_name unterstuetzen.
- Reports muessen optional Gruppierung nach store_name + store_branch unterstuetzen.
- UI muss die Gruppierung klar benennen.

Suche/Reports/Exports:
- Bestehende Suche filterbar nach productFamilyId und productVariantId machen.
- Suchergebnisse zeigen Produktfamilie, Variante, Einheitenpreis und assignmentSource/status, soweit vorhanden.
- Bestehende Reports um Produktfilter erweitern, wo sinnvoll.
- CSV-Exports erweitern um:
  - productFamilyId
  - productFamilyName
  - productVariantId
  - productVariantName
  - productAssignmentSource
  - productAssignmentStatus
  - unitPrice
  - normalizedUnit
  - effectivePrice
  - regularPrice, wenn ableitbar
  - excludedFromProductPriceComparison
- Eigene Produktpreisvergleich-CSV-Exports bereitstellen.
- Top-Produkte duerfen einfacher starten, sollen aber mindestens haeufig gekaufte und teuerste Produkte abdecken.

Frontend-Anforderungen:
- Hauptbereich "Produkte" um Preisvergleich erweitern.
- Produktfamilien- und Varianten-Detailseiten bauen.
- Tabellen und Diagramme dicht, scannbar und deutsch beschriftet.
- Recharts fuer Verlauf/Store-Vergleich nutzen, wenn passend.
- Filterleiste fuer Zeitraum, Store-Gruppierung, Produktfamilie/Variante.
- Detailtabellen mit Link zur Bon-Position oder Bon-Detailansicht.
- Ausreisser und ausgeschlossene Beobachtungen klar markieren.
- Aktionen zum Ausschliessen/Wiedereinschliessen mit Bestaetigung oder klarer Undo-Moeglichkeit.
- Keine UI-Texte, die effektiven und regulaeren Preis verwechseln.

API/DTO-Anforderungen:
- Produktpreisreport-Endpunkte fuer Familien und Varianten.
- Query-Parameter fuer:
  - dateFrom
  - dateTo
  - store
  - grouping: STORE oder STORE_BRANCH
  - includeExcluded optional
  - page/size fuer Beobachtungslisten
- Endpunkte zum Ausschliessen/Wiedereinschliessen einzelner Preisbeobachtungen.
- CSV-Export-Endpunkte fuer Produktpreisvergleiche.
- Bestehende Search-/Report-DTOs um Produktfelder erweitern.
- OpenAPI und Frontend-Typen konsistent halten.
- Alle Endpunkte ausser GET /api/health bleiben Bearer-Token-geschuetzt.

Tests:
- Backend-Tests fuer:
  - Einheitenumrechnung ml/l
  - Einheitenumrechnung g/kg
  - Stueckpreis
  - Mehrfachpackungen
  - effektiver Preis
  - regulaerer Preis nur wenn ableitbar
  - letzter bekannter Preis
  - historisches Minimum
  - Durchschnitt
  - Median
  - Store-Gruppierung STORE
  - Store-Gruppierung STORE_BRANCH
  - unsichere/rejected/NO_PRODUCT/ausschlossene Beobachtungen werden nicht still eingerechnet
  - Ausreissermarkierung
  - Ausschliessen und Wiedereinschliessen
  - CSV-Exportspalten und Werte
- API-Contract-Tests fuer neue Report- und Export-Endpunkte.
- Frontend-Build.
- Fokussierter UI-Test oder dokumentierter manueller Test fuer:
  - Familienseite
  - Variantenseite
  - Store-Gruppierung umschalten
  - Zeitraum filtern
  - Preisbeobachtung ausschliessen/wieder einschliessen
  - CSV exportieren

Pruefkommandos:
- cd backend && mvn verify
- cd frontend && npm run build
- docker compose config
- git diff --check

Wenn moeglich:
- Produktpreisvergleich mit mehreren Stores und Varianten ueber Mock-/Seed-Daten im Browser pruefen.
- CSV herunterladen und Spalten/Werte manuell plausibilisieren.

Stoppe nach Phase 15c. Implementiere keine externe Produktdatenbank, keinen Barcode-Scan, kein Store-Stammdatenmodell und keine Mehrfachprodukt-Zuordnung einer einzelnen Bon-Position.

Am Ende bitte zusammenfassen:
- Produktpreisreport-Verhalten
- Einheiten- und Mehrfachpackungslogik
- Rabatt-/Preislogik
- Ausreisser- und Ausschlussverhalten
- Suche/Report/CSV-Erweiterungen
- neue/angepasste Endpunkte und DTOs
- UI-Flows
- getestete Faelle
- ausgefuehrte Pruefkommandos
- bekannte Restrisiken
- offene Punkte
```
