# Interaktiver Codex-Audit für eBons

## Ziel

Der Audit ist ein bewusst gestarteter, interaktiver Codex-Arbeitsablauf. Er prüft zunächst das Parsing aller eBons aus Paperless und anschließend die Zuordnung ihrer Positionen zu Produktfamilien und Varianten.

Die Lösung besteht aus zwei projektlokalen Skills mit kleinen Node-Skripten. Sie verwendet vorhandene Paperless- und eBon-Schnittstellen. Es entstehen kein neuer Hintergrunddienst, keine Audit-Datenbanktabellen, keine eigene KI-Anbindung und kein OpenRouter-Aufruf.

## Gemeinsame Grenzen

- Paperless wird ausschließlich lesend über `GET` verwendet. Unvollständige Paginierung bricht den Lauf ab.
- Paperless-Text ist die primäre Quelle. Das Originaldokument oder lokale OCR werden nur bei begründetem Zweifel zur Gegenprüfung verwendet; OCR ersetzt den Paperless-Text nicht.
- Codex trifft die inhaltlichen Entscheidungen interaktiv in der ChatGPT-App. Die Skripte enthalten keine KI.
- Der fortsetzbare Zustand liegt unter `var/ebon-codex-audit/` und ist gitignoriert.
- Dauerhafter Fortschritt enthält nur Dokument- und Datensatz-IDs, Händler, Filiale, Status, Zähler, Fehlercodes und offene Entscheidungen.
- Paperless-Texte, OCR-Texte und Positionsbeschreibungen liegen nur in einer temporären Blockdatei. Sie wird nach Abschluss des Händlerblocks gelöscht und nie committet.
- Jeder schreibende eBon-Aufruf liest den aktuellen Datensatz erneut. Ein zwischenzeitlich geänderter oder manuell geschützter Datensatz wird nicht automatisch verändert.
- Automatisierte Tests verwenden ausschließlich simulierte Paperless- und eBon-Antworten.

## Skill 1: `ebon-receipt-audit`

### Zweck

Der Skill prüft alle Paperless-eBons mit den aktuellen deterministischen Parsing-Regeln. Er arbeitet die Händler mit den meisten Bons zuerst ab und trennt innerhalb eines Händlers nach Filiale.

### Ablauf

1. Das Skript inventarisiert alle getaggten Paperless-Dokumente und ordnet sie den lokalen eBon-Bons zu.
2. Es gruppiert nach normalisiertem Händler und Filiale und sortiert nach Bonanzahl absteigend.
3. Für den nächsten Block werden die Bons ohne KI-Fallback und ohne Überschreiben manueller Korrekturen mit den aktuellen Regeln neu geparst.
4. Codex vergleicht jeden Paperless-Text mit dem Parseergebnis. Geprüft werden Händler, Filiale, Datum, Gesamtsumme und jede plausible Positions- oder Preiszeile.
5. Codex sammelt alle Fehler bis zum Ende des Händler-/Filialblocks.
6. Eindeutige Fehler in konfigurierbaren Regeln werden anschließend korrigiert. Danach wird der vollständige Block erneut geprüft.
7. Änderungen an Parsercode oder Tests erfolgen nur nach vorheriger Zustimmung des Nutzers. Codex nennt Ursache, betroffene Dateien und vorgesehenen Regressionstest.
8. Nicht eindeutige Fehler werden blockweise mit dem Nutzer geklärt. Wenn kein sinnvoller Vorschlag existiert, bleibt der Fall begründet offen.

### Parser-Erfolg

Ein Bon ist erst geprüft, wenn Pflichtfelder und Positionen mit der Quelle übereinstimmen und keine plausible Zeile still fehlt. Eine Korrektur ist nur eindeutig, wenn sie den beobachteten Fehler erklärt und bei der erneuten Prüfung des gesamten Blocks keine Regression erzeugt.

## Skill 2: `ebon-product-audit`

### Zweck

Der Skill prüft Produktfamilie, Variante und Produktzuordnung für Positionen aus erfolgreich geprüften Bons. Er folgt derselben Händler-/Filialreihenfolge wie der Bon-Audit.

### Ablauf

1. Das Skript lädt die Positionen des nächsten geprüften Blocks sowie aktive Produktfamilien und Varianten.
2. Codex prüft bestehende, fehlende und vorgeschlagene Zuordnungen.
3. Offensichtlich falsche oder fehlende, nicht manuelle Zuordnungen werden sofort korrigiert.
4. Existiert eindeutig keine passende Familie, darf eine neue Produktfamilie angelegt werden. Größe, Gewicht, Volumen und Packungsanzahl werden als Variante modelliert.
5. Neue Familien benötigen mindestens `0,98` Konfidenz, einen eindeutigen normalisierten Namen, keine hinreichend ähnliche bestehende Familie und eine sichere Produktzeile.
6. Manuell zugeordnete Produkte, Familien und Varianten werden niemals automatisch verändert. Bei offensichtlich falscher manueller Zuordnung zeigt Codex Ist-Zuordnung, empfohlenes Ziel und Begründung; die Änderung benötigt eine ausdrückliche Nutzerbestätigung.
7. Unsichere Fälle werden mit einem Vorschlag gesammelt. Wenn kein sinnvoller Vorschlag möglich ist, wird der offene Fall ohne Vorschlag ausgewiesen.

### Produkt-Erfolg

Jede geprüfte Position endet als bestätigte Zuordnung, sicherer Nicht-Produktfall oder eindeutig offener Prüfpunkt. Manuelle Entscheidungen haben Vorrang vor Codex- und Regelzuordnungen.

## Skripte und Zustand

Jeder Skill besitzt ein dependency-freies Node-Skript und einen Test mit dem eingebauten Node-Test-Runner:

```text
.codex/skills/
├── ebon-receipt-audit/
│   ├── SKILL.md
│   └── scripts/
│       ├── receipt-audit.mjs
│       └── receipt-audit.test.mjs
└── ebon-product-audit/
    ├── SKILL.md
    └── scripts/
        ├── product-audit.mjs
        └── product-audit.test.mjs
```

Die Skripte übernehmen nur wiederholbare Mechanik: REST-Zugriffe, vollständige Paginierung, Gruppierung, Sortierung, temporäre Blockdateien, aktuelle Schutzprüfung, Anwendung freigegebener Entscheidungen und Fortschritt. Codex übernimmt die inhaltliche Prüfung.

Benötigt ein Skript eine heute fehlende Anwendungsschnittstelle, beschreibt Codex die kleinste notwendige Änderung und fragt vor Änderungen an Anwendungscode oder Tests.

## Verifikation

- Skripttests prüfen Paginierung, Sortierung, Wiederaufnahme, temporäre Datenbereinigung und Schutz manueller Zuordnungen.
- Skill-Szenarien prüfen, dass eindeutige Fälle bearbeitet, unsichere Fälle gesammelt und Codeänderungen nicht ohne Zustimmung begonnen werden.
- Ein lesender Live-Test prüft Paperless-Inventur und Gruppierung ohne eBon-Mutation.
- Parserkorrekturen werden gegen den vollständigen Händlerblock und die vorhandenen Parsertests geprüft.
- Produktänderungen werden nach Anwendung erneut gelesen; manuelle Zuordnungen müssen unverändert bleiben.
- Ein unveränderter Wiederholungslauf erzeugt keine zusätzlichen Korrekturen.

## Nicht Bestandteil

- autonomer oder geplanter Auditbetrieb;
- OpenRouter oder eine andere KI-API im Audit;
- neue Audit-Datenbankmodelle oder ein allgemeines Audit-Framework;
- automatische Übernahme von OCR-Text;
- automatische Änderung manueller Produktzuordnungen;
- Parsercode- oder Teständerungen ohne vorherige Zustimmung.
