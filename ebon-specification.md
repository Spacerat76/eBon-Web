# eBon Expense Tracker – Software-Spezifikation

**Version:** 1.2
**Datum:** 2026-06-01
**Status:** Final Draft

---

## Inhaltsverzeichnis

1. [Projektziel & Kontext](#1-projektziel--kontext)
2. [Systemarchitektur](#2-systemarchitektur)
3. [Technologie-Stack](#3-technologie-stack)
4. [Datenmodell](#4-datenmodell)
5. [Externe Schnittstellen](#5-externe-schnittstellen)
6. [Fachliche Anforderungen (Features)](#6-fachliche-anforderungen-features)
7. [Use Cases](#7-use-cases)
8. [API-Spezifikation (Backend)](#8-api-spezifikation-backend)
9. [UI-Spezifikation (Frontend)](#9-ui-spezifikation-frontend)
10. [Konfiguration & Umgebungsvariablen](#10-konfiguration--umgebungsvariablen)
11. [Docker-Deployment](#11-docker-deployment)
12. [Backup & Restore](#12-backup--restore)
13. [Fehlerbehandlung & Logging](#13-fehlerbehandlung--logging)
14. [Nicht-funktionale Anforderungen](#14-nicht-funktionale-anforderungen)
15. [Offene Punkte / Abgrenzung](#15-offene-punkte--abgrenzung)
16. [KI-Agenten-Umsetzung](#16-ki-agenten-umsetzung)
17. [Akzeptanzkriterien & Test-Fixtures](#17-akzeptanzkriterien--test-fixtures)

---

## 1. Projektziel & Kontext

### 1.1 Ziel

Die Applikation **eBon Expense Tracker** liest elektronische Kassenbons (eBons) aus dem Dokumentenmanagementsystem **Paperless-NGX**, parst deren Inhalte, kategorisiert die einzelnen Kaufpositionen und stellt Auswertungen sowie Suchfunktionen über die Ausgaben bereit.

### 1.2 Kontext

- eBons liegen als Dokumente in Paperless-NGX vor, gekennzeichnet durch einen konfigurierbaren Tag (Umgebungsvariable `PAPERLESS_EBON_TAG`, Standard `eBON`).
- Die Dokumente enthalten maschinenlesbaren Text (kein Scan/OCR erforderlich – der Text ist bereits extrahiert von Paperless-NGX).
- Der Nutzer interagiert ausschließlich über eine Web-UI.
- Die gesamte Applikation läuft in einem einzigen Docker-Compose-Setup.

### 1.3 Nutzer

Es gibt genau **einen Nutzer** (Single-User-Anwendung). Eine Benutzerverwaltung ist nicht vorgesehen. Der Zugriff ist durch ein einzelnes konfigurierbares API-Token zu sichern. Die Anwendung verwendet ausschließlich Bearer-Token-Authentifizierung über `Authorization: Bearer <APP_API_TOKEN>`. HTTP Basic Auth ist nicht Bestandteil der Anwendung.

---

## 2. Systemarchitektur

```
┌─────────────────────────────────────────────────────────┐
│                     Docker-Compose                      │
│                                                         │
│  ┌──────────────┐    ┌──────────────────────────────┐  │
│  │   React-SPA  │───▶│     Spring Boot Backend       │  │
│  │  (nginx)     │    │     (REST API, Port 8080)     │  │
│  └──────────────┘    │                              │  │
│       Port 80        │  ┌────────────────────────┐  │  │
│                      │  │  Scheduled Sync Job     │  │  │
│                      │  │  (Paperless-NGX Poller) │  │  │
│                      │  └────────────────────────┘  │  │
│                      │                              │  │
│                      │  ┌────────────────────────┐  │  │
│                      │  │  Categorizer Service    │  │  │
│                      │  │  (Rules + AI Fallback)  │  │  │
│                      │  └────────────────────────┘  │  │
│                      └──────────────┬───────────────┘  │
│                                     │                   │
│                      ┌──────────────▼───────────────┐  │
│                      │     PostgreSQL Datenbank      │  │
│                      │         (Port 5432)           │  │
│                      └──────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
         │                                  │
         ▼                                  ▼
  Paperless-NGX                     OpenRouter.ai
  (externes System)                 (externes System)
```

Die React-SPA wird von nginx als statische Dateien ausgeliefert. nginx proxied `/api/*` an das Spring Boot Backend.

---

## 3. Technologie-Stack

| Komponente | Technologie | Zielversion |
|---|---|---|
| Backend Framework | Spring Boot | 4.0.x |
| Sprache Backend | Java | 25 (LTS) |
| Build-Tool | Maven | 3.9.16 |
| Persistenz | Spring Data JPA + Hibernate | via Spring Boot |
| Datenbank | PostgreSQL | 18.x |
| DB-Migration | Flyway | via Spring Boot |
| HTTP-Client (Backend) | Spring RestClient | via Spring Boot |
| Frontend Framework | React / React DOM | 19.2.7 |
| Frontend Build | Vite + `@vitejs/plugin-react` | 8.0.16 / 6.0.2 |
| Frontend Sprache | TypeScript | 6.0.3 |
| UI-Komponenten | shadcn/ui + Tailwind CSS | aktuell / 4.3.0 |
| HTTP-Client (Frontend) | Axios (oder native `fetch`) | v1.7+ / aktuell |
| Icons | lucide-react | 1.17.0 |
| Charts | Recharts | 3.8.1 |
| Webserver (Frontend) | nginx | alpine |
| Container | Docker + Docker Compose | 26+ / 2.27+ |
| Entwicklungsumgebung | Devcontainer / VS Code Remote Containers | aktuell |

**Versionsregel:** Die genannten Versionen sind Zielversionen. Falls ein KI-Agent oder Build-System eine Zielversion noch nicht zuverlässig auflösen kann, muss der Agent den Konflikt dokumentieren und darf nur nach expliziter Entscheidung auf folgende stabile Fallbacks ausweichen: Spring Boot 3.5.x, Java 21 LTS, PostgreSQL 17.x, Vite 7.x. Ohne dokumentierte Abweichung gelten die Zielversionen als verpflichtend.

### 3.1 Entwicklungsumgebung mit Devcontainer

Die Anwendung wird in einer reproduzierbaren Devcontainer-Umgebung entwickelt. Das Repository enthält mindestens:

```
.devcontainer/
├── devcontainer.json
├── Dockerfile
└── docker-compose.devcontainer.yml
```

Der Devcontainer stellt bereit:

- Java 25 JDK
- Maven 3.9.16
- Node.js 24.16.0 LTS oder neuer
- Docker CLI
- PostgreSQL Client (`psql`)
- Git, curl, jq
- VS-Code-Extensions für Java, Spring Boot, Maven, ESLint, Prettier, Docker und Dev Containers

Der Devcontainer startet eine lokale PostgreSQL-Entwicklungsdatenbank über `docker-compose.devcontainer.yml`. Backend und Frontend werden im Devcontainer entwickelt, aber nicht zwingend automatisch gestartet.

Standard-Kommandos im Devcontainer:

```bash
# Backend
cd backend
mvn verify
mvn spring-boot:run

# Frontend
cd frontend
npm ci
npm run dev

# Gesamtsystem
docker compose up --build
```

Die Datei `.env.example` dokumentiert alle erforderlichen Umgebungsvariablen mit sicheren Beispielwerten. Echte Tokens und Passwörter dürfen nicht in das Repository eingecheckt werden.

---

## 4. Datenmodell

### 4.1 Entitäten

#### 4.1.1 `receipt` (Kassenbon)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `paperless_document_id` | INTEGER | UNIQUE, NOT NULL | Dokument-ID aus Paperless-NGX |
| `imported_at` | TIMESTAMPTZ | NOT NULL | Zeitpunkt des Imports |
| `receipt_date` | DATE | NULL | Datum des Bons (geparst) |
| `receipt_time` | TIME | NULL | Uhrzeit des Bons (geparst) |
| `store_name` | VARCHAR(255) | NULL | Geschäftsname (geparst) |
| `store_branch` | VARCHAR(255) | NULL | Filiale / Adresse (geparst) |
| `total_amount` | NUMERIC(10,2) | NULL | Gesamtbetrag laut Bon |
| `currency` | CHAR(3) | NOT NULL, DEFAULT 'EUR' | Währung (ISO 4217) |
| `raw_text` | TEXT | NOT NULL | Roher Text des Dokuments aus Paperless-NGX |
| `bonus_balance` | NUMERIC(10,2) | NULL | In diesem Einkauf neu gesammeltes Bonusguthaben, nicht der aktuelle Kontostand (geparst) |
| `bonus_points` | NUMERIC(10,2) | NULL | In diesem Einkauf neu gesammelte Payback-Punkte o.Ä. aus dem Bon (geparst) |
| `bonus_type` | VARCHAR(64) | NULL | Art des Bonusprogramms (z.B. „Payback", „DeutschlandCard", „Bonusclub") |
| `parse_status` | VARCHAR(32) | NOT NULL | Enum: `PENDING`, `PARSED`, `PARSE_ERROR`, `MANUALLY_EDITED` |
| `parse_source` | VARCHAR(32) | NULL | Quelle des aktuellen Parsergebnisses: `RULE`, `AI`, optional später `MANUAL_CORRECTED`; NULL solange kein verwertbares Parsergebnis vorliegt |
| `parse_error_message` | TEXT | NULL | Fehlermeldung bei `PARSE_ERROR` |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Letztes Update (automatisch) |
| `deleted_at` | TIMESTAMPTZ | NULL | Soft-Delete-Zeitpunkt, z.B. bei `TAG_REMOVED` |
| `delete_reason` | VARCHAR(32) | NULL | Enum: `USER_DELETED`, `TAG_REMOVED`, falls `deleted_at` gesetzt ist |

#### 4.1.2 `receipt_item` (Bon-Position)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `receipt_id` | BIGINT | FK → receipt.id, NOT NULL | Zugehöriger Bon |
| `position_index` | INTEGER | NOT NULL | Reihenfolge im Bon (0-basiert) |
| `description` | VARCHAR(512) | NOT NULL | Artikelbezeichnung (geparst) |
| `quantity` | NUMERIC(10,3) | NULL | Menge (z.B. 1,5 kg) |
| `unit` | VARCHAR(32) | NULL | Einheit (z.B. „kg", „Stk") |
| `unit_price` | NUMERIC(10,2) | NULL | Einzelpreis |
| `total_price` | NUMERIC(10,2) | NOT NULL | Gesamtpreis der Position |
| `discount_amount` | NUMERIC(10,2) | NULL | Rabattbetrag (sofern erkennbar) |
| `product_family_id` | BIGINT | FK → product_family.id, NULL | Zugeordnete Produktfamilie für Phase-15-Preisvergleiche |
| `product_variant_id` | BIGINT | FK → product_variant.id, NULL | Zugeordnete Produktvariante; NULL, wenn nur Familie bekannt oder kein Produkt |
| `product_assignment_source` | VARCHAR(32) | NULL | Enum: `RULE`, `AI`, `MANUAL`, `HISTORY`; nur gesetzt, wenn Produktzuordnung gesetzt ist |
| `product_assignment_status` | VARCHAR(32) | NULL | Enum: `CONFIRMED`, `AUTO_ASSIGNED`, `NEEDS_REVIEW`, `REJECTED`, `NO_PRODUCT`; NULL vor Phase-15-Zuordnung |
| `product_assignment_confidence` | NUMERIC(4,3) | NULL | Konfidenz der automatischen Produktzuordnung |
| `product_assignment_updated_at` | TIMESTAMPTZ | NULL | Zeitpunkt der letzten Produktzuordnungsänderung |
| `exclude_from_product_price_comparison` | BOOLEAN | NOT NULL, DEFAULT FALSE | Einzelne Preisbeobachtung aus Produktpreisvergleichen ausschließen |
| `product_price_exclusion_reason` | TEXT | NULL | Begründung für Ausschluss aus Produktpreisvergleichen |
| `category_id` | BIGINT | FK → category.id, NULL | Zugewiesene Kategorie |
| `category_source` | VARCHAR(32) | NULL | Enum: `RULE`, `AI`, `MANUAL`; nur gesetzt, wenn `category_id` gesetzt ist |
| `is_manually_edited` | BOOLEAN | NOT NULL, DEFAULT FALSE | Wurde die Position manuell bearbeitet |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Letztes Update |

#### 4.1.3 `category` (Warengruppe/Kategorie)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `name` | VARCHAR(128) | UNIQUE, NOT NULL | Kategoriename (z.B. „Lebensmittel", „Getränke") |
| `color_hex` | CHAR(7) | NULL | Farbe für UI-Darstellung (z.B. `#FF5733`) |
| `icon` | VARCHAR(64) | NULL | Icon-Bezeichner für UI |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | Aktiv-Status; Kategorien werden standardmäßig deaktiviert statt physisch gelöscht |
| `sort_order` | INTEGER | NOT NULL, DEFAULT 0 | Sortierreihenfolge in der UI |

#### 4.1.3a `product_family` (Produktfamilie)

Eine Produktfamilie beschreibt ein fachlich vergleichbares Produkt auf Mengenebene, z.B. `Coca Cola Zero`. Marke wird nicht separat gespeichert, sondern bleibt Teil des Namens.

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `name` | VARCHAR(255) | UNIQUE, NOT NULL | Anzeigename der Produktfamilie |
| `default_category_id` | BIGINT | FK → category.id, NULL | Optionale Standard-Kategorie; darf nur leere Kategorien füllen |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | Aktiv-Status |
| `created_at` | TIMESTAMPTZ | NOT NULL | Erstellungszeitpunkt |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Letztes Update |

`default_category_id` darf niemals bestehende oder manuell gesetzte Kategorien an `receipt_item` überschreiben.

#### 4.1.3b `product_variant` (Produktvariante)

Eine Produktvariante beschreibt das konkrete Gebinde oder die konkrete Kaufvariante, z.B. `Coca Cola Zero 0,33l Flasche`. Unterschiedliche Größen, Einheiten oder Packungsstrukturen sind unterschiedliche Varianten.

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `product_family_id` | BIGINT | FK → product_family.id, NOT NULL | Zugehörige Produktfamilie |
| `name` | VARCHAR(255) | NOT NULL | Anzeigename der Variante |
| `unit_quantity` | NUMERIC(12,4) | NULL | Einzelgebinde-Menge, z.B. `0.3300` |
| `unit` | VARCHAR(32) | NULL | Einheit, z.B. `l`, `ml`, `kg`, `g`, `piece` |
| `package_quantity` | NUMERIC(10,3) | NULL | Packungsanzahl, z.B. `6` bei `6x0,33l` |
| `package_description` | VARCHAR(128) | NULL | Verpackung/Gebinde, z.B. `Flasche`, `Dose`, `Packung` |
| `total_quantity` | NUMERIC(12,4) | NULL | Gesamtmenge für Preisvergleich, z.B. `1.9800` bei `6x0,33l` |
| `gtin` | VARCHAR(32) | NULL | Optional vorbereitet; keine externe Produktdatenbank-Abfrage in Phase 15 |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | Aktiv-Status |
| `created_at` | TIMESTAMPTZ | NOT NULL | Erstellungszeitpunkt |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Letztes Update |

Bekannte Einheiten werden normalisiert: `ml/l` nach Liter, `g/kg` nach Kilogramm und Stückzahlen nach `€/Stück`. Unbekannte Einheiten dürfen gespeichert werden, sind aber nicht automatisch mit bekannten Einheiten vergleichbar.

#### 4.1.4 `categorization_rule` (Kategorisierungsregel)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `category_id` | BIGINT | FK → category.id, NOT NULL | Ziel-Kategorie |
| `match_field` | VARCHAR(32) | NOT NULL | Enum: `DESCRIPTION`, `STORE_NAME` |
| `match_type` | VARCHAR(32) | NOT NULL | Enum: `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `EXACT`, `REGEX` |
| `match_value` | VARCHAR(512) | NOT NULL | Suchwert (case-insensitive) |
| `priority` | INTEGER | NOT NULL, DEFAULT 100 | Niedrigere Zahl = höhere Priorität |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | Regel aktiv/inaktiv |
| `created_at` | TIMESTAMPTZ | NOT NULL | Erstellungszeitpunkt |

Regeln werden in absteigender Priorität (niedrigster `priority`-Wert zuerst) geprüft. Die erste passende Regel gewinnt.

#### 4.1.4a `product_rule` (Produktzuordnungsregel)

Produktregeln ordnen Bon-Positionstexte einer Produktfamilie und optional einer Produktvariante zu. Sie bleiben strikt von `categorization_rule` getrennt.

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `product_family_id` | BIGINT | FK → product_family.id, NOT NULL | Ziel-Produktfamilie |
| `product_variant_id` | BIGINT | FK → product_variant.id, NULL | Ziel-Variante; NULL bedeutet nur Familienzuordnung |
| `store_name` | VARCHAR(255) | NULL | Optionaler Store-Kontext; NULL = globale Regel |
| `match_type` | VARCHAR(32) | NOT NULL | Enum: `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `EXACT`, `REGEX` |
| `match_value` | VARCHAR(512) | NOT NULL | Suchwert gegen `receipt_item.description` (case-insensitive) |
| `priority` | INTEGER | NOT NULL, DEFAULT 100 | Niedrigere Zahl = höhere Priorität; store-spezifische Regeln dürfen globale Regeln übersteuern |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | Regel aktiv/inaktiv |
| `created_at` | TIMESTAMPTZ | NOT NULL | Erstellungszeitpunkt |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Letztes Update |

Beim Speichern oder Ändern einer Regel wird eine Vorschau angeboten. Rückwirkende Anwendung auf bestehende Positionen erfolgt nur nach expliziter Bestätigung.

#### 4.1.5 `ai_categorization_log` (KI-Kategorisierungslog)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `receipt_item_id` | BIGINT | FK → receipt_item.id, NOT NULL | Zugehörige Position |
| `prompt_sent` | TEXT | NOT NULL | Gesendeter Prompt |
| `response_received` | TEXT | NOT NULL | Rohantwort der KI |
| `suggested_category_id` | BIGINT | FK → category.id, NULL | Von der KI vorgeschlagene, bekannte Kategorie; NULL bei unbekannter Kategorie oder invalider Antwort |
| `suggested_category_name` | VARCHAR(128) | NULL | Von der KI gelieferter Kategoriename, auch wenn er keiner bekannten Kategorie zugeordnet werden konnte |
| `assigned_category_id` | BIGINT | FK → category.id, NULL | Tatsächlich automatisch übernommene Kategorie; NULL, wenn Vorschlag nicht akzeptiert wurde |
| `ai_confidence` | NUMERIC(4,3) | NULL | Konfidenzwert (0.000–1.000), sofern geliefert |
| `rejection_reason` | VARCHAR(32) | NULL | Grund, warum kein `assigned_category_id` gesetzt wurde; z.B. `LOW_CONFIDENCE`, `UNKNOWN_CATEGORY`, `INVALID_RESPONSE` |
| `model_used` | VARCHAR(128) | NOT NULL | Verwendetes KI-Modell |
| `created_at` | TIMESTAMPTZ | NOT NULL | Zeitpunkt des KI-Calls |

#### 4.1.5a `ai_parsing_log` (KI-Parsing-Fallback-Log)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `receipt_id` | BIGINT | FK → receipt.id, NULL | Zugehöriger Bon; NULL nur für technische Verbindungstests ohne Bon-Daten |
| `trigger` | VARCHAR(32) | NOT NULL | Enum: `SYNC_AUTO`, `MANUAL_REPARSE`, `MANUAL_REPARSE_FORCE_FULL_TEXT`, `BULK_REPARSE`, `SETTINGS_TEST` |
| `status` | VARCHAR(32) | NOT NULL | Enum: `SUCCESS`, `FAILED`, `SKIPPED_LIMIT`, `INVALID_RESPONSE`, `LOW_CONFIDENCE`, `DISABLED`, `NO_API_KEY` |
| `model_used` | VARCHAR(128) | NULL | Verwendetes OpenRouter-Modell; NULL, wenn kein Call ausgeführt wurde |
| `started_at` | TIMESTAMPTZ | NOT NULL | Startzeitpunkt |
| `finished_at` | TIMESTAMPTZ | NULL | Endzeitpunkt |
| `duration_ms` | INTEGER | NULL | Dauer des KI-Calls oder der lokalen Fallback-Prüfung |
| `prompt_tokens` | INTEGER | NULL | Von OpenRouter gemeldete Prompt-Tokens, sofern vorhanden |
| `completion_tokens` | INTEGER | NULL | Von OpenRouter gemeldete Completion-Tokens, sofern vorhanden |
| `total_tokens` | INTEGER | NULL | Von OpenRouter gemeldete Gesamt-Tokens, sofern vorhanden |
| `parse_error_before` | TEXT | NULL | Fehlergrund des Regelparsers vor dem KI-Fallback |
| `failure_reason` | TEXT | NULL | Nutzer-/Admin-tauglicher Grund, warum keine Übernahme erfolgte |
| `overall_confidence` | NUMERIC(4,3) | NULL | Gesamtkonfidenz des KI-Parses |
| `field_confidence_json` | JSONB | NULL | Optionale Feld-Konfidenzen der KI, z.B. für Datum, Store, Total, Items |
| `warnings_json` | JSONB | NULL | Optionale KI-Warnungen, ohne Roh-Prompt oder vollständige Rohantwort |
| `prompt_snippet` | TEXT | NULL | Optionales gekürztes/maskiertes Prompt-Snippet nur bei lokaler Debug-Konfiguration |
| `response_snippet` | TEXT | NULL | Optionales gekürztes/maskiertes Antwort-Snippet nur bei lokaler Debug-Konfiguration |
| `created_at` | TIMESTAMPTZ | NOT NULL | Anlagezeitpunkt |

Standardmäßig werden keine vollständigen Prompts und keine vollständigen KI-Rohantworten gespeichert. Snippets sind nur für lokale Entwicklung zulässig, müssen gekürzt und maskiert sein und dürfen keine Secrets enthalten.

#### 4.1.5b `parse_rule_suggestion` (KI-vorgeschlagene Parser-Regel)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `ai_parsing_log_id` | BIGINT | FK → ai_parsing_log.id, NOT NULL | Ursprung des Vorschlags |
| `receipt_id` | BIGINT | FK → receipt.id, NULL | Betroffener Bon |
| `store_name` | VARCHAR(255) | NULL | Store-Kontext für die vorgeschlagene Regel |
| `rule_type` | VARCHAR(32) | NOT NULL | Enum wie `parse_rule.rule_type` |
| `match_regex` | VARCHAR(1024) | NOT NULL | Vorgeschlagene Regex |
| `extract_group` | VARCHAR(64) | NULL | Vorgeschlagene Capture-Group(s) |
| `confidence` | NUMERIC(4,3) | NULL | Konfidenz des Vorschlags |
| `trigger` | VARCHAR(32) | NOT NULL | Auslöser, der zum Vorschlag geführt hat |
| `problem_description` | TEXT | NOT NULL | Welches Parserproblem der Vorschlag adressiert |
| `solution_rationale` | TEXT | NOT NULL | Warum die Regel das Problem lösen soll |
| `validation_status` | VARCHAR(32) | NOT NULL | Enum: `VALID`, `INVALID_REGEX`, `NO_MATCH`, `WRONG_EXTRACTION`, `COLLISION_RISK` |
| `validation_message` | TEXT | NULL | Technische Validierungsdetails |
| `status` | VARCHAR(32) | NOT NULL | Enum: `OPEN`, `ACCEPTED`, `REJECTED` |
| `rejection_reason` | TEXT | NULL | Begründung bei `REJECTED` |
| `accepted_parse_rule_id` | BIGINT | FK → parse_rule.id, NULL | Erzeugte aktive Parser-Regel nach Annahme |
| `created_at` | TIMESTAMPTZ | NOT NULL | Anlagezeitpunkt |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Letzte Änderung |

Vorschläge werden nie automatisch aktiv. Sie müssen in der UI geprüft und können vor Annahme bearbeitet werden. Akzeptierte Vorschläge werden sofort als aktive `parse_rule` mit `source = AI_ADAPTED` übernommen und bleiben zusätzlich als Vorschlag auditierbar.

#### 4.1.6 `app_settings` (Anwendungskonfiguration)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `key` | VARCHAR(128) | PK | Einstellungsschlüssel |
| `value` | TEXT | NOT NULL | Einstellungswert |
| `description` | TEXT | NULL | Beschreibung der Einstellung |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Letztes Update |

Initiale Schlüssel: `sync_interval_minutes`, `ai_model`, `ai_max_tokens`, `ai_temperature`, `ai_categorization_min_confidence`, `ai_parsing_fallback_enabled`, `ai_parsing_model`, `ai_parsing_max_tokens`, `ai_parsing_temperature`, `ai_parsing_min_confidence`, `ai_parsing_sync_call_limit`, `ai_parsing_text_mode`, `ai_parsing_store_debug_snippets`, `product_history_min_confirmed_matches`, `product_history_min_variant_share`.

#### 4.1.7 `parse_rule` (Parsing-Regel, automatisch gelernt)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `store_name` | VARCHAR(255) | NULL | Geschäftsname (NULL = generische Regel) |
| `rule_type` | VARCHAR(32) | NOT NULL | Enum: `DATE_PATTERN`, `STORE_PATTERN`, `ITEM_PATTERN`, `TOTAL_PATTERN`, `BONUS_PATTERN` |
| `match_regex` | VARCHAR(1024) | NOT NULL | Regex-Muster für das Parsing |
| `extract_group` | VARCHAR(64) | NULL | Benannte Capture-Group(s) für die Extraktion |
| `confidence` | NUMERIC(4,3) | NULL | Konfidenz (0.000–1.000) der gelernten Regel |
| `hit_count` | INTEGER | NOT NULL, DEFAULT 0 | Wie oft die Regel erfolgreich angewendet wurde |
| `last_used_at` | TIMESTAMPTZ | NULL | Letzte Verwendung |
| `source` | VARCHAR(32) | NOT NULL | Enum: `MANUAL`, `AI_ADAPTED` |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | Regel aktiv/inaktiv |
| `created_at` | TIMESTAMPTZ | NOT NULL | Erstellungszeitpunkt |

#### 4.1.8 `sync_log` (Sync-Protokoll)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `started_at` | TIMESTAMPTZ | NOT NULL | Sync-Startzeit |
| `finished_at` | TIMESTAMPTZ | NULL | Sync-Endzeit |
| `status` | VARCHAR(32) | NOT NULL | Enum: `RUNNING`, `SUCCESS`, `FAILED` |
| `new_documents_count` | INTEGER | NOT NULL, DEFAULT 0 | Anzahl neu importierter Dokumente |
| `removed_documents_count` | INTEGER | NOT NULL, DEFAULT 0 | Anzahl entfernter Dokumente (TAG_REMOVED) |
| `error_message` | TEXT | NULL | Fehlermeldung bei `FAILED` |

#### 4.1.9 `sync_log_entry` (Einzelner Sync-Eintrag)

| Spalte | Typ | Constraints | Beschreibung |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Interne ID |
| `sync_log_id` | BIGINT | FK → sync_log.id, NOT NULL | Zugehöriger Sync-Lauf |
| `paperless_document_id` | INTEGER | NULL | Paperless-Dokument-ID |
| `action` | VARCHAR(32) | NOT NULL | Enum: `IMPORTED`, `TAG_REMOVED`, `SKIPPED` |
| `receipt_id` | BIGINT | NULL | Referenz zum receipt (vor Entfernung) |
| `details` | TEXT | NULL | Zusätzliche Informationen |
| `created_at` | TIMESTAMPTZ | NOT NULL | Zeitpunkt des Eintrags |

### 4.2 Indizes

- `receipt.paperless_document_id` – UNIQUE INDEX
- `receipt.receipt_date` – INDEX
- `receipt.store_name` – INDEX (für Suche)
- `receipt.bonus_type` – INDEX
- `receipt.parse_source` – INDEX
- `receipt_item.receipt_id` – INDEX
- `receipt_item.description` – GIN-Index für Volltextsuche (PostgreSQL `tsvector`)
- `receipt_item.category_id` – INDEX
- `receipt_item.product_family_id` – INDEX
- `receipt_item.product_variant_id` – INDEX
- `receipt_item.product_assignment_status` – INDEX
- `receipt_item.exclude_from_product_price_comparison` – INDEX
- `product_family.name` – UNIQUE INDEX
- `product_variant.product_family_id` – INDEX
- `product_variant.gtin` – INDEX (nullable)
- `product_rule.priority` – INDEX
- `product_rule.store_name` – INDEX
- `product_rule.is_active` – INDEX
- `categorization_rule.priority` – INDEX
- `ai_parsing_log.receipt_id` – INDEX
- `ai_parsing_log.trigger` – INDEX
- `ai_parsing_log.status` – INDEX
- `parse_rule_suggestion.status` – INDEX
- `parse_rule_suggestion.receipt_id` – INDEX
- `parse_rule.store_name` – INDEX
- `parse_rule.rule_type` – INDEX
- `parse_rule.is_active` – INDEX
- `sync_log.started_at` – INDEX
- `sync_log_entry.sync_log_id` – INDEX

---

## 5. Externe Schnittstellen

### 5.1 Paperless-NGX REST API

**Basis-URL:** konfigurierbar via `PAPERLESS_BASE_URL`  
**Authentifizierung:** Token-Auth (`Authorization: Token <PAPERLESS_API_TOKEN>`)

| Verwendeter Endpoint | Methode | Beschreibung |
|---|---|---|
| `/api/documents/?tags__name__iexact={TAG}&page_size=100&ordering=-created` | GET | Alle Dokumente mit exakt passendem konfiguriertem Tag abrufen (case-insensitive, paginiert). `{TAG}` = Wert der Umgebungsvariable `PAPERLESS_EBON_TAG`. |
| `/api/documents/{id}/` | GET | Metadaten eines einzelnen Dokuments |
| `/api/documents/{id}/download/` | GET | Download des Dokuments; nicht verwendet |

Die Applikation verwendet ausschließlich den Textinhalt (`content`-Feld) aus dem Dokument-Metadaten-Response. Kein direkter Download von PDFs.

**Paginierung:** Die API gibt `next`-Links zurück. Der Sync-Job folgt allen `next`-Links bis `null`.

**Relevante Felder im Paperless-Dokument-Response:**

```json
{
  "id": 42,
  "title": "Rewe eBon 2025-01-15",
  "created": "2025-01-15T10:23:00Z",
  "content": "<extrahierter Rohtext des Bons>",
  "tags": [1, 5, 12]
}
```

Die Tag-Filterung erfolgt via Query-Parameter `tags__name__iexact={TAG}`, wobei `{TAG}` der Wert aus `PAPERLESS_EBON_TAG` ist. Dieser Paperless-Filter matcht den Tag-Namen exakt und case-insensitive. Die Anwendung verlässt sich auf diesen serverseitigen Filter; eine zusätzliche lokale Tag-Prüfung ist nicht vorgesehen.

### 5.2 OpenRouter.ai API

**Basis-URL:** konfigurierbar via `OPENROUTER_BASE_URL`, Standard `https://openrouter.ai/api/v1`

**Authentifizierung:** Bearer-Token (`Authorization: Bearer <OPENROUTER_API_KEY>`)
**Verwendeter Endpoint:** `POST /chat/completions` (OpenAI-kompatibel)

OpenRouter wird für drei getrennte Workflows verwendet:

- KI-Kategorisierung (F-03)
- KI-Parsing-Fallback (F-02)
- KI-Produktzuordnung (F-19), ohne vollständige Bon-Rohtexte

Für KI-Parsing muss die Anwendung, soweit vom Modell unterstützt, strukturierte Ausgaben über `response_format`/JSON-Schema anfordern. Unabhängig davon validiert das Backend jede Antwort lokal gegen das erwartete Schema. OpenRouter-Verbindungstests dürfen keine echten Bon-Daten senden.

**KI-Kategorisierung und KI-Produktzuordnung:**

- Gesendet werden nur positionsbezogene Daten wie Beschreibung, Store, Preis, Menge und verfügbare Zieloptionen.
- Die Antwort wird gegen bekannte Kategorien bzw. Produktfamilien/-varianten abgeglichen.
- Bei keinem sicheren Treffer bleibt die Kategorie bzw. Produktzuordnung offen und wird strukturiert protokolliert.

**KI-Parsing-Fallback:**

- Der Prompt enthält minimierten oder explizit bestätigten vollständigen Bontext, Regelparser-Teilparse, Fehlergrund, Pflichtfelder, Summenvalidierung, Bonusdefinition und die Anweisung, ausschließlich JSON zurückzugeben.
- Optional darf die KI Parser-Regelvorschläge liefern.
- Vollständige Prompts und Rohantworten werden standardmäßig nicht gespeichert.
- Es werden maximal **3 Retry-Versuche** bei HTTP-5xx-Fehlern unternommen (exponential backoff: 1s, 2s, 4s).

---

## 6. Fachliche Anforderungen (Features)

### F-01: Paperless-NGX Synchronisation

- **F-01.1:** Ein konfigurierbarer Scheduled Job prüft in einem Intervall (Standard: 60 Minuten, konfigurierbar) alle Dokumente mit dem konfigurierten Tag (`PAPERLESS_EBON_TAG`) in Paperless-NGX.
- **F-01.2:** Dokumente, deren `paperless_document_id` noch nicht in der Tabelle `receipt` vorhanden ist, werden als neue Receipts angelegt (`parse_status = PENDING`).
- **F-01.3:** Der `raw_text` (Feld `content` aus der Paperless-API) wird unverändert in der Datenbank gespeichert.
- **F-01.4:** Ein manueller Sync-Trigger ist über die UI und API auslösbar.
- **F-01.5:** Der Sync-Status (letzter Sync, Anzahl neuer Dokumente, Fehler) ist über die API abrufbar.
- **F-01.6:** Bereits importierte Dokumente werden nicht erneut importiert, auch wenn sich ihr Inhalt in Paperless-NGX ändert. Ausnahme: explizites Re-Import-Kommando (UC-09).
- **F-01.7:** Wenn ein zuvor importiertes Dokument in Paperless-NGX den konfigurierten Tag (`PAPERLESS_EBON_TAG`, Standard `eBON`) nicht mehr besitzt, markiert der Sync den lokalen `receipt` per Soft-Delete (`deleted_at`, `delete_reason = TAG_REMOVED`) und protokolliert die Entfernung mit Grund `TAG_REMOVED` (Log-Eintrag als INFO). Das Dokument wird in der `sync_log`-Tabelle als entfernt vermerkt.
- **F-01.8:** `TAG_REMOVED` darf nur nach einem vollständig erfolgreichen Abruf aller Paperless-Paginierungsseiten angewendet werden. Bei leerem oder fehlerhaftem Paperless-Ergebnis werden keine lokalen Bons als entfernt markiert.
- **F-01.9:** Parallele Sync-Läufe sind ausgeschlossen. Läuft bereits ein Sync, gibt `POST /api/sync/trigger` `409 Conflict` mit dem aktuellen Sync-Status zurück.

### F-02: Bon-Parsing

- **F-02.1:** Nach dem Import wird jeder Bon mit `parse_status = PENDING` automatisch geparst (direkt nach Import oder via separatem Parsing-Job).
- **F-02.2:** Der Parser extrahiert aus dem `raw_text` folgende Felder:
  - Datum des Einkaufs (`receipt_date`, `receipt_time`)
  - Geschäftsname (`store_name`) und Filiale/Adresse (`store_branch`)
  - Einzelne Kaufpositionen mit: Bezeichnung, Menge, Einheit, Einzelpreis, Gesamtpreis, Rabatt
  - Gesamtbetrag (`total_amount`)
- **F-02.3:** Der Parser verwendet **primär** reguläre Ausdrücke und strukturbasiertes Parsen (regelbasiert). Der Parser ist erweiterbar für verschiedene Bon-Formate (Store-spezifische Parser als Strategy-Pattern).
- **F-02.3a:** Schlägt das regelbasierte Parsing fehl (kein vollständiger Parse möglich), kann ein **KI-Fallback** über OpenRouter.ai durchgeführt werden. Der Fallback ist über `ai_parsing_fallback_enabled` konfigurierbar, standardmäßig aktiv und läuft nur, wenn ein OpenRouter API-Key verfügbar ist. Für manuelle Reparse-Aufrufe kann der Nutzer den KI-Fallback explizit verwenden oder deaktivieren.
- **F-02.3b:** Der OpenRouter-Parsing-Fallback folgt einem kontrollierten Hybrid-Ansatz: Ein KI-Ergebnis darf den aktuellen Bon automatisch als `PARSED` speichern, aber nur wenn JSON syntaktisch valide ist, das Schema erfüllt ist, Pflichtfelder vorhanden sind, `overallConfidence >= ai_parsing_min_confidence` gilt, `receiptDate`, `storeName`, `totalAmount` valide sind, mindestens eine Position mit `description` und `totalPrice` existiert, `positionIndex` fortlaufend ist, Zahlenformate korrekt normalisiert sind und die Summentoleranz `0.02` erfüllt ist. Erfolgreiche KI-Parses setzen `receipt.parse_source = AI`.
- **F-02.3c:** Der Prompt enthält den minimierten oder vollständigen `raw_text`, den Regelparser-Teilparse, den Fehlergrund des Regelparsers, Pflichtfelder, Summenvalidierung, die Bonusdefinition („nur im Einkauf neu gesammelt") und die Anweisung, ausschließlich JSON zurückzugeben. Die KI muss ein vollständiges finales JSON liefern. Teilantworten oder Antworten außerhalb des Schemas werden nicht als `PARSED` übernommen.
- **F-02.3d:** Der KI-Fallback kann optionale Parser-Regelvorschläge liefern. Diese werden vor Speicherung validiert: Regex syntaktisch valide, Regex passt auf den Beispiel-Bon, erwartetes Feld wird extrahiert, keine offensichtlichen Steuer-, TSE-, Signatur- oder Zahlungszeilen als Item-Kandidaten. Invalide Vorschläge verhindern keinen ansonsten validen KI-Parse. Vorschläge werden in `parse_rule_suggestion` gespeichert, aber nicht automatisch aktiviert. Neue Regeln werden erst nach Nutzerprüfung/-bearbeitung als aktive `parse_rule` mit `source = AI_ADAPTED` übernommen. Diese Rule-Adaptation erzeugt keine `categorization_rule`-Einträge.
- **F-02.3e:** Der KI-Fallback für Parsing muss ein festes JSON-Schema liefern. Antworten außerhalb des Schemas werden verworfen und führen zu `PARSE_ERROR`, außer ein gültiger Teilparse kann nach F-02.5 gespeichert werden. Jeder Versuch wird in `ai_parsing_log` protokolliert.
- **F-02.3f:** Automatische KI-Parsing-Calls im Sync sind pro Sync-Lauf begrenzt (`ai_parsing_sync_call_limit`, Standard `25`). Wird das Limit erreicht, bleibt der Bon `PARSE_ERROR` mit einer klaren Meldung, dass der KI-Fallback wegen Limit übersprungen wurde.
- **F-02.3g:** Für den an OpenRouter gesendeten Text gibt es `ai_parsing_text_mode`: `MINIMIZED` (Standard) entfernt offensichtliche Zahlungs-, Steuer-, TSE-, Signatur- und Metadatenblöcke soweit möglich; `FULL_TEXT` sendet den vollständigen Paperless-Text. `FULL_TEXT` bei manuellem Reparse verlangt eine ausdrückliche Bestätigung in der UI.
- **F-02.3h:** Wenn ein späterer Regelparser-Reparse vollständig und valide ist, darf er ein früheres KI-Parsergebnis ersetzen und `receipt.parse_source = RULE` setzen. Manuelle Edits dürfen dabei nicht still überschrieben werden, soweit sie im jeweiligen Datenmodell abgebildet sind.
- **F-02.3i:** Wenn ein Bon die Filialadresse nicht als Text enthält (z.B. Adresse nur als Grafik im dm-eBon), darf der Parser eine aus dem Text extrahierte Filial-ID über eine konfigurierbare Mapping-Tabelle auf `store_branch` auflösen. Ist kein Mapping vorhanden, bleibt ein technischer, eindeutig nachvollziehbarer Fallback wie `Filiale <Code>` zulässig.
- **F-02.3j:** KI-Parsing und KI-Kategorisierung bleiben getrennte Workflows mit getrennten Logs und getrennten UI-Flows. KI-Parsing darf keine `categorization_rule` erzeugen.
- **F-02.3k:** Keine Modell-Fallback-Kette ist Teil der ersten Umsetzung. Das konfigurierte Parsing-Modell wird verwendet; Fehler werden transparent in `ai_parsing_log` protokolliert.
- **F-02.4:** Der Parser extrahiert aus dem `raw_text` zusätzlich folgende Bonus-Felder:
  - `bonus_balance`: In diesem Einkauf neu gesammeltes Bonusguthaben, nicht das aktuelle Bonuskonto-/Punkteguthaben
  - `bonus_points`: In diesem Einkauf neu gesammelte Payback-Punkte oder ähnliche Punktesysteme (mit Typ-Angabe)
  - `bonus_type`: Art des Bonusprogramms (z.B. „Payback", „DeutschlandCard", „Bonusclub")
- **F-02.5:** Kann ein Bon nicht vollständig geparst werden, wird `parse_status = PARSE_ERROR` gesetzt und `parse_error_message` befüllt. Teilweise geparste Daten werden dennoch gespeichert.
- **F-02.6:** Ein erfolgreich geparstes Dokument erhält `parse_status = PARSED`. **Definition „PARSED":** Ein Bon gilt als erfolgreich geparst (PARSED), wenn mindestens `total_amount`, `receipt_date` und `store_name` extrahiert wurden UND mindestens eine `receipt_item` mit gültigem `total_price` vorliegt. Fehlen einzelne optionale Felder (z.B. `receipt_time`, `store_branch`), gilt der Bon dennoch als PARSED.
- **F-02.7:** Der Nutzer kann den Re-Parse eines einzelnen Bons über die UI triggern (UC-09).
- **F-02.7a:** Vor einem Einzel-Reparse prüft die Anwendung den aktuellen Paperless-Text des zugehörigen Dokuments gegen den gespeicherten `receipt.raw_text`. Der Vergleich normalisiert ausschließlich Zeilenenden (`CRLF`/`LF`); sonstige Textunterschiede gelten als Änderung.
- **F-02.7b:** Bei geändertem Paperless-Text fragt die Detailansicht ausdrücklich, ob der neue Text übernommen und für den Reparse verwendet werden soll. Der Nutzer kann alternativ den gespeicherten Rohtext verwenden oder abbrechen. Die Statusprüfung überträgt weder Rohtext noch Hashes an die UI.
- **F-02.7c:** Der Einzel-Reparse akzeptiert `rawTextSource=STORED|PAPERLESS`, mit `STORED` als API-Default. Bei `PAPERLESS` lädt das Backend den Text unmittelbar vor dem Parse erneut, aktualisiert `receipt.raw_text` und parst denselben Text transaktional. Schlägt der Abruf fehl, bleibt der Bon unverändert; die UI bietet dann nur den gespeicherten Text an.
- **F-02.7d:** Bulk-Reparse, Reparse nach Regelübernahme und automatische Sync-Läufe verwenden immer den gespeicherten Rohtext und führen keine dialogpflichtige Paperless-Rohtextaktualisierung aus.

**Parser-Normalisierung und Validierung:**

- Deutsche Zahlenformate werden normalisiert: `1,99` → `1.99`, `1.234,56` → `1234.56`.
- Negative Beträge, Rabatte, Coupons und Pfandpositionen werden als eigene Positionen gespeichert, sofern sie im Bon als Position erscheinen.
- Mehrzeilige Artikelbezeichnungen werden zu einer `description` zusammengeführt.
- Die Summe aller `receipt_item.total_price` darf vom `receipt.total_amount` maximal um `0.02` abweichen. Größere Abweichungen setzen `parse_status = PARSE_ERROR`, speichern aber den Teilparse.
- `receipt_item.position_index` ist pro Bon fortlaufend und eindeutig.

**JSON-Schema für KI-Parsing-Fallback (semantisch):**

`bonusBalance` steht für neu in diesem Einkauf gesammeltes Bonusguthaben, nicht für den aktuellen Konto-/Punktestand des Bonusprogramms.

```json
{
  "receiptDate": "YYYY-MM-DD",
  "receiptTime": "HH:mm:ss|null",
  "storeName": "string",
  "storeBranch": "string|null",
  "totalAmount": 12.34,
  "currency": "EUR",
  "bonusBalance": 0.0,
  "bonusPoints": 0.0,
  "bonusType": "string|null",
  "overallConfidence": 0.95,
  "fieldConfidence": {
    "receiptDate": 0.98,
    "storeName": 0.95,
    "totalAmount": 0.99,
    "items": 0.93
  },
  "warnings": [
    "storeBranch konnte nur aus Kontext abgeleitet werden"
  ],
  "items": [
    {
      "positionIndex": 0,
      "description": "string",
      "quantity": 1.0,
      "unit": "string|null",
      "unitPrice": 1.23,
      "totalPrice": 1.23,
      "discountAmount": 0.0
    }
  ],
  "parseRuleSuggestions": [
    {
      "ruleType": "ITEM_PATTERN",
      "storeName": "string|null",
      "matchRegex": "string",
      "extractGroup": "string|null",
      "confidence": 0.91,
      "problemDescription": "Welche Parser-Schwäche der Vorschlag adressiert",
      "solutionRationale": "Warum diese Regel das Problem lösen soll"
    }
  ]
}
```

`overallConfidence`, `fieldConfidence`, `warnings` und `parseRuleSuggestions` sind optionale KI-Metadaten. Sie dürfen die harte Backend-Validierung nicht ersetzen. `parseRuleSuggestions` werden unabhängig vom Parse-Ergebnis validiert; invalide Vorschläge verhindern keinen ansonsten validen KI-Parse.

### F-03: Kategorisierung

- **F-03.1:** Nach dem Parsing wird jede `receipt_item` kategorisiert.
- **F-03.2:** Die Kategorisierung erfolgt zweistufig:
  1. **Regelbasiert:** Alle aktiven `categorization_rule`-Einträge werden in Prioritätsreihenfolge gegen `description` und/oder `store_name` geprüft. Bei Treffer: Zuweisung der Kategorie, `category_source = RULE`.
  2. **KI-Fallback:** Gibt es keinen Regeltr­effer, wird ein KI-Call an OpenRouter.ai gesendet. Nur wenn die KI-Antwort einer aktiven Kategorie eindeutig zugeordnet werden kann und die konfigurierte Mindest-Konfidenz erreicht, wird diese Kategorie gesetzt und `category_source = AI` gespeichert. Der Call wird in `ai_categorization_log` protokolliert.
- **F-03.3:** KI-Calls für die Kategorisierung werden **gebündelt**: Pro Bon wird ein einziger KI-Call mit allen unkategorisierten Positionen des Bons abgesetzt. Wenn die Antwort nicht valide ist, das Modell keine verwertbare Batch-Antwort liefert, keine gültige Kategorie gefunden wird oder die Konfidenz unter `app_settings.ai_categorization_min_confidence` liegt, bleibt die Position unkategorisiert (`category_id = NULL`, `category_source = NULL`) und kann später in der UI manuell bearbeitet werden. Ein nicht übernommener KI-Vorschlag wird dennoch strukturiert in `ai_categorization_log` gespeichert, damit die UI ihn als Entscheidungshilfe anzeigen kann.
- **F-03.4:** Wenn die KI eine Kategorie ermittelt, die dem Nutzer als neue Regel vorgeschlagen wird (UC-06), kann der Nutzer die Regel bestätigen und speichern.
- **F-03.5:** Kategorisierung kann manuell überschrieben werden (UC-07). In diesem Fall: `category_source = MANUAL`, `is_manually_edited = TRUE`.
- **F-03.6:** Eine Kategorisierung kann für einzelne Positionen oder alle gleichen Beschreibungen im gesamten Datenbestand angewendet werden (Bulk-Kategorisierung).
- **F-03.7:** Wenn kein `OPENROUTER_API_KEY` konfiguriert ist, wird der KI-Fallback übersprungen. Positionen bleiben unkategorisiert (`category_id = NULL`, `category_source = NULL`) und werden in der UI als „Ohne Kategorie" angezeigt.
- **F-03.8:** `category_source = AI` darf nie ohne gesetzte `category_id` persistiert werden. `category_source = NULL` bedeutet, dass die Position noch offen ist und vom Nutzer später manuell kategorisiert werden kann.

### F-04: Suche

- **F-04.1:** Der Nutzer kann über eine Suchmaske nach folgenden Feldern suchen (kombinierbar, AND-verknüpft):
  - Geschäftsname (Partial-Match, case-insensitive)
  - Positions-Beschreibung (Volltextsuche, PostgreSQL `tsvector`)
  - Datum von / Datum bis (`receipt_date`)
  - Kategorie (exakt, Mehrfachauswahl)
  - Produktfamilie und Produktvariante (exakt, Mehrfachauswahl)
  - Betrag von / Betrag bis (`total_price` der Position)
- **F-04.2:** Suchergebnisse werden paginiert zurückgegeben (Standard: 20 pro Seite, konfigurierbar bis 100).
- **F-04.3:** Suchergebnisse können nach Datum, Betrag oder Geschäftsname sortiert werden (auf- und absteigend).
- **F-04.4:** Die Suche gibt sowohl Bon-Ebene als auch Positions-Ebene zurück.

### F-05: Reports

- **F-05.1:** Reports aggregieren `receipt_item.total_price` nach konfigurierbaren Dimensionen.
- **F-05.2:** Folgende Report-Typen sind verfügbar:

  | Report-Typ | Beschreibung |
  |---|---|
  | Ausgaben nach Kategorie | Summe pro Kategorie, optional mit Zeitraumfilter |
  | Ausgaben nach Zeitraum | Summe pro Tag / Woche / Monat / Jahr |
  | Ausgaben nach Geschäft | Summe und Anzahl Besuche pro Geschäft |
  | Kombinierter Report | Kategorie + Zeitraum oder Geschäft + Zeitraum |
  | Top-Artikel | Häufigste/teuerste Positionen im Zeitraum |
  | Bonusübersicht | Neu gesammeltes Bonusguthaben und neu gesammelte Punkte aggregiert nach Typ und Geschäft |
  | Produktfamilie | Preis- und Mengenvergleich pro Produktfamilie |
  | Produktvariante | Konkreter Variantenpreis, Einheitenpreis und Verlauf |

- **F-05.3:** Alle Reports unterstützen Filterung nach: Zeitraum (von/bis), Geschäft, Kategorie. Produktreports unterstützen zusätzlich Produktfamilie, Produktvariante und Store-Gruppierung (`store_name` oder `store_name + store_branch`).
- **F-05.4:** Report-Daten werden als JSON über die API geliefert und im Frontend als Balkendiagramm, Kreisdiagramm oder Tabelle dargestellt.
- **F-05.5:** Reports können als CSV-Datei exportiert werden. CSV-Exports enthalten, soweit verfügbar, Produktfamilie, Produktvariante, Einheitenpreis, Zuordnungsquelle und Zuordnungsstatus.

### F-06: Manuelles Editieren

- **F-06.1:** Der Nutzer kann folgende Felder eines Bons manuell bearbeiten:
  - `receipt_date`, `receipt_time`, `store_name`, `store_branch`, `total_amount`, `bonus_balance`, `bonus_points`, `bonus_type`
- **F-06.2:** Der Nutzer kann folgende Felder einer Bon-Position manuell bearbeiten:
  - `description`, `quantity`, `unit`, `unit_price`, `total_price`, `discount_amount`, `category_id`, `product_family_id`, `product_variant_id`
- **F-06.3:** Nach manuellem Editieren wird `is_manually_edited = TRUE` und `parse_status = MANUALLY_EDITED` gesetzt.
- **F-06.4:** Positionen mit `is_manually_edited = TRUE` werden beim Re-Parse **nicht automatisch überschrieben**. Re-Parse erzeugt stattdessen einen **Konflikthinweis** und fordert explizite Bestätigung, bevor eine Überschreibung geschieht. **Standard: keine Überschreibung** manuell editierter Felder.
- **F-06.5:** Einzelne Positionen können gelöscht werden.
- **F-06.6:** Neue Positionen können zu einem Bon manuell hinzugefügt werden.

### F-07: Kategorisierungsregeln verwalten

- **F-07.1:** Der Nutzer kann Kategorisierungsregeln anlegen, bearbeiten, deaktivieren und löschen.
- **F-07.2:** Regelfelder: Zielkategorie, Match-Feld (`DESCRIPTION` oder `STORE_NAME`), Match-Typ (`CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `EXACT`, `REGEX`), Match-Wert, Priorität.
- **F-07.3:** Bei Speichern einer neuen Regel kann der Nutzer wählen, ob alle bestehenden Positionen ohne Kategorie oder mit KI-Kategorie rückwirkend mit der neuen Regel kategorisiert werden sollen (Bulk-Apply).
- **F-07.4:** Eine Regelvorschau zeigt vor dem Speichern an, wie viele bestehende Positionen von der Regel betroffen wären.

### F-08: Kategorien verwalten

- **F-08.1:** Der Nutzer kann Kategorien anlegen, umbenennen, deaktivieren und wieder aktivieren. Physisches Löschen ist nur erlaubt, wenn keine `receipt_item`-Einträge und keine aktiven Regeln auf die Kategorie verweisen.
- **F-08.2:** Beim Deaktivieren einer Kategorie bleiben bestehende `receipt_item`-Zuordnungen erhalten. Die Kategorie wird in Filtern und neuen Zuordnungen standardmäßig ausgeblendet, kann aber mit `includeInactive=true` angezeigt werden.
- **F-08.3:** Jede Kategorie hat einen Namen, optional eine Farbe und ein Icon-Bezeichner.

### F-09: Backup & Restore

- **F-09.1:** Ein vollständiges Backup aller Daten (alle Tabellen) kann über die UI und API als ZIP-Archiv heruntergeladen werden.
- **F-09.2:** Das ZIP-Archiv enthält eine Datei pro Tabelle im JSON-Format sowie eine `manifest.json` mit Version, Datum und Tabellenübersicht.
- **F-09.3:** Ein Restore aus einer Backup-ZIP-Datei ist über Upload in der UI möglich.
- **F-09.4:** Vor dem Restore wird validiert: Backup-Format, Version, Vollständigkeit. Fehler werden detailliert angezeigt.
- **F-09.5:** Der Restore löscht alle vorhandenen Daten und ersetzt sie durch die Backup-Daten (Full Restore). Es gibt keinen partiellen Restore.
- **F-09.6:** Während Backup und Restore sind andere schreibende Operationen gesperrt (pessimistischer Lock via Application-Level-Flag).

### F-10: Dashboard

- **F-10.1:** Das Dashboard zeigt auf einen Blick:
  - Gesamtausgaben im aktuellen Monat, Gesamtausgaben im Vormonat und Gesamtausgaben im aktuellen Jahr
  - Übersicht des neu gesammelten Bonusguthabens und der neu gesammelten Punkte (Summe nach Typ)
  - Ausgaben nach Kategorie (Tortendiagramm)
  - Letzte 5 Bons als Schnellnavigation zu den zuletzt datierten, nicht gelöschten Bons
  - Anzahl der Positionen ohne Kategorie (mit Link zur Kategorisierung)
  - Status des letzten Syncs (Zeitpunkt, Anzahl neue Bons, ggf. Fehler, Anzahl TAG_REMOVED)
- **F-10.2:** „Letzte Bons" sortiert standardmäßig nach `receipt_date DESC`, `receipt_time DESC`, `imported_at DESC` und dient als schnelle Einstiegsliste, nicht als vollständige Historie.
- **F-10.3:** Die Bonus-Anzeige bezieht sich ausschließlich auf neu im gewählten Zeitraum gesammelte Bonusguthaben/Punkte, nicht auf aktuelle Bonuskonto-/Punktestände.
- **F-10.4:** Kategorie- und Bonus-Diagramme auf dem Dashboard unterstützen Schnellfilter für aktueller Monat, letztes Quartal, letztes Jahr sowie einen benutzerdefinierten Zeitraum von/bis.
- **F-10.5:** Die KPI bzw. Anzeige „Ohne Kategorie" ist anklickbar und navigiert zu einer Liste/Suche, die nur Positionen mit `category_id = NULL` und `category_source = NULL` zeigt.

### F-11: Einstellungen

- **F-11.1:** Über eine Einstellungsseite kann der Nutzer konfigurieren:
  - Paperless-NGX URL und API-Token (mit Verbindungstest)
  - optionale Paperless-Web-URL oder Dokument-URL-Vorlage für klickbare Links zu Paperless-Dokumenten
  - OpenRouter API-Key und Modell-Auswahl (mit Verbindungstest)
  - Mindest-Konfidenz für automatische KI-Kategorisierung (`ai_categorization_min_confidence`, Standard `0.900`, Wertebereich `0.000` bis `1.000`)
  - OpenRouter KI-Parsing-Fallback: global aktiv/inaktiv (Default aktiv), separates Parsing-Modell, Max Tokens, Temperature, Mindest-Konfidenz (`ai_parsing_min_confidence`, Standard `0.900`), Sync-Call-Limit (Default `25`), Textmodus (`MINIMIZED`/`FULL_TEXT`) und lokales Debug-Snippet-Verhalten
  - Produkt-Historie-Defaults: Mindestanzahl bestätigter Treffer (`product_history_min_confirmed_matches`, Default `3`) und Mindestanteil derselben Variante (`product_history_min_variant_share`, Default `0.900`)
  - Sync-Intervall (Minuten)
  - Anzuzeigende Währung
- **F-11.2:** Sensible Felder (API-Keys) werden bei der Anzeige maskiert. Maskierte Platzhalterwerte dürfen beim Speichern nicht als neue Secrets persistiert werden. Klartext-Secrets werden ausschließlich beim initialen Setzen oder expliziten Ersetzen übertragen.
- **F-11.3:** Einstellungen werden in `app_settings` gespeichert (nicht in Umgebungsvariablen überschreibbar via UI, aber initial aus Umgebungsvariablen befüllt).
- **F-11.4:** Secrets in `app_settings` müssen in Logs, Fehlerantworten, Backup-Metadaten und UI-Responses maskiert werden. Für lokale Single-User-Deployments ist Speicherung im Klartext in der Datenbank zulässig, aber die Implementierung muss zentral über einen `SecretValue`/Maskierungsmechanismus erfolgen, damit spätere Verschlüsselung möglich bleibt.
- **F-11.5:** Einstellungen enthalten einen administrativen Bereich „Datenwartung" mit:
  - Re-Parse aller Bons, standardmäßig ohne Überschreiben manueller Änderungen.
  - Reset aller importierten Bon-Daten für ein vollständiges erneutes Einlesen aus Paperless-NGX. Der Reset löscht `receipt`, `receipt_item` und zugehörige KI-/Sync-Detaildaten, behält aber Kategorien, Kategorisierungsregeln, Produktstammdaten, Produktregeln, App-Einstellungen und Flyway-Migrationen. Die Aktion ist destruktiv und verlangt eine deutliche Bestätigung.
  - Separater Produktdaten-Reset für Produktfamilien, Varianten, Produktregeln, Reviewstatus und Preis-Ausschlüsse. Diese Aktion ist getrennt vom Bon-Daten-Reset, transaktional und verlangt eine eigene deutliche Bestätigung.

### F-12: Test-Suite

- **F-12.1:** Für fachlich kritische Backend-Komponenten existieren automatisierte Tests. Priorität haben Parser, OpenRouter-KI-Parsing-Fallback, Sync, Kategorisierung, Produktzuordnung, Produktpreisreports, Backup/Restore, Settings/Secret-Masking und API-Fehlerbehandlung.
- **F-12.2:** Verwendete Test-Frameworks: **JUnit 5**, **Mockito** (Mocking), **Cucumber** (BDD/Akzeptanztests für Parsing und Kategorisierung).
- **F-12.3:** Testabdeckung wird über JaCoCo gemessen; Ziel: ≥ 80 % Zeilenabdeckung für Service-Klassen und ≥ 90 % Branch-Coverage für Parser-Klassen.
- **F-12.4:** Cucumber-Feature-Files definieren Akzeptanzkriterien für:
  - Bon-Parsing (regelbasiert und KI-Fallback)
  - KI-Parsing-Log und Parser-Regelvorschläge
  - Kategorisierung (Regeln + KI)
  - Produktzuordnung (Regeln, Historie, KI-Vorschläge, Review)
  - Produktpreisvergleich (Einheitenpreise, Mehrfachpackungen, Ausreißer)
  - Sync-Verhalten (inkl. TAG_REMOVED)
  - Re-Parse-Konfliktauflösung
- **F-12.5:** Tests werden automatisch im CI-Build (`mvn verify`) ausgeführt.
- **F-12.6:** Tests dürfen keine echten Paperless-NGX- oder OpenRouter.ai-Calls ausführen. Externe Systeme werden über WireMock, MockWebServer oder vergleichbare Test-Doubles simuliert.

### F-13: Parser-Test-Corpus

- **F-13.1:** Ein Test-Corpus aus repräsentativen Bon-Beispielen wird im Repository unter `backend/src/test/resources/corpus/` abgelegt.
- **F-13.2:** Der Corpus enthält Bons verschiedener Geschäfte und Formate (Rewe, Edeka, Aldi, Lidl, DM, Rossmann, etc.) im Textformat.
- **F-13.3:** Für jeden Corpus-Bon existiert eine erwartete JSON-Ausgabe (`<name>.expected.json`) mit den erwarteten Parse-Ergebnissen.
- **F-13.4:** Ein parametrisierter JUnit-5-Test (`@ParameterizedTest` mit `@MethodSource`) iteriert über alle Corpus-Einträge und prüft, ob das Parse-Ergebnis mit der erwarteten Ausgabe übereinstimmt.
- **F-13.5:** Bei Erweiterung des Corpus (neue Bon-Formate) werden automatisch die entsprechenden Tests ausgeführt. Neue Corpus-Einträge können per PR eingereicht werden.

### F-14: OpenAPI / Swagger UI

- **F-14.1:** Die Backend-REST-API wird vollständig mit **OpenAPI 3.1** dokumentiert (via SpringDoc OpenAPI / `springdoc-openapi-starter-webmvc-ui`).
- **F-14.2:** Swagger UI ist unter `/swagger-ui.html` (oder konfiguriertem Pfad) erreichbar.
- **F-14.3:** Alle Endpunkte, Request/Response-DTOs, Query-Parameter und Fehler-Responses sind dokumentiert.
- **F-14.4:** Die OpenAPI-Spezifikation (JSON/YAML) ist unter `/v3/api-docs` abrufbar.
- **F-14.5:** Authentifizierung (Bearer Token) ist in Swagger UI konfigurierbar.

### F-15: Health-Checks

- **F-15.1:** Spring Boot Actuator Health-Endpunkte sind aktiviert:
  - `GET /api/health` – Öffentlicher Health-Check (ohne Authentifizierung): Gibt `{ "status": "UP" }` zurück.
  - `GET /api/actuator/health` (optional, mit Auth) – Detaillierter Health-Check inkl. DB-Connectivity und Paperless-NGX-Erreichbarkeit.
- **F-15.2:** Der Health-Check prüft:
  - Datenbankverbindung (PostgreSQL Connectivity)
  - Paperless-NGX-Erreichbarkeit (optional, via `PAPERLESS_BASE_URL`)
  - OpenRouter.ai-Erreichbarkeit (optional, nur wenn `OPENROUTER_API_KEY` konfiguriert)
- **F-15.3:** Docker-Healthcheck für den Backend-Container nutzt `GET /api/health`.

### F-16: Restore-Runbook & Dry-Run

- **F-16.1:** Ein **Restore-Runbook** (`docs/restore-runbook.md`) dokumentiert den vollständigen Wiederherstellungsprozess:
  - Voraussetzungen (Docker, Backup-ZIP, Umgebungsvariablen)
  - Schritt-für-Schritt-Anleitung für Restore
  - Fehlerbehebung bei häufigen Problemen
  - Validierung nach Restore (Datenintegrität prüfen)
- **F-16.2:** **Restore Dry-Run:** Vor dem eigentlichen Restore kann ein Dry-Run über die API (`POST /api/backup/validate`) durchgeführt werden:
  - Validiert das Backup-ZIP-Format, die Manifest-Kompatibilität und die Datenintegrität
  - Gibt einen detaillierten Validierungsbericht zurück (Anzahl validierter Records pro Tabelle, Warnungen, Fehler)
  - Verändert **keine** Daten in der Datenbank
- **F-16.3:** Der Dry-Run-Bericht enthält:
  ```json
  {
    "valid": true,
    "manifestVersion": "1",
    "tables": [
      { "name": "categories", "recordCount": 8, "valid": true },
      { "name": "receipts", "recordCount": 142, "valid": true }
    ],
    "warnings": [],
    "errors": []
  }
  ```

### F-17: Sync-Log & Tag-Entfernung

- **F-17.1:** Jeder Sync-Lauf wird in einer internen `sync_log`-Tabelle protokolliert (Startzeit, Endzeit, Status, Anzahl neuer/entfernter Dokumente, Fehlermeldung).
- **F-17.2:** Bei Entfernung eines Dokuments wegen `TAG_REMOVED` wird ein dedizierter Log-Eintrag erstellt (Grund `TAG_REMOVED`, paperless_document_id, vorherige Receipt-ID, Zeitstempel).
- **F-17.3:** Der Sync-Log ist über `GET /api/sync/log` (paginiert) abrufbar.

### F-18: CI, E2E, Rollierende Backups, Icons & Versionierung

- **F-18.1:** Das Repository enthält eine GitHub-Actions-CI-Pipeline für Pull Requests und Pushes auf den Hauptbranch.
- **F-18.2:** Die CI führt mindestens aus:
  - `cd backend && mvn verify`
  - `cd frontend && npm ci`
  - `cd frontend && npm run build`
  - `docker compose config`
- **F-18.3:** CI-Tests dürfen keine echten Paperless-NGX- oder OpenRouter.ai-Calls ausführen und dürfen keine echten Secrets benötigen.
- **F-18.4:** Es existiert mindestens ein Selenium-basierter Frontend-Smoke-Test für zentrale UI-Flows:
  - App startet und Dashboard/Navigation rendert.
  - Einstellungen-Seite ist erreichbar.
  - Backup-Tab zeigt Download/Dry-Run/Restore-Bedienelemente.
  - Receipt- oder Suchseite rendert mit Testdaten oder Mockdaten.
- **F-18.5:** Automatische rollierende Backups können zeitgesteuert aktiviert werden. Standardmäßig sind sie lokal deaktiviert oder sicher konfiguriert, damit keine unerwarteten Dateien entstehen.
- **F-18.6:** Rollierende Backups verwenden dasselbe Secret-Masking und dasselbe ZIP-Format wie manuelle Backups.
- **F-18.7:** Für rollierende Backups sind konfigurierbar: Zielverzeichnis/Volume, Intervall oder Cron-Ausdruck, maximale Anzahl aufzubewahrender Backups und Aktivierungsflag.
- **F-18.8:** Alte automatische Backups werden nach Retention-Regel gelöscht. Manuell heruntergeladene Backups werden nicht durch die Retention gelöscht.
- **F-18.9:** Kategorie-Icons werden in der UI auswählbar und in relevanten Ansichten angezeigt, ohne die bestehende Kategorie-Farbkodierung zu ersetzen.
- **F-18.10:** Kategorie-Icons werden gegen eine feste erlaubte Icon-Liste validiert, damit keine beliebigen Icon-Namen oder HTML/SVG-Fragmente persistiert werden.
- **F-18.11:** Die Anwendung zeigt eine konsistente Software-Version in UI und Backend an.
- **F-18.12:** Die Versionsinformation wird zentral aus Build-/Projektmetadaten abgeleitet und in Backup-Manifest, OpenAPI-Info, UI-Footer oder Settings-Bereich sowie optional Docker-Labels verwendet.

### F-19: Produktzuordnung und Produktpreisvergleich

- **F-19.1:** Die Anwendung führt eine Produktschicht oberhalb der Bon-Positionen ein. Mehrere unterschiedliche Bontexte können derselben Produktfamilie und, wenn eindeutig, derselben Produktvariante zugeordnet werden. Beispiel: `CC Zero` und `Coca Cola Zero` können dieselbe Produktfamilie meinen; `Coca Cola Zero 0,33l` und `Coca Cola Zero 0,5l` sind unterschiedliche Varianten.
- **F-19.2:** Das Produktmodell besteht aus Produktfamilie und Produktvariante. Eine Produktfamilie beschreibt das vergleichbare Produkt auf Mengenebene, z.B. `Coca Cola Zero`. Eine Produktvariante beschreibt das konkrete Gebinde oder die konkrete Kaufvariante, z.B. `Coca Cola Zero 0,33l Flasche`.
- **F-19.3:** Marke wird nicht als eigenes Feld modelliert, sondern bleibt Teil des Produktnamens. Pfand und Tüten können eigene Produktfamilien/Varianten sein.
- **F-19.4:** Eine `receipt_item` darf maximal eine Produktzuordnung besitzen. Mehrfachprodukt-Zuordnung oder Aufsplitten einer einzelnen Bon-Position in mehrere Produktanteile ist nicht Teil von Phase 15.
- **F-19.5:** Varianten mit unterschiedlicher Größe, Einheit oder Packungsstruktur dürfen nicht automatisch zusammengeführt werden. Mehrfachpackungen speichern Einzelgebinde und Packungsgröße, z.B. `6x0,33l`, damit Variante und Gesamtmenge auswertbar bleiben.
- **F-19.6:** Externe Produktdatenbanken und Barcode-Scan-Workflows sind nicht Teil von Phase 15. EAN/GTIN kann als optionales Feld vorbereitet werden.
- **F-19.7:** Produktregeln bleiben getrennt von Kategorisierungsregeln. Eine Produktregel setzt Produktfamilie und optional Variante. Eine Produktfamilie darf über `default_category_id` nur leere Kategorien füllen und niemals bestehende oder manuelle Kategorien überschreiben.
- **F-19.8:** Produktzuordnung läuft nach Parsing und Kategorisierung automatisch. Reihenfolge: Produktregel/Synonym, klare Historie, KI-Fallback, sonst `NEEDS_REVIEW` oder `NO_PRODUCT` je nach erkannter Positionsart.
- **F-19.9:** Produktregeln matchen gegen `receipt_item.description`, können global oder optional auf `store_name` eingeschränkt sein, und verwenden Match-Typen analog zu Kategorisierungsregeln: `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `EXACT`, `REGEX`. Store-spezifische Regeln dürfen globale Regeln übersteuern.
- **F-19.10:** Beim Speichern einer Produktregel kann der Nutzer wählen, ob sie nur künftig oder auch auf bestehende Positionen angewendet wird. Vor rückwirkendem Anwenden zeigt das Backend eine Vorschau mit Anzahl betroffener Positionen, Stores, Zeitraum und geplanter Änderung. Rückwirkende Anwendung erfolgt nur nach expliziter Bestätigung.
- **F-19.11:** Klare Historie darf eine automatische Variantenzuordnung stützen, wenn aus dem Bontext keine Variante sicher erkennbar ist. Defaults: mindestens `3` frühere vertrauenswürdige Treffer für denselben normalisierten Positions-/Store-Kontext und mindestens `90 %` dieselbe Variante. Diese Schwellen sind über `app_settings` konfigurierbar.
- **F-19.12:** Als vertrauenswürdige Historie zählen manuelle Zuordnungen, akzeptierte Vorschläge/Regeln und regelbasierte automatische Treffer. Reine KI-Treffer dürfen nicht allein klare Historie für spätere automatische Variantenentscheidungen begründen.
- **F-19.13:** KI-Produktzuordnung orientiert sich an der bestehenden KI-Kategorisierung und nutzt in Phase 15 deren OpenRouter-Konfiguration. Es gibt kein separates Produkt-KI-Sync-Call-Limit. Gesendet werden dürfen normalisierte Positionsdaten, Store, Preis und Menge; vollständige Bon-Rohtexte dürfen nicht gesendet werden. Vollständige Prompts und Rohantworten werden standardmäßig nicht gespeichert.
- **F-19.14:** KI darf Produktfamilie und Variante automatisch setzen, wenn Konfidenz, erkannte Einheit, Größe und Preislogik plausibel sind. Unsichere KI-Ergebnisse erhalten `product_assignment_status = NEEDS_REVIEW`.
- **F-19.15:** Reine Rabatte, Coupons, Zahlungszeilen und Rundungsdifferenzen werden standardmäßig als `NO_PRODUCT` geführt. Pfand und Tüten werden nicht pauschal ausgeschlossen und können normale Produktfamilien sein.
- **F-19.16:** Phase 15b stellt eine Prüfliste „Produktzuordnung prüfen" bereit. Sie zeigt Positionen mit `NEEDS_REVIEW`, niedriger oder widersprüchlicher Konfidenz, unklarer Größe, Konflikten zwischen Regel/Historie/KI und auffälligen Preisen. Standardsortierung ist nach Nutzen: häufige und teure Positionen zuerst.
- **F-19.17:** Die Prüfliste unterstützt Filter nach Unsicherheit, Store, Produktfamilie, Kategorie, Zeitraum, Zuordnungsquelle und Status. Jeder Eintrag zeigt Bon-Datum, Store/Filiale, Positionsbeschreibung, Menge/Einheit/Preis, vorgeschlagene Familie/Variante, Konfidenz, Begründung und mögliche rückwirkende Auswirkungen.
- **F-19.18:** Der Nutzer kann Vorschläge akzeptieren, korrigieren, ablehnen, neue Produktfamilien/Varianten anlegen, die Zuordnung entfernen, als `NO_PRODUCT` markieren oder aus einer manuellen Zuordnung eine Produktregel mit Vorschau erstellen. In der Prüfliste muss das Anlegen einer fehlenden Produktfamilie direkt im Korrekturdialog möglich sein; der Nutzer darf nicht erst in die Stammdatenpflege wechseln müssen.
- **F-19.18a:** Wenn mehrere offene Positionen mit gleicher normalisierter Beschreibung im gleichen `store_name` existieren, kann der Nutzer eine manuelle Produktzuordnung optional auf diese gleichartigen offenen Positionen anwenden. Bereits manuell bestätigte Produktzuordnungen dürfen dadurch nicht überschrieben werden.
- **F-19.19:** Korrektur-Workflows sind Pflicht: Produktfamilien/Varianten zusammenführen, falsch zusammengelegte Einträge trennen, einzelne Bon-Positionen umhängen oder Zuordnungen entfernen. Historisch wirkende Änderungen benötigen immer Vorschau und Bestätigung und müssen transaktional sein.
- **F-19.20:** Beim Reparse werden Produktzuordnungen neu abgeleitet. Bestätigte Zuordnungen sollen bestmöglich übertragen werden, wenn Beschreibung, Preis und Menge plausibel passen. Konflikte werden sichtbar gemacht, nicht still überschrieben.
- **F-19.21:** Produktpreisreports verwenden bestätigte und automatisch zugeordnete Preisbeobachtungen. Positionen mit `NEEDS_REVIEW`, `REJECTED`, `NO_PRODUCT` oder `exclude_from_product_price_comparison = true` werden nicht still in Vergleichszahlen gemischt.
- **F-19.22:** Produktfamilienreports vergleichen normalisierte Einheitenpreise, z.B. `€/l`, `€/kg` oder `€/Stück`, und können nach `store_name` oder `store_name + store_branch` gruppieren. Ein eigenes Store-Stammdatenmodell ist nicht Teil von Phase 15.
- **F-19.23:** Produktvariantenreports zeigen konkreten Positionspreis, Einheitenpreis, letzten bekannten Preis je Store, historisches Minimum, Durchschnitt, Median, Preisverlauf und zugrunde liegende Bon-Positionen.
- **F-19.24:** Standardpreislogik ist der effektiv gezahlte Preis inklusive Rabatten und Aktionen. Ein regulärer Preis wird zusätzlich nur angezeigt, wenn er sicher ableitbar ist. In der ersten Implementierung gilt ausschließlich ein positionsbezogener negativer `discount_amount` als sichere Grundlage (`regularPrice = effectivePrice + abs(discount_amount)`); `unit_price` und nicht eindeutig zuordenbare Rabattzeilen werden nicht geraten oder positionsübergreifend verrechnet.
- **F-19.25:** Auffällige Preise werden markiert. Der Nutzer kann einzelne Preisbeobachtungen vom Produktpreisvergleich ausschließen und wieder einschließen. Ausschlüsse bleiben auditierbar und reversibel.
- **F-19.26:** Suche, Reports und CSV-Exports werden um Produktfamilie, Produktvariante, Einheitenpreis, Zuordnungsquelle, Zuordnungsstatus und Ausschlussstatus erweitert.

---

## 7. Use Cases

### UC-01: Sync mit Paperless-NGX (automatisch)

**Akteur:** System (Scheduled Job)  
**Vorbedingung:** Paperless-NGX URL und API-Token sind konfiguriert.  
**Auslöser:** Ablauf des konfigurierten Sync-Intervalls.

**Hauptablauf:**
1. Das System sendet GET `/api/documents/?tags__name__iexact={TAG}&page_size=100` an Paperless-NGX.
2. Das System identifiziert Dokumente mit dem konfigurierten Tag.
3. Für jedes Dokument: Prüfung ob `paperless_document_id` bereits in `receipt` existiert.
4. Neue Dokumente: Anlegen eines `receipt`-Eintrags mit `raw_text = content`, `parse_status = PENDING`.
5. **Tag-Entfernung prüfen:** Nach vollständigem erfolgreichem Abruf aller Paperless-Seiten werden alle aktiven `receipt`-Einträge, deren `paperless_document_id` nicht mehr im Paperless-Ergebnis vorkommt, per Soft-Delete markiert (Grund `TAG_REMOVED`). Die Entfernung wird in `sync_log_entry` protokolliert und als INFO geloggt.
6. Das System startet den Parsing-Prozess (F-02) für alle neuen Receipts.
7. Nach Parsing und Kategorisierung startet das System die Produktzuordnung (F-19) für alle neuen Positionen.
8. Der Sync-Status und Sync-Log werden aktualisiert.

**Alternativer Ablauf – Paperless-NGX nicht erreichbar:**
- Das System markiert den Sync als fehlgeschlagen, schreibt einen Logeintrag. Kein Datenverlust.

**Nachbedingung:** Alle neuen eBON-Dokumente sind als aktive `receipt` in der DB, neue Receipts haben `parse_status = PARSED` oder `PARSE_ERROR`. Dokumente mit entferntem Tag sind nicht physisch gelöscht, sondern per `deleted_at` ausgeblendet.

---

### UC-02: Sync manuell auslösen

**Akteur:** Nutzer  
**Vorbedingung:** Applikation ist erreichbar.  
**Auslöser:** Nutzer klickt „Jetzt synchronisieren" auf Dashboard oder Sync-Seite.

**Hauptablauf:**
1. Frontend sendet `POST /api/sync/trigger`.
2. Backend startet den Sync asynchron, antwortet sofort mit `202 Accepted`. Wenn bereits ein Sync läuft, antwortet das Backend mit `409 Conflict` und dem aktuellen Sync-Status.
3. Frontend zeigt einen Ladestatus und pollt `GET /api/sync/status` alle 3 Sekunden.
4. Nach Abschluss zeigt die UI: Anzahl neuer Bons, Anzahl Fehler, Zeitstempel.

**Nachbedingung:** Sync wurde ausgeführt. Sync-Status ist aktuell.

---

### UC-03: Bon-Details ansehen

**Akteur:** Nutzer  
**Vorbedingung:** Mindestens ein Bon ist importiert.  
**Auslöser:** Nutzer klickt auf einen Bon in der Bon-Liste oder in den Suchergebnissen.

**Hauptablauf:**
1. Frontend lädt `GET /api/receipts/{id}` und zeigt:
   - Metadaten (Datum, Geschäft, Gesamtbetrag, Import-Datum)
   - Alle Positionen mit Betrag und Kategorie (farbig markiert)
   - Parse-Status und ggf. Fehlermeldung
2. Jede kategorisierte Position zeigt ihren `category_source` als kleines Badge (`RULE`, `AI`, `MANUAL`). Unkategorisierte Positionen (`category_id = NULL`, `category_source = NULL`) werden als „Ohne Kategorie" angezeigt und bleiben in der UI bearbeitbar.
3. Wenn zu einer unkategorisierten Position ein nicht übernommener KI-Vorschlag existiert, zeigt die Detailansicht diesen als Hinweis, z.B. „KI-Vorschlag: Drogerie (82 %) – nicht automatisch übernommen wegen niedriger Konfidenz". Der Nutzer kann den Vorschlag übernehmen oder eine andere Kategorie wählen.

---

### UC-04: Bon-Daten manuell bearbeiten

**Akteur:** Nutzer  
**Vorbedingung:** Bon ist importiert (beliebiger `parse_status`).  
**Auslöser:** Nutzer klickt „Bearbeiten" in der Bon-Detailansicht.

**Hauptablauf:**
1. Die Detailansicht wechselt in den Editiermodus.
2. Nutzer kann Bon-Metadaten und/oder Positionen bearbeiten.
3. Nutzer kann Positionen löschen oder neue hinzufügen.
4. Nutzer klickt „Speichern".
5. Frontend sendet `PUT /api/receipts/{id}` mit dem geänderten Objekt.
6. Backend speichert Änderungen, setzt `is_manually_edited = TRUE` für geänderte Positionen und `parse_status = MANUALLY_EDITED` für den Bon.
7. UI zeigt Erfolgsmeldung und kehrt zur Detailansicht zurück.

**Alternativer Ablauf – Validierungsfehler:**
- Pflichtfelder fehlen oder Betrag ist ungültig → Fehlermeldung inline, kein Speichern.

---

### UC-05: Position kategorisieren (manuell)

**Akteur:** Nutzer  
**Vorbedingung:** Position ist in der DB vorhanden.  
**Auslöser:** Nutzer ändert die Kategorie einer Position in der Bon-Detailansicht.

**Hauptablauf:**
1. Nutzer wählt eine Kategorie aus einem Dropdown.
2. Nutzer klickt „Speichern" (oder hat Autosave aktiv).
3. Frontend sendet `PATCH /api/receipt-items/{id}` mit `{ "categoryId": <id>, "categorySource": "MANUAL" }`.
4. Backend speichert, setzt `is_manually_edited = TRUE`.
5. System fragt: „Soll eine Regel für diese Bezeichnung erstellt werden?" (UC-06).

---

### UC-06: Kategorisierungsregel aus Beispiel erstellen

**Akteur:** Nutzer  
**Vorbedingung:** Nutzer hat eine Position manuell kategorisiert (UC-05) oder navigiert zur Regelverwaltung.  
**Auslöser:** System-Prompt nach UC-05 oder direktes Aufrufen der Regelerstellung.

**Hauptablauf:**
1. System schlägt eine Regel vor, z.B.: `DESCRIPTION CONTAINS "Bio-Milch" → Kategorie "Milchprodukte"` mit Priorität 100.
2. Nutzer kann Match-Typ, Match-Wert und Priorität anpassen.
3. System zeigt Vorschau: „X bestehende Positionen würden von dieser Regel erfasst."
4. Nutzer bestätigt mit „Regel speichern und auf bestehende Daten anwenden" oder „Nur speichern".
5. Backend speichert die Regel in `categorization_rule`.
6. Falls „und auf bestehende Daten anwenden": Backend kategorisiert alle passenden Positionen mit `category_source = RULE` (ohne `is_manually_edited` zu überschreiben).

---

### UC-07: Kategorien verwalten

**Akteur:** Nutzer  
**Vorbedingung:** –  
**Auslöser:** Nutzer navigiert zu „Kategorien" in den Einstellungen.

**Hauptablauf – Neue Kategorie:**
1. Nutzer klickt „Kategorie hinzufügen".
2. Nutzer gibt Name, Farbe (Color Picker) und optionales Icon ein.
3. Frontend sendet `POST /api/categories`.
4. Backend validiert (Name eindeutig, Farbe valides Hex) und speichert.

**Hauptablauf – Kategorie deaktivieren:**
1. Nutzer klickt „Deaktivieren" bei einer Kategorie.
2. System zeigt Hinweis: „Die Kategorie bleibt für bestehende Positionen erhalten, steht aber für neue Zuordnungen nicht mehr zur Auswahl."
3. Nutzer bestätigt.
4. Frontend sendet `PATCH /api/categories/{id}` mit `{ "isActive": false }`.
5. Backend setzt `is_active = FALSE`.

**Alternativer Ablauf – Kategorie physisch löschen:**
- Physisches Löschen über `DELETE /api/categories/{id}` ist nur möglich, wenn keine Positionen und keine aktiven Regeln mehr auf die Kategorie verweisen. Andernfalls antwortet das Backend mit `409 Conflict`.

---

### UC-08: Suche durchführen

**Akteur:** Nutzer  
**Auslöser:** Nutzer gibt Suchkriterien ein und klickt „Suchen".

**Hauptablauf:**
1. Frontend sendet `GET /api/search` mit Query-Parametern: `q` (Text), `store`, `dateFrom`, `dateTo`, `categoryIds`, `amountMin`, `amountMax`, `page`, `size`, `sortBy`, `sortDir`.
2. Backend führt die Suche aus und gibt paginierte Ergebnisse zurück.
3. Ergebnisse zeigen: Bon-Datum, Geschäft, Positions-Beschreibung, Betrag, Kategorie.
4. Nutzer kann direkt aus den Ergebnissen zur Bon-Detailansicht navigieren.

**Nachbedingung:** Suchergebnisse werden angezeigt. Keine Daten werden verändert.

---

### UC-09: Bon re-parsen

**Akteur:** Nutzer  
**Vorbedingung:** Bon ist importiert. `parse_status` ist beliebig.  
**Auslöser:** Nutzer klickt „Erneut parsen" in der Bon-Detailansicht.

**Hauptablauf:**
1. Wenn `is_manually_edited = TRUE` für den Bon oder Positionen: System zeigt Warnung mit Konflikthinweis: „Dieser Bon enthält manuell editierte Positionen. Sollen diese überschrieben werden?".
2. Nutzer muss explizit bestätigen (Checkbox: „Manuell editierte Positionen überschreiben").
3. Ohne Bestätigung: Nur nicht-manuell editierte Positionen werden re-parsed; manuell editierte Positionen bleiben unverändert.
4. Mit Bestätigung: Alle Positionen werden gelöscht und neu geparst; `is_manually_edited` wird zurückgesetzt.
5. Frontend sendet `POST /api/receipts/{id}/reparse` mit Query-Parameter `overwriteManualEdits=true` (nur bei Bestätigung).
6. Backend löscht alle bestehenden `receipt_item` für diesen Bon (respektiert `overwriteManualEdits`-Flag).
7. Backend parst `raw_text` erneut und legt neue Positionen an.
8. Kategorisierung (F-03) wird für alle neuen Positionen erneut ausgeführt.
9. Produktzuordnung (F-19) wird für alle neuen Positionen erneut ausgeführt. Bestätigte alte Zuordnungen werden bestmöglich übertragen, wenn Beschreibung, Preis und Menge plausibel passen; Konflikte werden sichtbar.
10. UI zeigt aktualisierte Bon-Detailansicht.

---

### UC-10: Report erstellen

**Akteur:** Nutzer  
**Auslöser:** Nutzer navigiert zur Report-Seite und wählt Report-Typ und Zeitraum.

**Hauptablauf:**
1. Nutzer wählt: Report-Typ, Zeitraum (von/bis oder Vorauswahl wie „Dieser Monat", „Letztes Quartal"), optional Kategorie- und Geschäftsfilter.
2. Frontend sendet `GET /api/reports/{type}` mit den gewählten Parametern.
3. Backend aggregiert und liefert strukturierte JSON-Daten.
4. Frontend rendert Diagramm (Recharts) und Datentabelle.
5. Nutzer kann „CSV exportieren" klicken → `GET /api/reports/{type}/export?format=csv`.

---

### UC-11: Backup erstellen

**Akteur:** Nutzer  
**Auslöser:** Nutzer klickt „Backup erstellen" auf der Backup-Seite.

**Hauptablauf:**
1. Frontend sendet `GET /api/backup/download`.
2. Backend serialisiert alle Tabellen als JSON, packt sie in ein ZIP.
3. Browser startet automatischen Download der Datei `ebon-backup-YYYY-MM-DD_HH-mm.zip`.

---

### UC-12: Restore aus Backup

**Akteur:** Nutzer  
**Auslöser:** Nutzer lädt eine Backup-ZIP auf der Backup-Seite hoch.

**Hauptablauf:**
1. Nutzer wählt eine Backup-ZIP-Datei und klickt „Restore starten".
2. System zeigt Warnung: „Alle vorhandenen Daten werden überschrieben. Diese Aktion kann nicht rückgängig gemacht werden."
3. Nutzer bestätigt durch Eingabe des Textes „RESTORE" in ein Textfeld.
4. Frontend sendet `POST /api/backup/restore` mit der Datei als Multipart.
5. Backend validiert das ZIP (Manifest, Version, Vollständigkeit).
6. Bei Validierungsfehler: Fehler anzeigen, keine Daten verändern.
7. Backend löscht alle Daten und importiert die Backup-Daten transaktional.
8. UI zeigt Erfolgs- oder Fehlermeldung.

---

### UC-13: Einstellungen konfigurieren

**Akteur:** Nutzer  
**Auslöser:** Nutzer navigiert zu „Einstellungen".

**Hauptablauf:**
1. Frontend lädt `GET /api/settings` und zeigt aktuelle Werte.
2. Nutzer ändert Werte (API-Keys werden maskiert angezeigt, können aber neu eingegeben werden).
3. Nutzer klickt „Verbindung testen" für Paperless-NGX oder OpenRouter → `POST /api/settings/test-connection`.
4. Backend antwortet mit Erfolg oder Fehlermeldung.
5. Nutzer klickt „Speichern" → `PUT /api/settings`.
6. Backend persistiert Änderungen in `app_settings`.

**Datenwartung in den Einstellungen:**
- Nutzer kann „Alle Bons erneut parsen" auslösen. Frontend sendet `POST /api/receipts/reparse` mit `overwriteManualEdits=false`, außer der Nutzer bestätigt explizit das Überschreiben manueller Änderungen.
- Nutzer kann „Importierte Bon-Daten zurücksetzen" auslösen. Vor dem Absenden muss ein eindeutiger Bestätigungstext eingegeben werden. Frontend sendet `POST /api/admin/data-reset/imported-receipts` mit `{ "confirmation": "DELETE_IMPORTED_RECEIPTS" }`.
- Backend führt den Reset transaktional aus und löscht nur importierte Bon-Daten sowie zugehörige Parser-/KI-/Sync-Detaildaten. Kategorien, Regeln, App-Einstellungen, Flyway-Historie und Backups bleiben erhalten.

---

### UC-14: KI-Parsing-Fallback prüfen

**Akteur:** Nutzer
**Vorbedingung:** Bon ist importiert und der Regelparser konnte ihn nicht vollständig parsen oder der Nutzer startet einen Reparse.
**Auslöser:** Automatischer Sync, manueller Reparse oder Bulk-Reparse.

**Hauptablauf:**
1. Backend führt zuerst den Regelparser aus.
2. Wenn der Regelparser `PARSED` liefert, wird kein KI-Parsing-Fallback benötigt und `parse_source = RULE` gesetzt.
3. Wenn der Regelparser `PARSE_ERROR` liefert, prüft das Backend Fallback-Aktivierung, API-Key, Trigger und Sync-Call-Limit.
4. Bei zulässigem Fallback sendet das Backend minimierten oder bestätigten vollständigen Bontext plus Teilparse und Fehlergrund an OpenRouter.
5. Backend validiert die KI-Antwort streng. Bei Erfolg wird der Bon als `PARSED` mit `parse_source = AI` gespeichert und anschließend kategorisiert.
6. Bei Fehler bleibt der Bon `PARSE_ERROR`; `parse_error_message` und `ai_parsing_log` erklären, warum keine Übernahme erfolgte.
7. UI zeigt Badge, KI-Parsing-Status und vorhandene Parser-Regelvorschläge.

**Nachbedingung:** Der Nutzer kann nachvollziehen, ob und warum KI-Parsing geholfen hat oder nicht.

---

### UC-15: Parser-Regelvorschlag prüfen und übernehmen

**Akteur:** Nutzer
**Vorbedingung:** Ein KI-Parsing-Lauf hat mindestens einen offenen `parse_rule_suggestion` erzeugt.
**Auslöser:** Nutzer öffnet den Vorschlag im Bon-Detail oder in den Einstellungen.

**Hauptablauf:**
1. UI zeigt Problem-Beschreibung, Lösungsbegründung, Regex, Extract-Group, Store-Kontext und Validierungsergebnis.
2. Nutzer kann Regex, Regeltyp, Extract-Group und Store-Kontext bearbeiten.
3. Backend validiert die bearbeitete Regel erneut gegen den Beispiel-Bon und prüft offensichtliche Kollisionen.
4. Nutzer akzeptiert den Vorschlag.
5. Backend erzeugt eine aktive `parse_rule` mit `source = AI_ADAPTED`, setzt den Vorschlag auf `ACCEPTED` und verknüpft `accepted_parse_rule_id`.
6. UI fragt, ob kein Reparse, nur der aktuelle Bon, alle `PARSE_ERROR`-Bons desselben Stores oder alle `PARSE_ERROR`-Bons erneut geparst werden sollen.
7. Optional exportiert der Nutzer akzeptierte Regeln als Flyway-Migrationsentwurf.

**Alternative:** Nutzer lehnt den Vorschlag mit Begründung ab. Der Vorschlag bleibt als `REJECTED` gespeichert, damit dieselbe Idee nicht blind erneut vorgeschlagen wird.

---

### UC-16: Produktzuordnung prüfen

**Akteur:** Nutzer

**Vorbedingung:** Mindestens eine `receipt_item` hat `product_assignment_status = NEEDS_REVIEW` oder einen Produktzuordnungskonflikt.

**Auslöser:** Nutzer öffnet den Bereich „Produkte" → „Produktzuordnung prüfen".

**Hauptablauf:**
1. Frontend lädt die Review-Queue mit Filtern für Unsicherheit, Store, Produktfamilie, Kategorie, Zeitraum, Quelle und Status.
2. Backend liefert paginierte Einträge, standardmäßig nach Nutzen sortiert (häufige/teure Positionen zuerst).
3. Nutzer öffnet einen Eintrag und sieht Bon-Datum, Store, Positionsbeschreibung, Menge, Preis, vorgeschlagene Familie/Variante, Konfidenz und Begründung.
4. Nutzer akzeptiert den Vorschlag, korrigiert Familie/Variante, legt neue Stammdaten an, lehnt ab oder markiert die Position als `NO_PRODUCT`.
5. Backend speichert die Entscheidung mit Quelle/Status, aktualisiert die Position und füllt optional eine leere Kategorie über `product_family.default_category_id`.
6. UI aktualisiert die Review-Queue und zeigt eine Erfolgsmeldung.

**Nachbedingung:** Die geprüfte Position ist `CONFIRMED`, `REJECTED` oder `NO_PRODUCT` und bleibt auditierbar.

---

### UC-17: Produktstammdaten und Produktregeln pflegen

**Akteur:** Nutzer

**Vorbedingung:** Produktbereich oder Produktverwaltung ist geöffnet.

**Auslöser:** Nutzer erstellt oder bearbeitet Produktfamilien, Varianten oder Regeln.

**Hauptablauf:**
1. Nutzer legt eine Produktfamilie an oder bearbeitet Name, Aktivstatus und optionale Standard-Kategorie.
2. Nutzer legt Varianten mit Einheit, Menge, Packungsstruktur und optionaler GTIN an.
3. Nutzer erstellt oder bearbeitet Produktregeln gegen Positionsbeschreibungen, optional mit Store-Kontext.
4. Backend berechnet vor dem rückwirkenden Anwenden eine Vorschau betroffener historischer Positionen.
5. Nutzer entscheidet zwischen „nur künftig" und „auf bestehende Positionen anwenden".
6. Backend wendet bestätigte rückwirkende Änderungen transaktional an.

**Korrekturabläufe:**
- Produktfamilien oder Varianten können zusammengeführt werden.
- Falsch zusammengelegte Familien oder Varianten können getrennt werden.
- Einzelne Positionen können auf eine andere Familie/Variante gesetzt oder aus der Produktzuordnung entfernt werden.
- Jede historisch wirkende Änderung zeigt vor Ausführung eine Vorschau und verlangt Bestätigung.

---

### UC-18: Produktpreisvergleich ansehen und exportieren

**Akteur:** Nutzer

**Vorbedingung:** Es existieren Produktzuordnungen mit Preisbeobachtungen.

**Auslöser:** Nutzer öffnet eine Produktfamilie, Produktvariante oder den Produktreport.

**Hauptablauf:**
1. Nutzer wählt Produktfamilie oder Produktvariante, Zeitraum und Store-Gruppierung (`store_name` oder `store_name + store_branch`).
2. Backend berechnet letzten bekannten Preis, historisches Minimum, Durchschnitt, Median, Verlauf und zugrunde liegende Preisbeobachtungen.
3. Familienreports normalisieren auf `€/l`, `€/kg` oder `€/Stück`; Variantenreports zeigen zusätzlich den konkreten Positionspreis.
4. UI zeigt Diagramm, Tabelle und Links zu den zugrunde liegenden Bon-Positionen.
5. Nutzer kann auffällige Beobachtungen ausschließen oder wieder einschließen.
6. Nutzer exportiert die Ansicht als CSV.

**Nachbedingung:** Produktpreise sind nachvollziehbar nach Store, Zeitraum und Einheit vergleichbar. Ausgeschlossene Beobachtungen bleiben sichtbar und reversibel.

---

## 8. API-Spezifikation (Backend)

Alle Endpunkte haben das Präfix `/api`. Alle Responses sind JSON (`Content-Type: application/json`), sofern nicht anders angegeben. HTTP-Statuscodes folgen REST-Konventionen.

### 8.1 Authentifizierung

Alle API-Endpunkte erfordern einen `Authorization`-Header:
- `Authorization: Bearer <APP_API_TOKEN>` (Token aus Umgebungsvariable `APP_API_TOKEN`)

Bei fehlendem oder falschem Token: `401 Unauthorized`.

### 8.2 Gemeinsame Fehlercodes

| HTTP-Status | Bedeutung |
|---|---|
| 400 | Bad Request – Validierungsfehler im Body oder Query-Param |
| 401 | Unauthorized – Token fehlt oder ungültig |
| 404 | Not Found – Ressource nicht gefunden |
| 409 | Conflict – z.B. doppelter Kategoriename |
| 422 | Unprocessable Entity – Semantischer Fehler (z.B. Backup inkompatibel) |
| 500 | Internal Server Error – Unerwarteter Fehler |

Fehlerresponse-Format:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Feld 'name' darf nicht leer sein.",
  "timestamp": "2026-05-26T10:00:00Z",
  "path": "/api/categories"
}
```

### 8.3 Endpunkte

#### Sync

| Methode | Pfad | Beschreibung | Request | Response |
|---|---|---|---|---|
| POST | `/api/sync/trigger` | Sync manuell starten | – | `202 { "message": "Sync gestartet" }` |
| GET | `/api/sync/status` | Sync-Status abrufen | – | `SyncStatusDTO` |
| GET | `/api/sync/log` | Sync-Log abrufen (paginiert) | `page`, `size` | `Page<SyncLogDTO>` |

`SyncStatusDTO`:
```json
{
  "lastSyncAt": "2026-05-26T09:00:00Z",
  "lastSyncStatus": "SUCCESS",
  "newDocumentsCount": 3,
  "removedDocumentsCount": 0,
  "errorCount": 0,
  "isSyncing": false
}
```

#### Receipts

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/receipts` | Liste aller Bons (paginiert, filterbar nach `status`, `dateFrom`, `dateTo`, `store`) |
| GET | `/api/receipts/{id}` | Bon-Details inkl. Positionen |
| GET | `/api/receipts/{id}/paperless-raw-text-status` | Statusvergleich zwischen gespeichertem und aktuellem Paperless-Rohtext für einen Einzel-Reparse |
| PUT | `/api/receipts/{id}` | Bon-Metadaten und Positionen aktualisieren |
| POST | `/api/receipts/{id}/reparse` | Bon erneut parsen; optional `rawTextSource=STORED|PAPERLESS`, Default `STORED` |
| POST | `/api/receipts/reparse` | Alle Bons erneut parsen, optional mit `overwriteManualEdits=false` |
| DELETE | `/api/receipts/{id}` | Bon und Positionen löschen |

Query-Parameter für `GET /api/receipts`:
- `page` (default 0), `size` (default 20), `sortBy` (default `receiptDate`), `sortDir` (`asc`/`desc`)
- `status` (`PENDING`, `PARSED`, `PARSE_ERROR`, `MANUALLY_EDITED`)
- `dateFrom` (ISO 8601 Date), `dateTo` (ISO 8601 Date)
- `store` (partial match)
- `includeDeleted` (default `false`; nur für administrative Ansichten)
- `uncategorizedOnly` (default `false`; zeigt nur Bons mit mindestens einer Position, bei der `category_id = NULL` und `category_source = NULL`)

`GET /api/receipts/{id}/paperless-raw-text-status` antwortet mit `status = UNCHANGED`, `CHANGED` oder `UNAVAILABLE`. Die Antwort enthält keinen Rohtext, keine Prüfsumme, keine Zugangsdaten und keine Details eines Paperless-Fehlers.

#### Receipt Items

| Methode | Pfad | Beschreibung |
|---|---|---|
| PATCH | `/api/receipt-items/{id}` | Einzelne Position aktualisieren |
| DELETE | `/api/receipt-items/{id}` | Position löschen |
| POST | `/api/receipts/{id}/items` | Neue Position zu Bon hinzufügen |

#### Kategorien

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/categories` | Alle Kategorien (inkl. inaktiver, wenn `includeInactive=true`) |
| POST | `/api/categories` | Neue Kategorie anlegen |
| PUT | `/api/categories/{id}` | Kategorie aktualisieren |
| PATCH | `/api/categories/{id}` | Kategorie teilweise aktualisieren, z.B. aktivieren/deaktivieren |
| DELETE | `/api/categories/{id}` | Kategorie physisch löschen, nur wenn unreferenziert (s. UC-07) |

#### Kategorisierungsregeln

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/categorization-rules` | Alle Regeln (sortiert nach Priorität) |
| POST | `/api/categorization-rules` | Neue Regel anlegen |
| PUT | `/api/categorization-rules/{id}` | Regel aktualisieren |
| DELETE | `/api/categorization-rules/{id}` | Regel löschen |
| POST | `/api/categorization-rules/{id}/apply` | Regel auf bestehende Daten anwenden |
| POST | `/api/categorization-rules/preview` | Vorschau: Wie viele Positionen betroffen |

#### Suche

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/search` | Suche über Bons und Positionen |

Query-Parameter: `q`, `store`, `dateFrom`, `dateTo`, `categoryIds` (kommagetrennt), `productFamilyId`, `productVariantId`, `uncategorizedOnly`, `amountMin`, `amountMax`, `page`, `size`, `sortBy`, `sortDir`.

#### Reports

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/reports/by-category` | Ausgaben gruppiert nach Kategorie |
| GET | `/api/reports/by-period` | Ausgaben gruppiert nach Zeitraum |
| GET | `/api/reports/by-store` | Ausgaben gruppiert nach Geschäft |
| GET | `/api/reports/top-items` | Häufigste/teuerste Positionen |
| GET | `/api/reports/bonus` | Neu gesammeltes Bonusguthaben und neu gesammelte Punkte aggregiert |
| GET | `/api/reports/top-products` | Häufige/teure Produktfamilien, `sortBy=total` oder `sortBy=count` |
| GET | `/api/reports/by-category/export` | CSV-Export |
| GET | `/api/reports/by-period/export` | CSV-Export |
| GET | `/api/reports/by-store/export` | CSV-Export |
| GET | `/api/reports/top-products/export` | Top-Produktfamilien als CSV-Export |

Gemeinsame Query-Parameter: `dateFrom`, `dateTo`, `categoryIds`, `productFamilyId`, `productVariantId`, `store`, `groupBy` (`day`/`week`/`month`/`year`). Die Produktpreisendpunkte unterstützen zusätzlich `grouping` (`STORE` oder `STORE_BRANCH`), `includeExcluded` (Default `true`, betrifft nur die Detailtabelle) sowie `page` und `size` für Preisbeobachtungen. Ausgeschlossene Positionen fließen unabhängig von `includeExcluded` nie in Kennzahlen ein.

#### Backup & Restore

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/backup/download` | Backup als ZIP herunterladen |
| POST | `/api/backup/restore` | Restore aus hochgeladener ZIP (Multipart) |
| POST | `/api/backup/validate` | Dry-Run-Validierung einer Backup-ZIP (Multipart) |

#### Einstellungen

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/settings` | Aktuelle Einstellungen (Keys maskiert) |
| PUT | `/api/settings` | Einstellungen speichern |
| POST | `/api/settings/test-connection` | Verbindung zu Paperless oder OpenRouter testen |

#### Datenwartung

| Methode | Pfad | Beschreibung |
|---|---|---|
| POST | `/api/admin/data-reset/imported-receipts` | Importierte Bon-Daten löschen, Kategorien/Regeln/Einstellungen behalten |
| POST | `/api/admin/data-reset/products` | Produktstammdaten, Produktregeln, Zuordnungen und Preis-Ausschlüsse separat zurücksetzen |
| POST | `/api/admin/reparse/bulk` | Bulk-Reparse für ausgewählte Bons, gleiche Stores oder alle `PARSE_ERROR`-Bons |

Body für `/api/admin/data-reset/imported-receipts`:
```json
{ "confirmation": "DELETE_IMPORTED_RECEIPTS" }
```

Body für `/api/admin/data-reset/products`:
```json
{ "confirmation": "DELETE_PRODUCT_DATA" }
```

Body für `/api/admin/reparse/bulk`:
```json
{
  "scope": "PARSE_ERROR_BY_STORE",
  "storeName": "REWE",
  "useAiFallback": true,
  "aiTextMode": "MINIMIZED",
  "confirmation": "REPARSE_RECEIPTS"
}
```

#### KI-Parsing und Parser-Regelvorschläge

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/receipts/{id}/ai-parsing-log` | KI-Parsing-Logs für einen Bon abrufen |
| GET | `/api/parser/rule-suggestions` | Parser-Regelvorschläge paginiert abrufen |
| GET | `/api/parser/rule-suggestions/{id}` | Parser-Regelvorschlag im Detail abrufen |
| PUT | `/api/parser/rule-suggestions/{id}` | Parser-Regelvorschlag vor Annahme bearbeiten |
| POST | `/api/parser/rule-suggestions/{id}/accept` | Vorschlag als aktive `parse_rule` übernehmen |
| POST | `/api/parser/rule-suggestions/{id}/reject` | Vorschlag mit Begründung ablehnen |
| POST | `/api/parser/rule-suggestions/export-migration` | Aus akzeptierten Parser-Regeln einen Flyway-Migrationsentwurf exportieren |
| POST | `/api/parser/fixtures/preview` | Anonymisierte Fixture-Vorschau aus KI-Parsing-Log erzeugen |
| POST | `/api/parser/fixtures/export` | Anonymisiertes Fixture lokal außerhalb des Test-Corpus exportieren |

#### Produkte

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/products/families` | Produktfamilien abrufen |
| POST | `/api/products/families` | Produktfamilie anlegen |
| PUT | `/api/products/families/{id}` | Produktfamilie aktualisieren |
| POST | `/api/products/families/merge/preview` | Zusammenführung von Familien unveränderlich vorschauen |
| POST | `/api/products/families/merge/apply` | Zusammenführung nach `confirm = true` transaktional anwenden |
| POST | `/api/products/families/split/preview` | Trennung ausgewählter familienweiter Positionen unveränderlich vorschauen |
| POST | `/api/products/families/split/apply` | Trennung nach `confirm = true` transaktional anwenden |
| GET | `/api/products/variants` | Produktvarianten abrufen, optional nach `productFamilyId` filtern |
| POST | `/api/products/variants` | Produktvariante anlegen |
| PUT | `/api/products/variants/{id}` | Produktvariante aktualisieren |
| POST | `/api/products/variants/merge/preview` | Zusammenführung von Varianten derselben Familie unveränderlich vorschauen |
| POST | `/api/products/variants/merge/apply` | Zusammenführung nach `confirm = true` transaktional anwenden und Quelle deaktivieren |
| POST | `/api/products/variants/split/preview` | Trennung ausgewählter Variantenpositionen unveränderlich vorschauen |
| POST | `/api/products/variants/split/apply` | Trennung nach `confirm = true` transaktional anwenden |
| GET | `/api/products/rules` | Produktregeln abrufen |
| POST | `/api/products/rules` | Produktregel anlegen |
| PUT | `/api/products/rules/{id}` | Produktregel aktualisieren |
| DELETE | `/api/products/rules/{id}` | Produktregel löschen |
| POST | `/api/products/rules/preview` | Vorschau betroffener Positionen berechnen |
| POST | `/api/products/rules/{id}/apply` | Produktregel auf bestehende Positionen anwenden |
| GET | `/api/products/review` | Produkt-Review-Queue paginiert abrufen |
| POST | `/api/products/review/{receiptItemId}/accept` | Produktvorschlag akzeptieren |
| POST | `/api/products/review/{receiptItemId}/correct` | Produktzuordnung korrigieren |
| POST | `/api/products/review/{receiptItemId}/reject` | Produktvorschlag ablehnen |
| POST | `/api/products/review/{receiptItemId}/no-product` | Position als `NO_PRODUCT` markieren |
| DELETE | `/api/products/review/{receiptItemId}/assignment` | Produktzuordnung entfernen |
| POST | `/api/products/review/{receiptItemId}/rule-suggestion` | Regelvorschlag aus manueller Zuordnung mit Vorschau erzeugen |
| POST | `/api/products/review/{receiptItemId}/rule-suggestion/accept` | Vorschlag nach `confirm = true` als Produktregel speichern und optional anwenden |
| POST | `/api/products/assignments/run` | Produktzuordnung für Bon oder offene Positionen neu ausführen |
| GET | `/api/products/families/{productFamilyId}/prices` | Produktfamilien-Preisvergleich über normalisierte Einheitenpreise |
| GET | `/api/products/families/{productFamilyId}/price-observations` | Paginierte Preisbeobachtungen der Produktfamilie |
| GET | `/api/products/families/{productFamilyId}/prices/export` | Produktfamilien-Preisvergleich als CSV |
| GET | `/api/products/variants/{productVariantId}/prices` | Produktvarianten-Preisvergleich über effektive Positionspreise |
| GET | `/api/products/variants/{productVariantId}/price-observations` | Paginierte Preisbeobachtungen der Produktvariante |
| GET | `/api/products/variants/{productVariantId}/prices/export` | Produktvarianten-Preisvergleich als CSV |
| POST | `/api/products/price-observations/{receiptItemId}/exclude` | Preisbeobachtung ausschließen |
| POST | `/api/products/price-observations/{receiptItemId}/include` | Preisbeobachtung wieder einschließen |

Für jede historisch wirkende Zusammenführung, Trennung oder Regelanwendung gilt: Der Client ruft zuerst den `preview`-Endpunkt auf und sendet danach einen separaten `apply`-Request mit `confirm = true`. Familien-Splits dürfen nur ausgewählte, aktuell familienweite Positionen verschieben. Varianten-Splits dürfen nur ausgewählte Positionen der Quellvariante verschieben. Bestehende Produktregeln werden beim Split nicht automatisch kopiert; bei Merge werden Variantenregeln auf die Zielvariante umgehängt. Die Quelle eines Merge wird nur deaktiviert, damit Audit-Logs ihren historischen Bezug behalten.

Body für `/api/settings/test-connection`:
```json
{ "target": "PAPERLESS" }
```
oder
```json
{ "target": "OPENROUTER" }
```

#### Dashboard

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/api/dashboard` | Aggregierte Kennzahlen für Dashboard |

Response `DashboardDTO`:
```json
{
  "currentMonthTotal": 312.45,
  "previousMonthTotal": 287.12,
  "currentYearTotal": 2480.95,
  "currentMonthByCategory": [
    { "categoryId": 1, "categoryName": "Lebensmittel", "total": 198.30 }
  ],
  "bonusSummary": [
    { "bonusType": "Payback", "totalPoints": 1250, "totalEarnedBalance": 45.90 }
  ],
  "recentReceipts": [ /* letzte 5 Receipts */ ],
  "uncategorizedItemsCount": 12,
  "lastSyncStatus": { /* SyncStatusDTO */ }
}
```

## 8.4 DTO- und Validierungsregeln

Die Implementierung muss explizite Request-/Response-DTOs verwenden. JPA-Entities werden nicht direkt über die API serialisiert.

### Gemeinsames Pagination-Format

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 125,
  "totalPages": 7,
  "sortBy": "receiptDate",
  "sortDir": "desc"
}
```

### ReceiptDTO

```json
{
  "id": 1,
  "paperlessDocumentId": 42,
  "paperlessDocumentUrl": "http://paperless.example/documents/42/details",
  "importedAt": "2026-05-26T10:00:00Z",
  "receiptDate": "2026-05-26",
  "receiptTime": "10:00:00",
  "storeName": "REWE",
  "storeBranch": "Berlin Mitte",
  "totalAmount": 42.15,
  "currency": "EUR",
  "bonusBalance": 12.5,
  "bonusPoints": 25.0,
  "bonusType": "Payback",
  "parseStatus": "PARSED",
  "parseSource": "AI",
  "parseErrorMessage": null,
  "aiParsingSummary": {
    "lastStatus": "SUCCESS",
    "lastTrigger": "MANUAL_REPARSE",
    "modelUsed": "google/gemini-flash-1.5",
    "overallConfidence": 0.940,
    "hasOpenRuleSuggestions": true
  },
  "deletedAt": null,
  "deleteReason": null,
  "rawText": "Roher Paperless-Text, nur in Detailantworten befüllt",
  "items": []
}
```

Validierung:

- `receiptDate`: ISO-8601-Date oder `null`
- `receiptTime`: ISO-8601-Time oder `null`
- `paperlessDocumentUrl`: optionaler, klickbarer Link zur Paperless-Weboberfläche; wird aus Paperless-Web-URL oder Dokument-URL-Vorlage gebaut und enthält keine Secrets
- `storeName`: maximal 255 Zeichen
- `totalAmount`: `>= 0`, maximal 2 Nachkommastellen
- `currency`: ISO-4217-Code, Standard `EUR`
- `bonusBalance`: neu in diesem Einkauf gesammeltes Bonusguthaben oder `null`, nicht aktueller Bonuskonto-/Punktestand
- `bonusPoints`: neu in diesem Einkauf gesammelte Punkte oder `null`
- `parseSource`: `RULE`, `AI`, `MANUAL_CORRECTED` oder `null`; `AI` bedeutet, dass das aktuell gespeicherte Parsergebnis vom OpenRouter-KI-Fallback übernommen wurde
- `aiParsingSummary`: optionaler letzter KI-Parsing-Status für UI-Badge und Detailanzeige; enthält keine Prompt- oder Rohantwortdaten
- `rawText`: roher Paperless-Text; nur in Detail- und Mutationsantworten (`GET/PUT/POST /api/receipts/{id}`) befüllt, in Listen- und Dashboard-Antworten `null`

### ReceiptItemDTO

```json
{
  "id": 10,
  "receiptId": 1,
  "positionIndex": 0,
  "description": "Bio Milch",
  "quantity": 1.0,
  "unit": "Stk",
  "normalizedUnitPrice": 1.49,
  "totalPrice": 1.49,
  "discountAmount": 0.0,
  "categoryId": 2,
  "categoryName": "Lebensmittel",
  "categorySource": "RULE",
  "productFamilyId": 20,
  "productFamilyName": "Coca Cola Zero",
  "productVariantId": 31,
  "productVariantName": "Coca Cola Zero 0,33l Flasche",
  "productAssignmentSource": "RULE",
  "productAssignmentStatus": "AUTO_ASSIGNED",
  "productAssignmentConfidence": 0.980,
  "computedUnitPrice": 1.52,
  "computedUnit": "l",
  "excludeFromProductPriceComparison": false,
  "productPriceExclusionReason": null,
  "isManuallyEdited": false,
  "aiSuggestion": {
    "categoryId": 3,
    "categoryName": "Drogerie",
    "confidence": 0.820,
    "rejectionReason": "LOW_CONFIDENCE"
  }
}
```

Validierung:

- `description`: Pflichtfeld, 1-512 Zeichen
- `totalPrice`: Pflichtfeld, maximal 2 Nachkommastellen
- `quantity`: optional, `> 0`
- `categorySource`: `RULE`, `AI`, `MANUAL` oder `null`; `AI` nur zusammen mit gesetzter `categoryId`, `null` bedeutet „Ohne Kategorie"
- `aiSuggestion`: optionaler letzter nicht übernommener KI-Vorschlag für diese Position; nur anzeigen, wenn `categoryId = NULL`. `rejectionReason`: `LOW_CONFIDENCE`, `UNKNOWN_CATEGORY`, `INVALID_RESPONSE` oder `null`.
- `productAssignmentSource`: `RULE`, `AI`, `MANUAL`, `HISTORY` oder `null`; nur zusammen mit Produktzuordnung oder `NO_PRODUCT`-Status.
- `productAssignmentStatus`: `CONFIRMED`, `AUTO_ASSIGNED`, `NEEDS_REVIEW`, `REJECTED`, `NO_PRODUCT` oder `null`.
- `computedUnitPrice`: aus effektiv gezahltem Preis und normalisierter Menge berechneter Einheitenpreis; `null`, wenn Einheit unbekannt oder nicht vergleichbar ist.
- `excludeFromProductPriceComparison`: ausgeschlossene Beobachtungen bleiben sichtbar, fließen aber nicht in Produktpreisvergleichszahlen ein.

### SearchResultDTO

```json
{
  "receiptId": 1,
  "receiptItemId": 10,
  "receiptDate": "2026-05-26",
  "storeName": "REWE",
  "description": "Bio Milch",
  "totalPrice": 1.49,
  "categoryId": 2,
  "categoryName": "Lebensmittel",
  "productFamilyId": 20,
  "productFamilyName": "Bio Milch",
  "productVariantId": 33,
  "productVariantName": "Bio Milch 1l",
  "productAssignmentSource": "MANUAL",
  "productAssignmentStatus": "CONFIRMED",
  "unitPrice": 1.49,
  "normalizedUnit": "l",
  "highlights": ["Bio Milch"]
}
```

### CategoryDTO

```json
{
  "id": 2,
  "name": "Lebensmittel",
  "colorHex": "#4CAF50",
  "icon": "shopping-basket",
  "isActive": true,
  "sortOrder": 10,
  "assignedItemsCount": 123
}
```

### SettingsDTO

```json
{
  "paperlessBaseUrl": "http://paperless:8000",
  "paperlessPublicBaseUrl": "http://paperless.local:8000",
  "paperlessDocumentUrlTemplate": "http://paperless.local:8000/documents/{paperlessDocumentId}/details",
  "paperlessApiToken": "********",
  "paperlessEbonTag": "eBON",
  "openRouterApiKey": "********",
  "openRouterModel": "google/gemini-flash-1.5",
  "aiCategorizationMinConfidence": 0.900,
  "aiParsingFallbackEnabled": true,
  "aiParsingModel": "google/gemini-flash-1.5",
  "aiParsingMaxTokens": 2500,
  "aiParsingTemperature": 0.0,
  "aiParsingMinConfidence": 0.900,
  "aiParsingSyncCallLimit": 25,
  "aiParsingTextMode": "MINIMIZED",
  "aiParsingStoreDebugSnippets": false,
  "productHistoryMinConfirmedMatches": 3,
  "productHistoryMinVariantShare": 0.900,
  "syncIntervalMinutes": 60,
  "currency": "EUR"
}
```

Beim Speichern bedeuten fehlende Secret-Felder „unverändert lassen". Der Wert `"********"` darf nie persistiert werden.

Validierung:

- `aiParsingMinConfidence`: `0.000` bis `1.000`, Default `0.900`
- `aiParsingSyncCallLimit`: Integer `>= 0`, Default `25`; `0` bedeutet kein automatischer KI-Parsing-Fallback im Sync
- `aiParsingTextMode`: `MINIMIZED` oder `FULL_TEXT`; `FULL_TEXT` darf in manuellen Reparse-Flows nur nach ausdrücklicher Bestätigung verwendet werden
- `aiParsingStoreDebugSnippets`: nur für lokale Entwicklung; bei `true` dürfen gekürzte/maskierte Prompt-/Antwort-Snippets gespeichert werden, niemals vollständige Rohdaten oder Secrets
- `productHistoryMinConfirmedMatches`: Integer `>= 1`, Default `3`
- `productHistoryMinVariantShare`: `0.000` bis `1.000`, Default `0.900`

### AiParsingLogDTO

```json
{
  "id": 1,
  "receiptId": 42,
  "trigger": "MANUAL_REPARSE",
  "status": "SUCCESS",
  "modelUsed": "google/gemini-flash-1.5",
  "startedAt": "2026-06-16T10:00:00Z",
  "finishedAt": "2026-06-16T10:00:03Z",
  "durationMs": 3120,
  "overallConfidence": 0.940,
  "parseErrorBefore": "total_amount fehlt.",
  "failureReason": null,
  "fieldConfidence": { "receiptDate": 0.98, "items": 0.93 },
  "warnings": ["storeBranch unsicher"],
  "promptSnippet": null,
  "responseSnippet": null
}
```

### ParseRuleSuggestionDTO

```json
{
  "id": 10,
  "receiptId": 42,
  "aiParsingLogId": 1,
  "storeName": "McDonald's",
  "ruleType": "ITEM_PATTERN",
  "matchRegex": "^(?<quantity>\\d+)\\s+(?<description>.+?)\\s+(?<total>\\d+\\.\\d{2})$",
  "extractGroup": "quantity,description,total",
  "confidence": 0.920,
  "trigger": "MANUAL_REPARSE",
  "problemDescription": "Regelparser erkannte Punkt-Dezimalpositionen nicht.",
  "solutionRationale": "Die Regel extrahiert Menge, Beschreibung und Betrag aus der McDonald's-Artikeltabelle.",
  "validationStatus": "VALID",
  "validationMessage": null,
  "status": "OPEN",
  "rejectionReason": null,
  "acceptedParseRuleId": null
}
```

Beim Akzeptieren eines Vorschlags muss die UI eine Reparse-Auswahl anbieten: aktueller Bon, gleicher Store, alle `PARSE_ERROR`-Bons oder keine sofortige Anwendung.

### ProductFamilyDTO

```json
{
  "id": 20,
  "name": "Coca Cola Zero",
  "defaultCategoryId": 4,
  "defaultCategoryName": "Getränke",
  "isActive": true,
  "variantCount": 3,
  "assignedItemsCount": 42
}
```

### ProductVariantDTO

```json
{
  "id": 31,
  "productFamilyId": 20,
  "productFamilyName": "Coca Cola Zero",
  "name": "Coca Cola Zero 0,33l Flasche",
  "unitQuantity": 0.33,
  "unit": "l",
  "packageQuantity": 1,
  "packageDescription": "Flasche",
  "totalQuantity": 0.33,
  "gtin": null,
  "isActive": true,
  "assignedItemsCount": 18
}
```

Validierung:

- `unit` kann bekannte Einheiten (`ml`, `l`, `g`, `kg`, `piece`) oder eine freie Einheit enthalten.
- `totalQuantity` muss zur Packungsstruktur passen, wenn `unitQuantity` und `packageQuantity` gesetzt sind.
- Unterschiedliche Größen, Einheiten oder Packungsstrukturen dürfen nicht automatisch zusammengeführt werden.

### ProductReviewItemDTO

```json
{
  "receiptItemId": 10,
  "receiptId": 1,
  "receiptDate": "2026-05-26",
  "storeName": "REWE",
  "storeBranch": "Berlin Mitte",
  "description": "CC Zero",
  "quantity": 1.0,
  "unit": "Stk",
  "totalPrice": 0.99,
  "suggestedProductFamilyId": 20,
  "suggestedProductFamilyName": "Coca Cola Zero",
  "suggestedProductVariantId": 31,
  "suggestedProductVariantName": "Coca Cola Zero 0,33l Flasche",
  "confidence": 0.870,
  "source": "AI",
  "status": "NEEDS_REVIEW",
  "reason": "Beschreibung passt, Größe unsicher",
  "affectedHistoricalItemsCount": 12
}
```

### ProductPriceReportDTO

```json
{
  "productFamilyId": 20,
  "productVariantId": 31,
  "name": "Coca Cola Zero 0,33l Flasche",
  "storeGrouping": "STORE",
  "normalizedUnit": "l",
  "stores": [
    {
      "storeName": "REWE",
      "storeBranch": null,
      "lastPrice": 0.99,
      "lastUnitPrice": 3.00,
      "historicalMinimum": 0.79,
      "historicalMinimumUnitPrice": 2.39,
      "averageUnitPrice": 2.81,
      "medianUnitPrice": 2.75,
      "observationsCount": 8
    }
  ],
  "observations": []
}
```

---

## 9. UI-Spezifikation (Frontend)

### 9.1 Navigation

Die App hat eine linke Seitenleiste (Desktop) / untere Tab-Bar (Mobile) mit folgenden Einträgen:
- Dashboard
- Bons (Bon-Liste)
- Suche
- Reports
- Produkte
- Einstellungen (Untermenü: Kategorien, Regeln, Allgemein, Backup)

### 9.2 Seiten & Komponenten

#### 9.2.1 Dashboard

- KPI-Cards: Ausgaben aktueller Monat, Ausgaben Vormonat, Ausgaben aktuelles Jahr, Delta zu Vormonat (Pfeil + Prozent), Anzahl Bons, unkategorisierte Positionen, neu gesammeltes Bonusguthaben.
- Tortendiagramm: Ausgaben nach Kategorie mit Zeitraumwahl: aktueller Monat, letztes Quartal, letztes Jahr, benutzerdefiniert von/bis.
- Bonus-Karte: Neu gesammelte Punkte/Guthaben nach Bonustyp mit derselben Zeitraumwahl; die Anzeige meint neu gesammelte Werte im Zeitraum, nicht den aktuellen Bonuskonto-/Punktestand.
- Tabelle: Letzte 5 Bons mit Datum, Geschäft, Betrag, Status-Badge, Bonus-Info. Diese Tabelle ist eine Schnellnavigation zu den neuesten Bons.
- Klick auf „Ohne Kategorie" öffnet die Bon-/Suchliste mit Filter auf Positionen ohne Kategorie (`category_id = NULL`, `category_source = NULL`).
- Sync-Status-Banner (grün/gelb/rot) inkl. TAG_REMOVED-Zähler.

#### 9.2.2 Bon-Liste

- Tabelle mit Spalten: Datum, Geschäft, Betrag, Anzahl Positionen, Status, Import-Datum.
- Import-Datum zeigt Datum und Uhrzeit lesbar an; wenn die Spalte schmal ist, steht die Uhrzeit in einer zweiten Zeile unter dem Datum.
- Filter: Status, Geschäft (Freitext), Zeitraum (Datepicker).
- Sortierung per Klick auf Spaltenköpfe.
- Klick auf Zeile → Bon-Detailansicht.
- Sync-Button oben rechts.

#### 9.2.3 Bon-Detailansicht

- Header: Geschäft, Datum/Uhrzeit, Gesamtbetrag, Bonus-Info (Typ + in diesem Einkauf gesammeltes Guthaben/Punkte), Parse-Status-Badge, Buttons „Bearbeiten" / „Erneut parsen" / „Löschen".
- Paperless-Dokument-ID wird als Link zur Paperless-Weboberfläche angezeigt, wenn `paperlessDocumentUrl` vorhanden ist; andernfalls bleibt sie als reine ID sichtbar.
- Wenn `parseSource = AI`, zeigt die Detailansicht ein Badge „per KI geparst". Das Badge blockiert den Workflow nicht, macht die Datenherkunft aber dauerhaft sichtbar.
- Parse-Fehler zeigen, ob der KI-Parsing-Fallback versucht wurde, deaktiviert war, wegen fehlendem API-Key nicht verfügbar war, wegen Sync-Limit übersprungen wurde, invalides JSON geliefert hat oder wegen niedriger Konfidenz nicht übernommen wurde.
- KI-Parsing-Logs sind kompakt sichtbar (Trigger, Status, Modell, Zeitpunkt, Dauer, Grund) und technisch aufklappbar (Warnungen, Feld-Konfidenzen, Fehlgrund). Prompt- und Rohantwortdaten werden im Normalbetrieb nicht angezeigt.
- Bonbezogene Parser-Regelvorschläge werden im Bon-Detail angezeigt. Für jeden Vorschlag muss sichtbar sein, welches Problem ihn ausgelöst hat und warum die Regel dieses Problem lösen soll.
- Positionstabelle: Beschreibung, Menge, Einheit, Einzelpreis, Gesamtpreis, Rabatt, Kategorie (Chip mit Farbe), Quelle-Badge, Produktfamilie, Produktvariante, Produktzuordnungsquelle/-status und berechneter Einheitenpreis.
- Produktzuordnungen sind kompakt korrigierbar. Unsichere Zuordnungen zeigen `NEEDS_REVIEW`; `NO_PRODUCT` wird als eigener Status dargestellt und nicht mit Kategorien verwechselt.
- Im Editiermodus: Inline-Editierung aller Felder, Dropdown für Kategorie, Buttons „Position löschen" / „Position hinzufügen". Speichern/Abbrechen bleibt beim Scrollen sichtbar und klickbar (z.B. Sticky Action-Bar), damit lange Bons nicht nach oben zurückscrollen müssen.
- Rohtextansicht (ausklappbar): `raw_text` in Monospace-Font.
- Einzel-Reparse: Vor dem Start prüft die UI den Paperless-Rohtext. Bei `CHANGED` erscheint ein klarer Bestätigungsdialog mit den Optionen „Neuen Rohtext übernehmen und parsen“, „Gespeicherten Rohtext verwenden“ und Abbrechen. Bei `UNAVAILABLE` erklärt die UI die fehlende Paperless-Verbindung und bietet nur den gespeicherten Rohtext oder Abbrechen an. Bulk-Reparse erhält keinen solchen Dialog.

#### 9.2.4 Suche

- Suchleiste (Freitext) + erweiterbare Filteroptionen (Seitenleiste oder Accordion), inkl. Produktfamilie und Produktvariante.
- Ergebnisliste mit Hervorhebung der Trefferwörter.
- Ergebnisliste zeigt, soweit vorhanden, Produktfamilie, Variante, Einheitenpreis und Produktzuordnungsstatus.
- Paginierung unten.

#### 9.2.5 Reports

- Tab-Auswahl: Kategorie / Zeitraum / Geschäft / Top-Artikel / Bonus / Produkte.
- Filterleiste: Zeitraumauswahl (Schnellauswahl + Datepicker), Kategorie-Mehrfachauswahl, Produktfamilie und Produktvariante.
- Diagramm (Recharts: Bar oder Pie) + Datentabelle.
- CSV-Export-Button.

#### 9.2.6 Einstellungen – Allgemein

- Formular mit Feldern gemäß F-11.1.
- „Verbindung testen"-Buttons direkt neben Paperless-NGX und OpenRouter URL-Feldern.
- Eigener Bereich für KI-Parsing-Fallback: globaler Aktiv-Schalter, Parsing-Modell, Max Tokens, Temperature, Mindest-Konfidenz, Sync-Call-Limit, Textmodus (`MINIMIZED`/`FULL_TEXT`) und lokale Debug-Snippets.
- `FULL_TEXT` muss als datenschutzrelevante Option erklärt werden. Manuelle Reparse-Flows mit `FULL_TEXT` verlangen eine zusätzliche Bestätigung.
- Bereich „Datenwartung" mit Re-Parse aller Bons und Reset aller importierten Bon-Daten. Der Reset benötigt eine deutliche Bestätigung und darf Kategorien, Regeln und Einstellungen nicht löschen.
- Produkt-Historie-Defaults (`productHistoryMinConfirmedMatches`, `productHistoryMinVariantShare`) sind administrativ konfigurierbar.
- Ein separater Produktdaten-Reset ist sichtbar vom Reset importierter Bon-Daten getrennt und benötigt eine eigene deutliche Bestätigung.

#### 9.2.7 Einstellungen – Kategorien

- Liste aller Kategorien mit Color-Badge, Name, Anzahl zugewiesener Positionen.
- Buttons: Neu, Bearbeiten, Deaktivieren/Aktivieren. Ein zusätzlicher Löschen-Button ist nur sichtbar, wenn die Kategorie unreferenziert ist.
- Bearbeiten: Inline-Formular mit Color-Picker und Name.

#### 9.2.8 Einstellungen – Regeln

- Tabelle: Priorität, Match-Feld, Match-Typ, Match-Wert, Ziel-Kategorie, Aktiv.
- Inline-Editierung, Drag-and-Drop zur Priorisierung (ändert `priority`-Wert).
- „Regel anwenden"-Button pro Zeile.

#### 9.2.9 Einstellungen – Parser-Regelvorschläge

- Zentrale Liste aller `parse_rule_suggestion`-Einträge mit Filter nach Status, Store, Regeltyp und Validierungsstatus.
- Detailansicht zeigt Regex, Extract-Group, Beispielkontext, Problem-Beschreibung, Lösungsbegründung und Validierungsergebnis.
- Offene Vorschläge können bearbeitet, akzeptiert oder mit Begründung abgelehnt werden.
- Beim Akzeptieren wird eine aktive `parse_rule` erzeugt. Danach kann der Nutzer wählen, ob kein Reparse, nur der aktuelle Bon, alle `PARSE_ERROR`-Bons desselben Stores oder alle `PARSE_ERROR`-Bons erneut geparst werden.
- Akzeptierte Parser-Regeln können als Flyway-Migrationsentwurf exportiert werden. Der Export erzeugt keinen automatischen Commit und schreibt nicht ohne Nutzerentscheidung in das Repository.
- Für Entwickler kann aus einem KI-Parsing-Log eine anonymisierte Fixture-Vorschau erzeugt werden. Der Export erfolgt lokal außerhalb `backend/src/test/resources/corpus/`; ein Fixture gelangt nur nach manueller Prüfung bewusst ins Repository.

#### 9.2.10 Einstellungen – Backup

- Bereich „Backup erstellen": Button „Backup jetzt herunterladen" + Information über letztes Backup.
- Bereich „Restore": File-Upload-Feld + Warntextblock + Bestätigungseingabe + „Restore starten"-Button.

#### 9.2.11 Produkte – Review, Pflege und Preisvergleich

- Hauptbereich „Produkte" enthält Tabs oder gleichwertige Navigation für:
  - Produktzuordnung prüfen
  - Produktfamilien
  - Produktvarianten
  - Produktregeln
  - Preisvergleich
- „Produktzuordnung prüfen" ist eine dichte, filterbare Tabelle. Standard-Sortierung: häufige/teure unsichere Positionen zuerst. Filter: Unsicherheit, Store, Produktfamilie, Kategorie, Zeitraum, Quelle, Status.
- Jeder Review-Eintrag zeigt Bon-Datum, Store/Filiale, Positionsbeschreibung, Menge, Einheit, Preis, vorgeschlagene Familie/Variante, Konfidenz, Begründung und Vorschau betroffener historischer Positionen.
- Aktionen: akzeptieren, korrigieren, ablehnen, `NO_PRODUCT`, neue Familie/Variante anlegen, Produktregel aus Zuordnung vorschlagen.
- Produktfamilien-/Variantenverwaltung unterstützt Anlegen, Bearbeiten, Aktivieren/Deaktivieren, Merge/Split und Vorschau vor historisch wirkenden Änderungen.
- Produktregeln sind getrennt von Kategorisierungsregeln sichtbar. Rückwirkendes Anwenden braucht Vorschau und Bestätigung.
- Produktpreisvergleich zeigt Familien- und Variantenansichten. Standard: letzter bekannter Preis und historisches Minimum pro Store. Details: Durchschnitt, Median, Verlauf und zugrunde liegende Bon-Positionen.
- Store-Gruppierung ist zwischen `store_name` und `store_name + store_branch` umschaltbar.
- Effektiv gezahlter Preis und regulärer Preis werden klar unterschieden. Regulärer Preis wird nur angezeigt, wenn er sicher ableitbar ist.
- Auffällige oder ausgeschlossene Preisbeobachtungen sind markiert; Ausschluss/Wiedereinschluss ist reversibel.

### 9.3 Allgemeine UI-Anforderungen

- Responsive Design: optimiert für Desktop (1280px+) und Tablet (768px+). Mobil (≤767px) eingeschränkte Unterstützung.
- Dark Mode und Light Mode (folgt System-Einstellung).
- Alle Geldbeträge werden mit 2 Nachkommastellen und Währungssymbol angezeigt (konfiguriert in Einstellungen).
- Datumsformatierung: `DD.MM.YYYY` (deutsch).
- Ladeanimationen bei allen asynchronen API-Calls (Skeleton Loader oder Spinner).
- Toast-Notifications für Erfolg und Fehler (autoclose nach 5 Sekunden).
- Der aktive Navigationspunkt/Breadcrumb entspricht immer der aktuellen Route, z.B. „Bons" in Bon-Liste und Bon-Detail statt „Dashboard".
- Das eBon-Web-Logo wird als eigenständige Wort-/Bildmarke gestaltet und ersetzt provisorische Platzhalter.

---

## 10. Konfiguration & Umgebungsvariablen

Alle Umgebungsvariablen werden beim Start des Containers gelesen. Sie befüllen initial die `app_settings`-Tabelle, falls die Einstellung dort noch nicht existiert.

| Variable | Pflicht | Standardwert | Beschreibung |
|---|---|---|---|
| `DB_HOST` | Ja | – | PostgreSQL-Host |
| `DB_PORT` | Nein | `5432` | PostgreSQL-Port |
| `DB_NAME` | Ja | – | Datenbankname |
| `DB_USER` | Ja | – | Datenbankbenutzer |
| `DB_PASSWORD` | Ja | – | Datenbankpasswort |
| `APP_API_TOKEN` | Ja | – | Sicherheitstoken für API-Zugriff |
| `PAPERLESS_BASE_URL` | Ja | – | Basis-URL der Paperless-NGX-Instanz (z.B. `http://paperless:8000`) |
| `PAPERLESS_PUBLIC_BASE_URL` | Nein | Wert von `PAPERLESS_BASE_URL` | Vom Browser erreichbare Paperless-Web-URL für klickbare Dokumentlinks |
| `PAPERLESS_API_TOKEN` | Ja | – | Paperless-NGX API-Token |
| `PAPERLESS_EBON_TAG` | Nein | `eBON` | Tag-Name in Paperless-NGX |
| `OPENROUTER_API_KEY` | Nein | – | OpenRouter API-Key (optional, falls KI-Fallback gewünscht) |
| `OPENROUTER_MODEL` | Nein | `google/gemini-flash-1.5` | Zu verwendendes Standardmodell für KI-Kategorisierung |
| `OPENROUTER_PARSING_MODEL` | Nein | Wert von `OPENROUTER_MODEL` | Zu verwendendes Modell für KI-Parsing-Fallback |
| `OPENROUTER_PARSING_MAX_TOKENS` | Nein | `2500` | Max Tokens für KI-Parsing |
| `OPENROUTER_PARSING_TEMPERATURE` | Nein | `0.0` | Temperature für KI-Parsing |
| `AI_PARSING_FALLBACK_ENABLED` | Nein | `true` | Globaler Schalter für OpenRouter-KI-Parsing-Fallback |
| `AI_PARSING_MIN_CONFIDENCE` | Nein | `0.900` | Mindest-Konfidenz für automatische Übernahme eines KI-Parses |
| `AI_PARSING_SYNC_CALL_LIMIT` | Nein | `25` | Maximale KI-Parsing-Calls pro automatischem Sync-Lauf |
| `AI_PARSING_TEXT_MODE` | Nein | `MINIMIZED` | `MINIMIZED` oder `FULL_TEXT`; `FULL_TEXT` ist datenschutzsensitiv |
| `AI_PARSING_STORE_DEBUG_SNIPPETS` | Nein | `false` | Nur lokale Entwicklung: gekürzte/maskierte Prompt-/Antwort-Snippets speichern |
| `SYNC_INTERVAL_MINUTES` | Nein | `60` | Sync-Intervall in Minuten |
| `LOG_LEVEL` | Nein | `INFO` | Log-Level (`DEBUG`, `INFO`, `WARN`, `ERROR`) |

---

## 11. Docker-Deployment

### 11.1 docker-compose.yml (Zielzustand)

```yaml
version: "3.9"

services:
  db:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - db_data:/var/lib/postgresql/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    environment:
      DB_HOST: db
      DB_PORT: 5432
      DB_NAME: ${DB_NAME}
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      APP_API_TOKEN: ${APP_API_TOKEN}
      PAPERLESS_BASE_URL: ${PAPERLESS_BASE_URL}
      PAPERLESS_API_TOKEN: ${PAPERLESS_API_TOKEN}
      PAPERLESS_EBON_TAG: ${PAPERLESS_EBON_TAG:-eBON}
      OPENROUTER_API_KEY: ${OPENROUTER_API_KEY}
    depends_on:
      db:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/api/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
    restart: unless-stopped

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "80:80"
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  db_data:
```

### 11.2 Backend Dockerfile

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 11.3 Frontend Dockerfile

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### 11.4 nginx.conf

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 11.5 Devcontainer-Dateien

Die Entwicklungsumgebung ist Teil des Repositories. Ein KI-Agent muss diese Dateien zuerst anlegen oder aktualisieren, bevor Backend/Frontend implementiert werden.

#### `.devcontainer/devcontainer.json`

```json
{
  "name": "eBon Web Dev",
  "dockerComposeFile": "docker-compose.devcontainer.yml",
  "service": "devcontainer",
  "workspaceFolder": "/workspace",
  "shutdownAction": "stopCompose",
  "forwardPorts": [5173, 8080, 5432],
  "postCreateCommand": "java -version && mvn -version && node --version && npm --version",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "vmware.vscode-boot-dev-pack",
        "redhat.vscode-xml",
        "dbaeumer.vscode-eslint",
        "esbenp.prettier-vscode",
        "ms-azuretools.vscode-docker"
      ]
    }
  }
}
```

#### `.devcontainer/docker-compose.devcontainer.yml`

```yaml
services:
  devcontainer:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      EBON_DEVCONTAINER: "true"
      TESTCONTAINERS_HOST_OVERRIDE: host.docker.internal
    volumes:
      - ..:/workspace:cached
      - /var/run/docker.sock:/var/run/docker.sock
      - devcontainer_backend_target:/workspace/backend/target
      - devcontainer_frontend_node_modules:/workspace/frontend/node_modules
    command: bash /workspace/.devcontainer/init-devcontainer.sh
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: ebon
      POSTGRES_USER: ebon
      POSTGRES_PASSWORD: ebon_dev_password
    ports:
      - "5432:5432"
    volumes:
      - devcontainer_db_data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ebon -d ebon"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  devcontainer_db_data:
  devcontainer_backend_target:
  devcontainer_frontend_node_modules:
```

Die dedizierten Volumes fuer `backend/target` und `frontend/node_modules` halten Devcontainer-Artefakte von Host-Artefakten getrennt. Maven nutzt im Devcontainer weiterhin den Standardpfad `backend/target`; der Pfad ist lediglich ein Container-Volume statt Teil des Windows-Bind-Mounts. `EBON_DEVCONTAINER=true` aktiviert ein Maven-Clean-Profil, das bei `mvn clean` den Inhalt des gemounteten `target`-Verzeichnisses loescht, aber nicht den Mountpoint selbst. Der Docker-Socket-Mount erlaubt Testcontainers, sibling containers ueber Docker Desktop zu starten; `.devcontainer/init-devcontainer.sh` nimmt den `vscode`-User in die passende Socket-Gruppe auf.

#### `.devcontainer/init-devcontainer.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

if [ -S /var/run/docker.sock ]; then
  docker_gid="$(stat -c '%g' /var/run/docker.sock)"
  docker_group="$(getent group "${docker_gid}" | cut -d: -f1 || true)"

  if [ -z "${docker_group}" ]; then
    docker_group="docker-host"
    groupadd -g "${docker_gid}" "${docker_group}"
  fi

  usermod -aG "${docker_group}" vscode
fi

mkdir -p /workspace/backend/target /workspace/frontend/node_modules
chown -R vscode:vscode /workspace/backend/target /workspace/frontend/node_modules

exec sleep infinity
```

#### `.devcontainer/Dockerfile`

```dockerfile
FROM mcr.microsoft.com/devcontainers/java:dev-25-jdk-bookworm

ARG MAVEN_VERSION=3.9.16
ARG NODE_VERSION=24.16.0

RUN rm -f /etc/apt/sources.list.d/yarn.list /etc/apt/sources.list.d/*yarn*.list

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        docker.io \
        git \
        jq \
        postgresql-client \
        tar \
        xz-utils \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/apache-maven.tar.gz \
    && mkdir -p /opt/maven \
    && tar -xzf /tmp/apache-maven.tar.gz -C /opt/maven --strip-components=1 \
    && ln -s /opt/maven/bin/mvn /usr/local/bin/mvn \
    && rm /tmp/apache-maven.tar.gz

RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
        -o /tmp/node.tar.xz \
    && mkdir -p /opt/node \
    && tar -xJf /tmp/node.tar.xz -C /opt/node --strip-components=1 \
    && ln -s /opt/node/bin/node /usr/local/bin/node \
    && ln -s /opt/node/bin/npm /usr/local/bin/npm \
    && ln -s /opt/node/bin/npx /usr/local/bin/npx \
    && ln -s /opt/node/bin/corepack /usr/local/bin/corepack \
    && rm /tmp/node.tar.xz
```

Falls das Basisimage `mcr.microsoft.com/devcontainers/java:dev-25-jdk-bookworm` nicht verfügbar ist, darf nicht still auf eine aeltere Java-Version ausgewichen werden. Der Agent muss den Konflikt dokumentieren und vor einer Abweichung eine explizite Entscheidung einholen.

---

## 12. Backup & Restore

### 12.1 Backup-ZIP-Struktur

```
ebon-backup-2026-05-26_10-00.zip
├── manifest.json
├── categories.json
├── categorization_rules.json
├── product_families.json
├── product_variants.json
├── product_rules.json
├── parse_rules.json
├── parse_rule_suggestions.json
├── receipts.json
├── receipt_items.json
├── ai_parsing_log.json
├── ai_categorization_log.json
├── sync_log.json
├── sync_log_entry.json
└── app_settings.json
```

Produktzuordnungen, Reviewstatus und Preis-Ausschlüsse werden über `receipt_items.json` und Produkt-Stammdatendateien gesichert. Secret-Werte in `app_settings.json` werden nicht im Klartext exportiert. Für Secret-Keys enthält das Backup entweder `null` oder einen maskierten Platzhalter mit zusätzlichem Feld `"requiresReconfiguration": true`. Nach einem Restore müssen externe Secrets über die Einstellungsseite oder Umgebungsvariablen neu gesetzt werden.

### 12.2 manifest.json

```json
{
  "version": "1",
  "appVersion": "1.0.0",
  "createdAt": "2026-05-26T10:00:00Z",
  "tables": ["categories", "categorization_rules", "product_families", "product_variants", "product_rules", "parse_rules", "parse_rule_suggestions", "receipts", "receipt_items", "ai_parsing_log", "ai_categorization_log", "sync_log", "sync_log_entry", "app_settings"],
  "recordCounts": {
    "categories": 8,
    "receipts": 142,
    "receipt_items": 1834
  }
}
```

### 12.3 Kompatibilitätsregel

Das Restore-Backend prüft `manifest.version`. Nur Backups mit kompatibler Version (aktuell: `"1"`) werden akzeptiert. Bei inkompatiblen Versionen: `422 Unprocessable Entity` mit erklärender Fehlermeldung.

### 12.4 Rollierende automatische Backups

Automatische Backups sind eine optionale Phase-13-Erweiterung. Sie verwenden dasselbe Backup-ZIP-Format wie manuelle Backups und exportieren Secrets niemals im Klartext. Die Funktion ist konfigurierbar über Aktivierungsflag, Intervall oder Cron-Ausdruck, Zielverzeichnis/Volume und Retention-Anzahl. Die Retention löscht ausschließlich automatisch erzeugte Backups im konfigurierten Zielverzeichnis und niemals manuell heruntergeladene Backup-Dateien.

---

## 13. Fehlerbehandlung & Logging

### 13.1 Logging

- Log-Format: JSON-Struktur (für Log-Aggregation) mit Feldern: `timestamp`, `level`, `logger`, `message`, `traceId`.
- Log-Level konfigurierbar via `LOG_LEVEL`.
- Jeder eingehende API-Request wird auf `DEBUG` geloggt (Methode, Pfad, Status, Dauer).
- Fehler werden mit Stack-Trace auf `ERROR` geloggt.
- KI-Calls werden auf `INFO` geloggt (Workflow, Modell, betroffene Bon-/Positions-ID, Status, Dauer). Bei KI-Parsing- und KI-Produktzuordnungs-Calls werden keine vollständigen Bontexte, Prompts oder Rohantworten auf `INFO` oder `ERROR` geloggt.
- `TAG_REMOVED`-Events werden als INFO geloggt.
- Secrets (`APP_API_TOKEN`, `PAPERLESS_API_TOKEN`, `OPENROUTER_API_KEY`, Datenbankpasswörter) dürfen nie im Klartext geloggt werden.
- `raw_text`, KI-Prompts und KI-Rohantworten dürfen standardmäßig nicht auf `INFO` oder `ERROR` geloggt werden. Auf `DEBUG` oder in lokalen Debug-Snippets dürfen sie nur gekürzt, maskiert und mit sichtbarer PII-Warnung erscheinen.
- Jeder Request erhält eine `traceId`, die in Fehlerantworten und Logs korreliert werden kann.

### 13.2 Globale Fehlerbehandlung (Backend)

Ein `@ControllerAdvice` fängt alle Exceptions ab und gibt strukturierte Fehlerobjekte zurück (s. Abschnitt 8.2). Unerwartete Exceptions liefern `500` ohne internen Stacktrace in der Response.

### 13.3 Resilience

- KI-Kategorisierungs-Calls: 3 Versuche mit exponential backoff. Schlägt der letzte Versuch fehl oder liefert die KI keine eindeutig passende Kategorie: Position bleibt unkategorisiert (`category_id = NULL`, `category_source = NULL`), Warnung wird geloggt.
- KI-Parsing-Calls: 3 Versuche mit exponential backoff bei transienten Fehlern. Schlägt der letzte Versuch fehl, wird der Fallback als `FAILED` in `ai_parsing_log` protokolliert und der Bon bleibt `PARSE_ERROR` mit dem besten verfügbaren Teilparse. Bei invalidem JSON, zu niedriger Konfidenz oder Sync-Limit wird ein spezifischer Status protokolliert.
- KI-Produktzuordnungs-Calls: 3 Versuche mit exponential backoff bei transienten Fehlern. Schlägt der letzte Versuch fehl oder ist das Ergebnis unsicher, bleibt die Position `NEEDS_REVIEW` oder ohne Produktzuordnung; vollständige Raw-Receipt-Texte dürfen nicht gesendet oder geloggt werden.
- Paperless-NGX-Calls: 3 Versuche bei HTTP 5xx. Bei endgültigem Fehler: Sync schlägt fehl, kein Partial-Import kaputt.
- Datenbank-Fehler: Bei Transaktionsfehler vollständiges Rollback, Fehlermeldung in Response.

---

## 14. Nicht-funktionale Anforderungen

| ID | Anforderung |
|---|---|
| NF-01 | API-Response-Zeit < 500 ms für alle Endpunkte (außer Backup-Download und Report-Aggregation, < 5 s) bei einer Datenbasis von bis zu 10.000 Bons / 150.000 Positionen. |
| NF-02 | Die Applikation startet innerhalb von 60 Sekunden nach Container-Start. |
| NF-03 | Die PostgreSQL-Daten werden ausschließlich über das Docker-Volume persistiert. Kein Datenverlust bei Container-Neustart. |
| NF-04 | Flyway-Migrationen werden beim Backend-Start automatisch ausgeführt. Keine manuelle DB-Initialisierung erforderlich. |
| NF-05 | Der `APP_API_TOKEN` schützt alle API-Endpunkte. Kein Endpunkt ist ohne Token erreichbar (außer `GET /api/health`). |
| NF-06 | Die Applikation läuft auf einem System mit 2 CPU-Cores und 2 GB RAM ohne Performance-Probleme (bei NF-01-Datenmenge). |
| NF-07 | Alle API-Keys und Passwörter werden niemals im Klartext in Logs geschrieben. |
| NF-08 | Datenbankpasswörter und API-Keys werden ausschließlich über Umgebungsvariablen übergeben, nicht in Konfigurationsdateien im Repository. |
| NF-09 | Swagger UI und `/v3/api-docs` sind durch denselben Bearer Token geschützt oder per Konfiguration vollständig deaktivierbar. |
| NF-10 | Alle lokal gelöschten oder durch `TAG_REMOVED` ausgeblendeten Bons werden in Standardlisten, Suche, Reports und Dashboard nicht berücksichtigt, außer ein Endpunkt bietet explizit `includeDeleted=true`. |
| NF-11 | Die Entwicklungsumgebung muss per Devcontainer ohne manuelle lokale Installation von Java, Maven, Node.js oder PostgreSQL startbar sein. |

---

## 15. Offene Punkte / Abgrenzung

| Thema | Entscheidung |
|---|---|
| Multi-User / Auth | Nicht im Scope. Single-User via `APP_API_TOKEN`. |
| OCR von Scan-Bons | Nicht im Scope. Nur bereits durch Paperless-NGX extrahierter Text. |
| Mehrsprachigkeit (i18n) | UI ausschließlich auf Deutsch. |
| Push-Benachrichtigungen | Nicht im Scope. Kein WebSocket/SSE; UI pollt Sync-Status aktiv. |
| Mobile App (nativ) | Nicht im Scope. Responsive Web-UI reicht aus. |
| Archivierung alter Bons | Nicht im Scope. Alle Bons bleiben dauerhaft in der DB. |
| Bon-Erkennung per KI | **Eingeschlossen:** Parsing verwendet regelbasierten Ansatz; bei Fehlschlag KI-Fallback über OpenRouter.ai mit automatischer Rule-Adaptation. |
| Mehrere Paperless-Instanzen | Nicht im Scope. Genau eine Paperless-NGX-Instanz wird unterstützt. |
| HTTPS/TLS im Container | Nicht im Scope. TLS-Terminierung obliegt einem vorgelagerten Reverse Proxy. |
| Automatische Kategorisierungsregel-Generierung per KI | Nicht im Scope. KI darf Kategorien vorschlagen; `categorization_rule`-Einträge werden nur durch Nutzerbestätigung angelegt. Automatische `parse_rule`-Adaptation für Bon-Parsing ist hingegen eingeschlossen. |
| CI/CD-Pipeline | Eingeschlossen in Phase 13: GitHub Actions für Backend, Frontend, Docker-Konfiguration und E2E-Smoke-Tests. |
| Browser-E2E-Tests | Eingeschlossen in Phase 13: Selenium-basierte Smoke-Tests für zentrale UI-Flows. |
| Automatische Backups | Eingeschlossen in Phase 13: zeitgesteuerte rollierende lokale Backups mit Retention. |
| Kategorie-Icons | Eingeschlossen in Phase 13: Auswahl, Anzeige und konsistente Persistenz vorhandener Kategorie-Icons. |
| Release-/Software-Versionierung | Eingeschlossen in Phase 13: zentrale Versionsanzeige und konsistente Build-/Backup-/UI-Version. |
| OpenRouter KI-Parsing-Fallback | Eingeschlossen in Phase 14: kontrollierte automatische Übernahme valider KI-Parses, KI-Parsing-Log, Parser-Regelvorschläge, UI-Verwaltung und Migrationsexport. |
| Produktzuordnung und Preisvergleich | Eingeschlossen in Phase 15a-c: Produktfamilien/-varianten, Review-Queue, Produktpflege, Preisvergleich und Exports. |
| Externe Produktdatenbank | Nicht im Scope. EAN/GTIN wird vorbereitet, aber nicht extern abgefragt. |
| Barcode-Scan-Workflow | Nicht im Scope. |
| Store-Stammdatenmodell | Nicht im Scope von Phase 15. Preisvergleiche nutzen `store_name` oder `store_name + store_branch`. |
| Mehrfachprodukt-Zuordnung einer Bon-Position | Nicht im Scope von Phase 15. Eine `receipt_item` hat maximal eine Produktzuordnung. |
| Separate Produkt-KI-Settings | Nicht im Scope von Phase 15. Produkt-KI nutzt die bestehende KI-Kategorisierungs-/OpenRouter-Konfiguration. |

---

## 16. KI-Agenten-Umsetzung

Diese Spezifikation ist so umzusetzen, dass ein KI-Agent das Projekt schrittweise und prüfbar erzeugen kann. Der Agent arbeitet in kleinen, verifizierbaren Inkrementen und muss nach jeder Phase mindestens Build- oder Testkommandos ausführen.

### 16.1 Repository-Zielstruktur

```
.
├── .devcontainer/
├── backend/
│   ├── pom.xml
│   └── src/
├── frontend/
│   ├── package.json
│   └── src/
├── docs/
│   └── restore-runbook.md
├── .github/
│   └── workflows/
├── docker-compose.yml
├── .env.example
└── README.md
```

### 16.2 Implementierungsphasen

Die Frontend-, Backup- und Hardening-Arbeiten sind bewusst feiner aufgeteilt als in der ersten Grobplanung. Dadurch kann ein KI-Agent mit begrenztem Nutzungskontingent nach jedem Teilabschnitt stoppen, bauen und die Ergebnisse manuell pruefen.

| Phase | Ziel | Mindestnachweis |
|---|---|---|
| 1 | Devcontainer, `.env.example`, Docker-Grundstruktur | Devcontainer öffnet, `java -version`, `mvn -version`, `node --version` funktionieren |
| 2 | Backend-Skeleton mit Spring Boot, Security, Health, OpenAPI | `mvn verify`, `GET /api/health` |
| 3 | Datenmodell, Flyway-Migrationen, JPA-Repositories | Migrationstest gegen PostgreSQL |
| 4 | Paperless-Client und Sync-Service mit Mock-Tests | Tests für Import, Idempotenz, `TAG_REMOVED`, Sync-Lock |
| 5 | Parser mit Corpus-Tests und KI-Fallback-Mock | Parser-Corpus grün, ungültiges KI-JSON führt zu `PARSE_ERROR` |
| 6 | Kategorisierung mit Regeln, Batch-KI-Mock und manueller Überschreibung | Tests für Priorität, Bulk-Apply, fehlenden API-Key |
| 7 | REST-API-DTOs, Validierung, Fehlerbehandlung | Controller-/Contract-Tests |
| 8 | Frontend-Shell mit React/Vite/TypeScript, Tailwind CSS, shadcn/ui, Grundlayout, Basis-Routing, Dashboard-Grundlage und API-Client | `npm run build`, API-Client-Typen an Backend-DTOs ausgerichtet |
| 9 | Bons-UI mit Bon-Liste, Bon-Detailansicht, Rohtextansicht, Editiermodus, Kategorie-/Status-Badges, Re-Parse und Soft-Delete-Darstellung | `npm run build`, Bon-Flows manuell oder per UI-Test geprüft |
| 10 | Suche, Reports, Einstellungen, Kategorien-/Regelverwaltung, CSV-Export und Frontend-Secret-Handling | `npm run build`, relevante Such-/Report-/Settings-Flows geprüft |
| 11 | Backup/Restore, Dry-Run, transaktionaler Restore, Schreibsperre, Restore-Runbook und Backup-UI | `mvn verify`, `npm run build`, Restore-Dry-Run-Test, transaktionaler Restore-Test |
| 12 | Echte Integration, Docker-Gesamtsystem, Logging, Secret-Masking, README, Smoke-Test und finale Verifikation | `docker compose up --build`, Smoke-Test |
| 13 | CI, Selenium-E2E-Smoke-Tests, rollierende Backups, Kategorie-Icons und Software-Versionierung | GitHub-Actions-Workflow definiert, E2E-Smoke-Test lauffähig, `mvn verify`, `npm run build`, `docker compose config` |
| 14 | OpenRouter KI-Parsing-Fallback, KI-Parsing-Log, Parser-Regelvorschläge, UI-Verwaltung, Fixture-Vorschau und Migrationsexport | Mocked OpenRouter-Tests, `mvn verify`, `npm run build`, kein echter OpenRouter-Call in Tests/CI |
| 15a | Produktfundament: Produktfamilien, Produktvarianten, Produktzuordnung an Bon-Positionen, Produktregeln/Synonyme, automatische Zuordnung nach Sync/Reparse und Backup/Restore/Reset-Semantik | Flyway-Migrationen, Produktzuordnungsservice-Tests mit Regel/Historie/KI-Mock, `mvn verify`, kein echter OpenRouter-Call in Tests/CI |
| 15b | Produktreview und Pflege: Prüfliste, manuelle Korrekturen, Merge/Split, Produktverwaltung, Regelvorschläge aus manuellen Zuordnungen und rückwirkendes Anwenden mit Vorschau | API-Contract-Tests für Review/Merge/Split/Regelvorschau, `mvn verify`, `npm run build` |
| 15c | Produktpreisvergleich und Exports: Produktseiten, Store-Vergleich, Einheitenpreise, letzter/minimaler/durchschnittlicher/medianer Preis, Ausreißerhandling, Suche/Report/CSV-Erweiterungen | Report-Tests für Einheiten/Rabatte/Median/Minimum/Ausreißer, CSV-Tests, `mvn verify`, `npm run build` |

### 16.3 Agenten-Regeln

- Der Agent darf keine echten externen API-Calls in Tests ausführen.
- Der Agent muss DTOs, OpenAPI und Frontend-Typen konsistent halten.
- Der Agent darf bei unklaren Parser-Fällen einen failing Test im Corpus ergänzen und danach die Implementierung anpassen.
- Der Agent muss bei jeder Abweichung von Zielversionen, API-Verträgen oder Datenmodell eine kurze Notiz in `README.md` oder `docs/implementation-notes.md` ergänzen.
- Der Agent muss destructive Operationen wie Restore, Hard-Delete oder Datenbank-Reset in Tests isolieren und darf sie nicht gegen produktive Volumes ausführen.

---

## 17. Akzeptanzkriterien & Test-Fixtures

### 17.1 Globale Definition of Done

Eine Implementierung gilt als vollständig, wenn:

- `docker compose up --build` alle Services startet.
- `GET /api/health` ohne Auth `{ "status": "UP" }` zurückgibt.
- Alle geschützten API-Endpunkte ohne Token `401` zurückgeben.
- `mvn verify` im Backend erfolgreich läuft.
- `npm run build` im Frontend erfolgreich läuft.
- OpenAPI unter `/v3/api-docs` verfügbar und geschützt ist.
- Mindestens ein Parser-Corpus mit mehreren Store-Formaten erfolgreich verarbeitet wird.
- Backup-Dry-Run und Restore-Transaktion automatisiert getestet sind.
- KI-Parsing-Fallback automatisiert gegen gemockte OpenRouter-Antworten getestet ist: Erfolg, invalides JSON, niedrige Konfidenz, Sync-Limit, deaktivierter Fallback, fehlender API-Key.
- Parser-Regelvorschläge in Tests validiert, akzeptiert, abgelehnt und als Migration exportiert werden können.
- Produktzuordnung automatisiert gegen Regeln, klare Historie und gemockte KI getestet ist.
- Produktpreisreports Einheitenumrechnung, Mehrfachpackungen, effektive Preise, Median/Minimum/letzten Preis und Ausreißer-Ausschluss korrekt abdecken.

### 17.2 Parser-Corpus

Das Repository enthält unter `backend/src/test/resources/corpus/` mindestens:

```
rewe_simple.txt
rewe_simple.expected.json
aldi_discount.txt
aldi_discount.expected.json
dm_bonus.txt
dm_bonus.expected.json
lidl_multiline_items.txt
lidl_multiline_items.expected.json
parse_error_missing_total.txt
parse_error_missing_total.expected.json
```

Jede `expected.json` folgt dem KI-Parsing-JSON-Schema aus F-02. Bei negativen Tests enthält sie zusätzlich:

```json
{
  "expectedParseStatus": "PARSE_ERROR",
  "expectedErrorContains": "total_amount"
}
```

### 17.3 Akzeptanzkriterien je Kernfeature

**Sync:**

- Given Paperless liefert ein neues Dokument mit `content`, when der Sync läuft, then entsteht genau ein aktiver `receipt`.
- Given dasselbe Dokument wird erneut geliefert, when der Sync erneut läuft, then entsteht kein Duplikat.
- Given ein zuvor importiertes Dokument fehlt nach vollständigem erfolgreichen Sync, then wird es per `deleted_at` und `delete_reason = TAG_REMOVED` ausgeblendet.
- Given Paperless liefert einen Fehler oder unvollständige Pagination, then werden keine Bons als `TAG_REMOVED` markiert.

**Parsing:**

- Given ein gültiger Corpus-Bon, when der Parser läuft, then erfüllt das Ergebnis die Definition `PARSED`.
- Given die Item-Summe weicht um mehr als `0.02` vom Gesamtbetrag ab, then wird `PARSE_ERROR` gesetzt und der Teilparse gespeichert.
- Given regelbasiertes Parsing scheitert und KI liefert valides JSON, then wird der Bon aus dem KI-JSON gespeichert.
- Given KI liefert invalides JSON, then wird kein ungeprüftes Ergebnis persistiert.
- Given der aktuelle Paperless-Rohtext eines Einzelbons abweicht, when der Nutzer „Neuen Rohtext übernehmen und parsen“ bestätigt, then wird der neue Text transaktional gespeichert und geparst.
- Given der aktuelle Paperless-Rohtext abweicht, when der Nutzer „Gespeicherten Rohtext verwenden“ wählt, then bleibt `receipt.raw_text` unverändert.
- Given Paperless beim Einzel-Reparse nicht erreichbar ist, then zeigt die UI keine externen Fehlerdetails und ermöglicht nur einen Reparse mit gespeichertem Rohtext.

**Kategorisierung:**

- Given mehrere passende Regeln, then gewinnt die Regel mit der niedrigsten `priority`.
- Given keine passende Regel und kein `OPENROUTER_API_KEY`, then bleibt die Position unkategorisiert.
- Given KI liefert keine gültige oder keine bekannte Kategorie, then bleibt die Position unkategorisiert und `category_source = NULL`.
- Given KI liefert eine gültige bekannte Kategorie, then wird die Kategorie gesetzt und `category_source = AI`.
- Given manuelle Kategorieänderung, then wird `category_source = MANUAL` und `is_manually_edited = TRUE` gesetzt.

**Backup/Restore:**

- Given ein valides Backup, when Dry-Run ausgeführt wird, then werden keine Daten verändert.
- Given ein inkompatibles Manifest, when Restore ausgeführt wird, then antwortet die API mit `422`.
- Given ein Fehler während Restore-Import, then wird die gesamte Transaktion zurückgerollt.

**Produktzuordnung:**

- Given zwei Positionen beschreiben dasselbe Produkt mit gleicher Größe, when eine Produktregel greift, then werden sie derselben Produktfamilie und Variante zugeordnet.
- Given zwei Positionen unterscheiden sich in Größe oder Packungsstruktur, then dürfen sie nicht automatisch derselben Variante zugeordnet werden.
- Given eine Position enthält keine sichere Größe, then darf nur die Produktfamilie gesetzt werden, außer klare vertrauenswürdige Historie stützt eine Variante.
- Given klare Historie wird berechnet, then zählen KI-only-Treffer nicht als alleinige vertrauenswürdige Grundlage.
- Given eine Produktfamilie hat eine Standard-Kategorie und die Position ist unkategorisiert, then wird die Kategorie gefüllt.
- Given eine Position hat bereits eine Kategorie, then überschreibt Produktzuordnung diese Kategorie nicht.
- Given KI-Produktzuordnung ist unsicher, then landet die Position in der Review-Queue.

**Produktreview und Produktpflege:**

- Given eine Position steht in der Review-Queue, when der Nutzer akzeptiert, korrigiert, ablehnt oder `NO_PRODUCT` wählt, then wird der Status nachvollziehbar gespeichert.
- Given eine Produktregel soll rückwirkend angewendet werden, then zeigt das Backend zuerst eine Vorschau und ändert erst nach Bestätigung Daten.
- Given Produktfamilien oder Varianten werden zusammengeführt oder getrennt, then passiert dies transaktional und nur nach Vorschau/Bestaetigung.

**Produktpreisvergleich:**

- Given Produktvarianten mit `ml/l`, `g/kg` oder Stückzahlen existieren, then werden Einheitenpreise korrekt normalisiert.
- Given eine Mehrfachpackung wie `6x0,33l` existiert, then wird die Gesamtmenge für den Einheitenpreis verwendet.
- Given Rabatte sind abbildbar, then ist der effektiv gezahlte Preis Standard und ein regulärer Preis wird nur bei sicherer Ableitung angezeigt.
- Given eine Preisbeobachtung ist ausgeschlossen, then fließt sie nicht in Minimum, Durchschnitt, Median oder letzten Preis ein und bleibt auditierbar.
- Given Store-Gruppierung umgeschaltet wird, then liefert der Report Ergebnisse nach `store_name` oder `store_name + store_branch`.

**CI/E2E/Operationalisierung:**

- Given ein Pull Request, when GitHub Actions läuft, then werden Backend-Tests, Frontend-Build und Docker-Compose-Konfiguration ohne echte Secrets geprüft.
- Given die Frontend-Smoke-Tests laufen, then werden zentrale Navigations- und Settings-Flows per Selenium geprüft.
- Given rollierende Backups sind aktiviert, when der geplante Zeitpunkt erreicht ist, then entsteht ein maskiertes Backup im konfigurierten Zielverzeichnis.
- Given mehr automatische Backups als erlaubt existieren, when Retention ausgeführt wird, then werden nur alte automatische Backups gelöscht.
- Given eine Kategorie bearbeitet wird, then kann ein Icon aus einer erlaubten Liste gewählt und später in Listen/Reports angezeigt werden.
- Given die Anwendung läuft, then ist die Software-Version in UI und Backend konsistent sichtbar.
