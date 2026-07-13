# Händlerspezifische Kategorisierungsregeln

## Ziel

Kategorisierungsregeln dürfen optional auf einen Händler eingeschränkt werden. Damit kann beispielsweise `BEDIENUNGSTHEKE` nur bei REWE als `Fleisch und Wurst` kategorisiert werden, ohne denselben allgemeinen Text anderer Händler zu beeinflussen.

Zusätzlich werden die aktuell offenen Positionen anhand bestätigter oder eindeutig ableitbarer Zuordnungen kategorisiert. Nicht belastbar bestimmbare Positionen wie `ORIGINAL` bleiben ohne Kategorie.

## Datenmodell

`categorization_rule` erhält die nullable Spalte `store_name`. Ein leerer Wert bedeutet, dass die Regel unabhängig vom Händler gilt. Vorhandene Regeln behalten dadurch unverändert ihre bisherige Bedeutung.

`store_name` ist eine zusätzliche AND-Bedingung zur bestehenden Regel aus `match_field`, `match_type` und `match_value`. Der Händlervergleich ist nach Trimmen case-insensitive und exakt. Eine händlerspezifische Regel trifft nicht, wenn der Bon keinen Händlernamen besitzt.

## Backend und API

Das Persistenzmodell, die Create-/Update-/Preview-Requests und das Response-DTO erhalten `storeName` als optionales Feld. Leere Eingaben werden als `NULL` gespeichert. Die OpenAPI-Beschreibung dokumentiert die AND-Semantik.

Der zentrale Matcher prüft weiterhin die bestehende Beschreibung- oder Händlerbedingung. Bei gesetztem `storeName` muss zusätzlich der Händler des Bons passen. Vorschau, Bulk-Apply und automatische Kategorisierung verwenden denselben Matcher, damit ihre Ergebnisse übereinstimmen.

Regeln mit `matchField = STORE_NAME` dürfen keine zusätzliche Händlerbedingung tragen. Die UI bietet sie in diesem Fall nicht an und das Backend lehnt einen gesetzten Wert ab, damit keine widersprüchlichen Händlerbedingungen entstehen.

## Frontend

Die Regelverwaltung in den Einstellungen erhält für Beschreibungsregeln das optionale Feld „Nur bei Händler“. Das Feld ist beim Anlegen und Bearbeiten verfügbar, wird in der Regelliste angezeigt und fließt in die Vorschau ein. Beim Wechsel auf `STORE_NAME` wird die zusätzliche Einschränkung geleert und ausgeblendet.

Frontend-Typen, API-Aufrufe und Mock-Daten bleiben mit Regeln ohne Händlerbedingung rückwärtskompatibel.

## Bestandsregeln und Datenkorrektur

Eine Flyway-Migration legt die bestätigten und eindeutig ableitbaren Beschreibungsregeln idempotent an. `BEDIENUNGSTHEKE` wird mit `store_name = REWE` auf `Fleisch und Wurst` eingeschränkt. Die Migration wendet ihre Regeln rückwirkend ausschließlich auf Positionen an, für die gilt:

- `category_id IS NULL`
- `category_source IS NULL`
- `is_manually_edited = FALSE`
- der zugehörige Bon ist nicht soft-gelöscht
- Beschreibung und gegebenenfalls Händlerbedingung treffen

Manuelle oder bereits regel-/KI-kategorisierte Werte werden nicht überschrieben. `ORIGINAL` erhält weder eine Regel noch eine Kategorie.

## Validierung und Tests

Backend-Tests belegen:

- bestehende globale Regeln funktionieren unverändert;
- passende Beschreibung plus passender Händler trifft;
- ein anderer oder fehlender Händler trifft nicht;
- Händlervergleich ignoriert Groß-/Kleinschreibung und Rand-Leerzeichen;
- Request-Validierung verhindert eine zusätzliche Händlerbedingung bei `matchField = STORE_NAME`;
- Vorschau, Bulk-Apply und automatische Kategorisierung verwenden dieselbe Semantik;
- die Migration ist idempotent und schützt manuell bearbeitete sowie soft-gelöschte Daten.

Frontend-Tests belegen Anlegen, Bearbeiten, Anzeigen und Vorschau einer händlerspezifischen Regel sowie das Ausblenden und Leeren des Felds bei `STORE_NAME`.

Die Verifikation umfasst mindestens `mvn verify`, `npm run build` und eine Abfrage der verbleibenden offenen Positionen in der lokalen Compose-Datenbank nach Anwendung der Migration.
