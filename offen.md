# Offene Punkte - beantwortet und eingeordnet

Stand: 2026-06-04

Diese Punkte sind in `ebon-specification.md` und den Phasen-Prompts 9, 10 und 12 eingearbeitet. Wenn Phase 9 bereits umgesetzt wurde, gelten die Phase-9-Punkte als kleine Nacharbeit vor oder während Phase 10.

| Punkt | Antwort / neue Anforderung | Zielphase |
|---|---|---|
| Paperless-ID anklickbar machen | Die Paperless-Dokument-ID soll als Link angezeigt werden, wenn `paperlessDocumentUrl` vorhanden ist. Der Link wird serverseitig oder aus einer konfigurierbaren Paperless-Web-URL/Dokument-URL-Vorlage erzeugt und enthält keine Secrets. | 9/10 |
| Klick auf „Ohne Kategorie" im Dashboard | Öffnet Bon-/Suchliste mit Filter `uncategorizedOnly=true`. Gemeint sind nur Positionen mit `category_id = NULL` und `category_source = NULL`, keine persistierte Pseudo-Kategorie. | 10 |
| Re-Parse aller Bons in Settings | Bereich „Datenwartung" in Einstellungen. Standard: `overwriteManualEdits=false`, damit manuelle Änderungen nicht still überschrieben werden. | 10 |
| Alle importierten Bons/Positionen löschen | Destruktiver Reset für komplettes Neueinlesen aus Paperless-NGX. Löscht importierte Bon-Daten und zugehörige Detaildaten, behält Kategorien, Regeln, Einstellungen und Flyway-Migrationen. Nur mit deutlicher Bestätigung, z.B. `DELETE_IMPORTED_RECEIPTS`. | 10 |
| Dashboard-Summe: aktueller Monat, Vormonat, Jahr | Dashboard zeigt alle drei Werte. Der Monatsvergleich bleibt als Delta/Trend möglich. | 10 |
| Zeitraumwahl im Kategorie-Tortendiagramm | Unterstützt aktueller Monat, letztes Quartal, letztes Jahr und benutzerdefiniert von/bis. | 10 |
| Zeitraumwahl im Bonusbereich | Unterstützt dieselben Zeitraumfilter wie das Kategorie-Diagramm. | 10 |
| Bedeutung „Letzte Bons" | Das ist eine Schnellnavigation zu den zuletzt datierten, nicht gelöschten Bons, nicht die vollständige Historie. Sortierung: `receipt_date DESC`, `receipt_time DESC`, `imported_at DESC`. | 10 |
| Bedeutung „Bonus" oben rechts | Gemeint ist neu im gewählten Zeitraum gesammeltes Bonusguthaben/Punkte, nicht der aktuelle Kontostand eines Bonusprogramms. | 10 |
| Header zeigt bei Bon-Detail „Dashboard" | Navigationspunkt/Breadcrumb muss zur Route passen: Bon-Liste und Bon-Detail zeigen „Bons". | 9/12 |
| Bon-Liste: Import-Zeit abgeschnitten | Import-Datum zeigt Datum und Uhrzeit lesbar; bei schmaler Spalte steht die Uhrzeit in einer zweiten Zeile. | 9 |
| eBon-Web-Logo | Provisorisches Logo durch stimmige Wort-/Bildmarke ersetzen. Keine Secrets, keine echten Belegdaten im Asset. | 12 |
| Speichern nach Positionseditierung | Speichern/Abbrechen bleibt beim Scrollen sichtbar, z.B. über Sticky Action-Bar. | 9 |
