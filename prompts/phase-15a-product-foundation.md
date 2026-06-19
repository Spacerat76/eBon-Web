# Phase 15a - Produktfundament

```text
Setze Phase 15a aus ebon-specification.md Abschnitt 16 um.

Ziel:
- Produktfamilien und Produktvarianten als Stammdaten einfuehren
- Produktzuordnung an receipt_item ergaenzen
- Produktregeln/Synonyme fuer automatische Zuordnung implementieren
- Automatische Produktzuordnung nach Sync, Reparse und manuellem Neustart einbauen
- Klare-Historie-Zuordnung mit konservativen, konfigurierbaren Defaults implementieren
- KI-Produktzuordnung mit gemocktem OpenRouter/KI-Client vorbereiten
- Backup/Restore und Datenwartungs-Reset fuer Produktdaten korrekt erweitern

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte F-19, 4, 6, 7, 8, 10, 12, 13, 14, 15, 16 und 17.
- Verwende die Skills .codex/skills/ebon-backend, .codex/skills/ebon-parser und .codex/skills/ebon-qa.
- Keine echten Paperless-NGX- oder OpenRouter.ai-Calls in Tests oder CI.
- Keine echten Tokens, privaten Bontexte, vollstaendigen KI-Prompts oder KI-Rohantworten ins Repository schreiben.
- Produktregeln nicht mit Kategorisierungsregeln vermischen.
- Bestehende Parser-, Sync-, Kategorisierungs-, Backup- und API-Tests muessen gruen bleiben.

Fachliche Anforderungen:
- Modell besteht aus Produktfamilie und Produktvariante.
- Marke wird nicht separat gespeichert; sie bleibt Teil des Namens.
- Pfand und Tueten koennen normale Produktfamilien sein.
- Eine Bon-Position darf maximal eine Produktzuordnung haben.
- Varianten mit unterschiedlicher Groesse, Einheit oder Packungsstruktur duerfen nicht automatisch zusammengefuehrt werden.
- Produktfamilie kann eine optionale default_category_id haben.
- default_category_id darf nur leere Kategorien setzen und niemals bestehende oder manuelle Kategorien ueberschreiben.
- Produktvariante speichert mindestens:
  - product_family_id
  - name
  - unit_quantity
  - unit
  - package_quantity
  - package_description
  - total_quantity oder sauber berechenbare Gesamtmenge
  - optional gtin, ohne externe Produktdatenbank-Abfrage
  - active/status und Zeitstempel
- Bekannte Einheiten normalisieren:
  - ml/l fuer Litervergleich
  - g/kg fuer Kilogrammvergleich
  - Stueck/piece fuer Stueckvergleich
- Unbekannte Einheiten duerfen gespeichert werden, sind aber nicht automatisch vergleichbar.

Datenmodell:
- Flyway-Migrationen fuer neue Produkt-Tabellen anlegen, mindestens:
  - product_family
  - product_variant
  - product_rule oder aequivalente Regel-/Synonym-Tabelle
  - ggf. product_assignment_log oder KI-/Review-Audit, wenn fuer Nachvollziehbarkeit noetig
- receipt_item erweitern um:
  - product_family_id nullable
  - product_variant_id nullable
  - product_assignment_source: RULE, AI, MANUAL, HISTORY
  - product_assignment_status: CONFIRMED, AUTO_ASSIGNED, NEEDS_REVIEW, REJECTED, NO_PRODUCT
  - product_assignment_confidence nullable
  - product_assignment_updated_at
  - exclude_from_product_price_comparison default false
  - product_price_exclusion_reason nullable
- Sinnvolle Indizes fuer Produktzuordnung, Produktreports und Regelmatching anlegen.
- Constraints muessen verhindern, dass product_variant ohne passende product_family persistiert wird.
- Keine fake Produktfamilie fuer "kein Produkt" anlegen; NO_PRODUCT ist Status an der Position.

Produktregeln und Historie:
- Produktregeln matchen gegen receipt_item.description.
- Regeln koennen global gelten oder optional auf store_name eingeschraenkt sein.
- Store-spezifische Regeln duerfen globale Regeln uebersteuern.
- Match-Typen analog zu Kategorisierungsregeln unterstuetzen:
  - CONTAINS
  - STARTS_WITH
  - ENDS_WITH
  - EXACT
  - REGEX
- Regelvorschau fuer rueckwirkende Anwendung im Backend bereitstellen.
- Beim Anwenden auf bestehende Positionen nur mit explizitem Apply-Aufruf aendern, nicht still beim Speichern.
- Klare Historie darf nur aus vertrauenswuerdigen Zuordnungen entstehen:
  - manuelle Zuordnungen
  - akzeptierte Vorschlaege/Regeln
  - regelbasierte automatische Treffer
- Reine KI-Treffer duerfen nicht allein klare Historie fuer spaetere automatische Variantenentscheidungen bilden.
- Konservative Defaults fuer Historie:
  - mindestens 3 fruehere vertrauenswuerdige Treffer fuer denselben normalisierten Positions-/Store-Kontext
  - mindestens 90 Prozent dieselbe Variante
- Defaults in app_settings oder aequivalenter Konfiguration hinterlegen.

Automatische Zuordnung:
- Produktzuordnung laeuft nach Parsing und Kategorisierung.
- Reihenfolge:
  1. Produktregel/Synonym
  2. klare Historie
  3. KI-Fallback
  4. NEEDS_REVIEW oder NO_PRODUCT
- Automatische Zuordnung muss bei neuen Sync-Receipts laufen.
- Automatische Zuordnung muss nach Reparse neu ableitbar sein.
- Zusaetzlich manuelle Startpunkte anbieten:
  - einzelner Bon
  - alle offenen Positionen
  - nach Regelanlage oder Regelaenderung
- Nichtprodukt-Zeilen wie reine Rabatte, Coupons, Zahlungszeilen und Rundungsdifferenzen sollen als NO_PRODUCT markierbar sein.
- Deposit/Pfand nicht pauschal ausschliessen; diese Zeilen koennen Produktfamilien sein.

KI-Anforderungen:
- Produkt-KI nutzt in Phase 15 die bestehende KI-Kategorisierungs-/OpenRouter-Konfiguration.
- Kein eigenes Produkt-KI-Sync-Limit einfuehren.
- KI darf nur normalisierte Positionsdaten, Store, Preis und Menge erhalten.
- Vollstaendige raw_text-Bontexte duerfen fuer Produktzuordnung nicht gesendet werden.
- KI darf bei hoher Sicherheit automatisch zuordnen.
- Unsichere KI-Ergebnisse muessen NEEDS_REVIEW werden.
- KI-Versuche muessen nachvollziehbar sein, aber keine vollstaendigen Prompts/Rohantworten standardmaessig speichern.
- Tests ausschliesslich mit Mock/Testdouble.

API/DTO-Anforderungen:
- DTOs, OpenAPI und Frontend-Typen konsistent halten.
- ReceiptItemDto um Produktfamilie, Produktvariante, assignmentSource, assignmentStatus, confidence und ggf. computedUnitPrice erweitern.
- Backend-Endpunkte fuer Produktfamilien, Varianten, Regeln, Regelvorschau und Produktzuordnungslauf bereitstellen.
- Alle Endpunkte ausser GET /api/health bleiben Bearer-Token-geschuetzt.

Backup/Restore/Datenwartung:
- Backup/Restore muss Produktfamilien, Varianten, Regeln, Zuordnungen, Reviewstatus und Preis-Ausschluesse enthalten.
- Bestehender Reset importierter Bon-Daten behaelt Produktstammdaten und Produktregeln.
- Produktzuordnungen an geloeschten Bon-Positionen verschwinden mit den importierten Bon-Daten.
- Separate Produktdaten-Reset-Aktion vorbereiten oder backendseitig implementieren, aber nur mit deutlicher Bestaetigung.
- Reset darf Kategorien, Kategorisierungsregeln, Settings, Backups und Flyway-Historie nicht versehentlich loeschen.

Tests:
- Migration-/Repository-Tests fuer product_family, product_variant, product_rule und receipt_item-Erweiterungen.
- Service-Tests fuer:
  - Regelmatching
  - store-spezifische Regel uebersteuert globale Regel
  - klare Historie
  - KI-Mock erfolgreich
  - KI-Mock unsicher -> NEEDS_REVIEW
  - NO_PRODUCT fuer reine Rabatt-/Coupon-/Zahlungszeilen
  - unterschiedliche Groessen/Einheiten werden nicht automatisch gemergt
  - default_category_id fuellt nur leere Kategorien
  - bestehende/manuelle Kategorien bleiben erhalten
- Tests fuer automatische Zuordnung nach Sync/Reparse oder an den relevanten Service-Grenzen.
- Tests fuer Backup/Restore und Reset-Semantik.
- API-Contract-Tests fuer neue/erweiterte DTOs und Endpunkte.

Pruefkommandos:
- cd backend && mvn verify
- cd frontend && npm run build
- docker compose config
- git diff --check

Wenn moeglich:
- Einen lokalen Zuordnungslauf mit Testdaten oder Mockdaten manuell pruefen.

Stoppe nach Phase 15a. Implementiere noch keine vollstaendige Produkt-Review-UI und keine Produktpreisvergleichsseiten aus Phase 15b/15c.

Am Ende bitte zusammenfassen:
- neues Datenmodell und Migrationen
- neue/angepasste Endpunkte und DTOs
- Produktzuordnungslogik
- KI-Mock-Verhalten
- Backup/Restore/Reset-Verhalten
- getestete Faelle
- ausgefuehrte Pruefkommandos
- bekannte Restrisiken
- offene Punkte fuer Phase 15b
```
