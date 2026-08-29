# Wiederaufnehmbarer Paperless-eBon-Audit mit Codex

**Status:** Vom Nutzer am 29.08.2026 abschnittsweise freigegebener Entwurf

**Geltungsbereich:** Interaktiver Bestands- und Folgeaudit; kein Ersatz des normalen Live-Syncs

**Abhängigkeiten:** `ebon-specification.md`, `2026-07-13-adaptive-receipt-processing-design.md` und die zugehörigen vier Implementierungspläne

## 1. Ziel

Ein lokaler, wiederaufnehmbarer Audit-Workflow verifiziert zunächst alle mit dem konfigurierten eBon-Tag versehenen Dokumente in Paperless-NGX. Er inventarisiert Händler, Filialen und Layout-Fingerprints, prüft die Parsingqualität und optimiert daraus versionierte Händler-/Filialprofile. Erst für vollständig verifizierte Bons prüft er sämtliche Positionen auf Produktfamilie, Variante und Produktzuordnung.

Spätere Läufe verarbeiten nur neue Dokumente oder Einheiten, deren relevante Eingaben sich geändert haben. Der Fortschritt bleibt zwischen Codex-Sitzungen erhalten und ist in einer lokalen, gitignorierten Markdown-Datei vollständig nachvollziehbar.

## 2. Verbindliche Leitentscheidungen

- Der von Paperless gelieferte Text bleibt die primäre und einzige automatisch übernehmbare Textquelle.
- Das Originaldokument darf ausschließlich lesend geladen und lokal OCR-verarbeitet werden. Lokale OCR ist Vergleichs- und Diagnoseevidenz und ersetzt den Paperless-Text nie automatisch.
- Paperless-Zugriffe des Audits sind ausschließlich `GET`-Operationen.
- Der Audit ist interaktiv und verwendet für KI-gestützte Bewertungen ausschließlich Codex in der ChatGPT-App.
- OpenRouter bleibt dem normalen Live-Betrieb vorbehalten. Der Audit darf weder OpenRouter-Schlüssel erhalten noch OpenRouter- oder Live-KI-Endpunkte aufrufen.
- Der Audit ist dateibasiert und wird als einmaliger Docker-Compose-Job ausgeführt. Er führt keinen eigenständigen Hintergrundbetrieb ein.
- Codex wird nicht als API oder Dienst integriert. Der Nutzer startet und beendet jede Audit-Sitzung bewusst.
- Direkte Produktkorrekturen benötigen eine Konfidenz von mindestens `0,98` und alle deterministischen Schutzprüfungen.
- Produktvorschläge mit Konfidenz von `0,85` bis unter `0,98` werden blockweise über Markdown bestätigt oder bearbeitet.
- Niedrigere Konfidenz, Konflikte und strukturelle Parserprobleme werden in der eBon-Oberfläche geprüft.
- Parserprofile beginnen unabhängig von einer KI-Konfidenz in Quarantäne und durchlaufen die vorhandenen Evidenz-, Schattenprüfungs- und Rollback-Gates.
- Bontexte, OCR-Auszüge, Originaldateien, Tokens und vollständige KI-Eingaben oder -Antworten werden weder im Audit-Arbeitsbereich noch in Git gespeichert.

## 3. Abgrenzung zum Live-Betrieb

Der normale eBon-Sync, der Live-Parser und der dort konfigurierte OpenRouter-Fallback bleiben unverändert eigenständig. Der Audit kann Live-Ergebnisse als zu prüfende Eingabe sehen, behandelt sie jedoch nicht als Wahrheit.

Die bestehende Produktspezifikation grenzt eigene OCR im Live-Betrieb aus. Diese Grenze bleibt erhalten: Die hier beschriebene lokale OCR ist ein bewusst gestartetes Auditwerkzeug, keine neue automatische Importquelle und kein Live-Parser-Fallback.

Der Audit schreibt niemals nach Paperless. Lokale Änderungen an Profilen, Produkten oder Zuordnungen erfolgen ausschließlich über abgesicherte eBon-APIs mit Vorschau, Schutzprüfungen und Auditprotokoll.

## 4. Architektur

### 4.1 Audit-Runner

Der Audit-Runner ist ein eigener Compose-Job. Er orchestriert Paperless-Inventur, Hashbildung, Parseraufrufe, lokale OCR, Clustering, Vorschlagserzeugung, Markdown-Export und Entscheidungsimport. Er verwendet keine direkte Datenbankverbindung und keine Paperless-Schreiboperation.

Der Runner arbeitet mit stabilen Idempotenzschlüsseln und einem Single-Writer-Lock. Er speichert seinen Zustand nach jeder abgeschlossenen Dokument- oder Clustereinheit atomar.

### 4.2 Lokale OCR

Eine isolierte OCR-Komponente verarbeitet nur temporäre Kopien von Originaldokumenten. Sie unterstützt PDF- und Bildoriginale, verwendet deutsche und englische OCR-Modelle und liefert ein strukturiertes Vergleichsergebnis an den Runner. Temporärdateien und OCR-Volltext werden nach dem Vergleich auch bei Fehlern gelöscht.

### 4.3 eBon-API-Fassade

Der Runner liest Parser-, Profil-, Bon-, Produkt- und Reviewdaten ausschließlich über die eBon-API. Änderungen werden über dedizierte Audit-Endpunkte oder bestehende Vorschau-/Apply-Endpunkte ausgeführt. Die API prüft den aktuellen Zustand vor jeder Mutation erneut und schützt manuell bestätigte Daten.

### 4.4 Codex-Arbeitspakete

Sobald deterministische Verarbeitung nicht genügt, erzeugt der Runner ein begrenztes Arbeitspaket je Händler, optionaler Filiale und Layout-Fingerprint. Es enthält Dokument- und Vorschlags-IDs, strukturierte Parsergebnisse, Differenztypen, Zähler, relevante Profilversionen und erlaubte Aktionen.

Codex öffnet Bontext oder Originaldokument bei Bedarf direkt aus der autorisierten lokalen Quelle. Private Inhalte werden nicht in die Fortschrittsdatei kopiert. Codex dokumentiert nur strukturierte Vorschläge, Konfidenz, Begründungscode und Entscheidung.

## 5. Lokaler Audit-Arbeitsbereich

Der komplette Arbeitsbereich liegt unter `var/ebon-audit/` und wird in `.gitignore` aufgenommen:

```text
var/ebon-audit/
├── progress.md
├── audit-state.json
├── decision-history.jsonl
└── lock
```

`audit-state.json` ist die maschinenlesbare Wahrheit für Wiederaufnahme, Hashvergleich, Revisionen und Status. `progress.md` ist die vollständige menschlich lesbare Projektion und enthält die editierbaren Entscheidungsblöcke. `decision-history.jsonl` ist ein append-only Protokoll angewendeter Entscheidungen ohne Bontext. `lock` verhindert überlappende Läufe.

Es werden keine heruntergeladenen Dokumente, OCR-Volltexte oder Bontext-Caches in diesem Verzeichnis abgelegt.

## 6. Audit-Zustand und Invalidation

Für jedes Paperless-Dokument speichert der Zustand mindestens:

- Paperless-Dokument-ID und letzte bekannte Änderungszeit;
- Hash des Paperless-Textes und Hash der Originaldatei;
- normalisierten Händler- und Filialschlüssel samt Anzeigeform;
- Fingerprint, Fingerprint-Version und Parser-/Profilversion;
- Status jeder Auditphase;
- Pflichtfeld-, Summen-, Positions-, Zeilen- und OCR-Qualitätszähler;
- IDs offener oder angewendeter Vorschläge;
- Zeitpunkt des letzten erfolgreichen Audits.

Eine Phase wird nur erneut ausgeführt, wenn eine ihrer Eingaben geändert wurde. Relevante Änderungen sind:

- neuer oder geänderter Paperless-Text;
- neue oder geänderte Originaldatei;
- neue Parser-, Normalisierungs- oder Fingerprint-Version;
- geändertes aktives Händler-/Filialprofil;
- geänderte Produktfamilie, Variante oder Produktregel;
- geänderte Konfidenz- oder Qualitätsgrenze;
- neue, bearbeitete oder zurückgenommene Nutzerentscheidung.

Eine Parsingänderung invalidiert alle nachgelagerten Produktprüfungen des Bons. Eine Produktstammdatenänderung invalidiert nur die davon betroffenen Positionen.

## 7. Verarbeitungsphasen

Der Runner verwendet diese feste Reihenfolge:

```text
INVENTORY
→ SOURCE_VERIFY
→ MERCHANT_BRANCH_DISCOVERY
→ PARSE_VERIFY
→ PROFILE_OPTIMIZATION
→ PRODUCT_VERIFY
→ REPORT
```

Jede Phase ist für dasselbe Eingabefingerprint idempotent. Ein abgebrochener Lauf setzt an der letzten atomar abgeschlossenen Einheit fort.

### 7.1 Inventur

Der Runner lädt alle Seiten des serverseitig nach `PAPERLESS_EBON_TAG` gefilterten Paperless-Ergebnisses. Erst nach vollständig erfolgreicher Paginierung gilt der Snapshot als gültig. Fehler, leere unerwartete Ergebnisse oder unvollständige Seiten verwerfen den neuen Snapshot vollständig. Der Audit leitet daraus niemals `TAG_REMOVED`-Änderungen ab.

### 7.2 Quellenprüfung

Jeder aktuelle eBon erhält Content- und Originaldateihash. Ein unverändertes Dokument wird nur dann erneut verarbeitet, wenn eine nachgelagerte Version oder Entscheidung seine bisherigen Ergebnisse invalidiert.

### 7.3 Händler-, Filial- und Formaterkennung

Händler und Filiale werden frisch aus der aktuellen Auditquelle erkannt. Persistierte Bonpositionen, Kategorien, Produkte und manuelle Zuordnungen sind keine Parsingreferenz.

Die Filialerkennung verwendet diese Reihenfolge:

1. Adresse oder Filialname im Paperless-Text;
2. bekannte Filial-ID-Zuordnung;
3. lokale OCR, falls die Filialinformation vermutlich nur grafisch enthalten ist;
4. expliziter offener Schlüssel `UNBEKANNTE_FILIALE`.

Die Prüfeinheit lautet:

```text
normalisierter Händler + optional normalisierte Filiale + Layout-Fingerprint + Fingerprint-Version
```

Filiale und Layout-Fingerprint bleiben getrennte Identitätsbestandteile. Mehrere Filialen dürfen dasselbe Format verwenden; eine Filiale darf mehrere historische Formate besitzen.

## 8. OCR-Strategie

Jeder eBon wird frisch deterministisch geparst. Lokale OCR wird nur ausgelöst, wenn mindestens eine Bedingung erfüllt ist:

- Paperless-Text fehlt oder ist auffällig kurz;
- Händler, Datum, Gesamtbetrag oder mindestens eine Position fehlen;
- Positionssumme und Gesamtbetrag weichen um mehr als `0,02 EUR` ab;
- plausible Preis- oder Positionszeilen bleiben ungeklärt;
- das Layout weicht vom bekannten Fingerprint ab;
- Händler oder Filiale sind widersprüchlich;
- Paperless-Text und Originaldatei wirken strukturell inkonsistent;
- das Dokument gehört zu den ersten drei unterschiedlichen Stichproben eines neuen Layoutclusters.

Paperless-Text und OCR werden ausschließlich strukturell verglichen: Händler, Filiale, Datum, Gesamtbetrag, Positionen, Mengen, Einheiten, Preise und Zeilenklassen. Ein OCR-Unterschied darf den Paperless-Text nicht automatisch ersetzen.

Ergebnisstatus:

- `VERIFIED`: Deterministischer Parse besteht alle Qualitätsprüfungen.
- `VERIFIED_WITH_OCR`: Lokale OCR bestätigt den strukturellen Parse.
- `OCR_DIFFERENCE`: Lokale OCR liefert eine relevante Abweichung.
- `PARSE_REVIEW`: Parsing ist unvollständig oder widersprüchlich.
- `SOURCE_UNAVAILABLE`: Originaldatei konnte nicht lesend geladen werden.

Nur `VERIFIED` und `VERIFIED_WITH_OCR` dürfen in die automatische Produktprüfung einfließen.

## 9. Parsing- und Profilprüfung

Für jeden Händler-/Filial-/Fingerprint-Cluster vergleicht der Runner:

1. aktives Filialprofil;
2. aktives Händlerprofil;
3. Legacy-Parser als frische Vergleichsbasis;
4. validierten interaktiven Codex-Vorschlag nur bei Qualitätsfehlern;
5. lokale OCR-Struktur nur als zusätzliche Evidenz.

Persistierte oder manuell korrigierte Positionen werden nicht als Parserreferenz verwendet.

Jede plausible Zeile wird als Produktposition, Rabatt/Pfand, Metadaten, Zahlung/Steuer/TSE, Gesamtsumme, ausdrücklich sicher ignorierbar oder ungeklärt klassifiziert. Keine plausible Zeile darf still verworfen werden. Eine ungeklärte Zeile verhindert `VERIFIED`.

Unbekannte Cluster erzeugen ein geschlossenes deklaratives Profil in Quarantäne. Bekannte Cluster werden vollständig gegen die aktive Version geprüft. Abweichungen erzeugen eine neue unveränderliche Version; bestehende Versionen werden nicht mutiert.

Filialprofile haben Vorrang, wenn die Abweichung nachweislich filialbezogen ist. Ein Händlerprofil wird bevorzugt, wenn alle Filialen desselben Fingerprints konfliktfrei übereinstimmen. Regex-Ausdrücke werden auf Breite, Laufzeitrisiken und Kollisionen mit Steuer-, Zahlungs- und TSE-Zeilen geprüft.

### 9.1 Aktivierung und Überwachung

- Neue und geänderte Profile beginnen in `QUARANTINE`.
- Mindestens drei unterschiedliche, vollständige Bons desselben Clusters müssen ohne Pflichtfeld-, Positions- oder Zeilenklassifikationsabweichung bestehen.
- Beim Bestandsaudit müssen zusätzlich alle weiteren geeigneten Bons des Clusters regressionsfrei bestehen.
- Cluster mit weniger als drei unterschiedlichen Bons bleiben in Quarantäne.
- Nach Aktivierung werden die ersten fünf Treffer im Schattenmodus geprüft, danach jeder zehnte Treffer.
- Eine relevante Abweichung suspendiert das Profil sofort.
- Betroffene Bons seit der letzten erfolgreichen Schattenprüfung werden idempotent zum Reparse vorgemerkt.
- Manuelle Korrekturen bleiben geschützt.

## 10. Produktfamilien- und Produktzuordnungsprüfung

Die Produktprüfung verarbeitet ausschließlich Positionen aus `VERIFIED` oder `VERIFIED_WITH_OCR`. Sie berücksichtigt Händler, Filiale, vollständige konservativ normalisierte Beschreibung, Menge, Einheit, Packungsangabe und Preis.

Prüfreihenfolge:

1. bestehende manuell bestätigte Zuordnung;
2. exakte händlerspezifische Produktregel;
3. konfliktfreie bestätigte Historie;
4. vorhandene Produktfamilie mit eindeutiger Variante;
5. vorhandene Produktfamilie ohne sichere Variante;
6. interaktiver Codex-Vorschlag;
7. neue Produktfamilie;
8. offener UI-Prüffall.

### 10.1 Produktschutzregeln

- Manuelle Zuordnungen werden nie automatisch überschrieben.
- Produktfamilien sind global; Evidenz und automatische Regeln sind mindestens händlerspezifisch.
- Eine Beschreibung wird nur händlerweit gelernt, wenn keine Filiale desselben Händlers widerspricht.
- Filialkonflikte verhindern automatische Regelbildung und erzeugen einen UI-Prüffall.
- Automatische Regeln verwenden nur konservativ normalisierte vollständige Beschreibungen.
- `CONTAINS`, Regex und globale Regeln benötigen Nutzerbestätigung.
- Größen, Gewichte, Volumen und Packungen sind Varianten, keine eigenen Familien.
- Ohne sichere Größenangabe wird höchstens die Familie gesetzt.
- Pfand und Tüten dürfen Produktfamilien sein.
- Reine Rabatt-, Zahlungs-, Steuer- und Rundungszeilen werden `NO_PRODUCT`.
- Eine Familien-Standardkategorie darf nur eine leere Kategorie füllen.

### 10.2 Direkte Korrektur

Eine direkte Produktkorrektur ist ab Konfidenz `0,98` erlaubt, wenn zusätzlich:

- der Familienname eindeutig normalisiert ist;
- keine bestehende Familie oder Aliasähnlichkeit von mindestens `0,85` konkurriert;
- die Position keine Rabatt-, Zahlungs-, Steuer- oder Metadatenzeile ist;
- Einheit, Menge, Packung und Preislogik plausibel sind;
- keine manuelle oder filialübergreifende Gegeninformation existiert.

Eine einzelne hochkonfidente Position darf direkt zugeordnet und gegebenenfalls als neue Familie angelegt werden. Eine automatische exakte Händlerregel entsteht erst nach drei konsistenten, konfliktfreien Bons oder nach ausdrücklicher Nutzerbestätigung.

Entscheidungswege:

- `>= 0,98`: im expliziten Apply-Lauf direkt anwenden und auditieren;
- `>= 0,85` und `< 0,98`: editierbarer Markdown-Entscheidungsblock;
- `< 0,85`, Mehrdeutigkeit oder Konflikt: UI-Prüfliste;
- ungeklärte Parserposition: keine Produktzuordnung.

## 11. Fortschrittsbericht

`progress.md` enthält mindestens:

- Lauf-ID, Erstellungszeit und letzten erfolgreichen Checkpoint;
- Gesamtfortschritt nach Phase;
- neue, geänderte, geprüfte und offene Dokumente;
- alle Händler, Filialen und Fingerprints;
- Profilstatus und Evidenzzähler je Cluster;
- OCR-Auslösungen und Differenzzähler;
- Parserprobleme und ungeklärte Zeilenzähler;
- Produktfamilien- und Zuordnungsfortschritt;
- direkt angewendete Korrekturen;
- offene Markdown- und UI-Entscheidungen;
- Fehler, Budgetstopps und konkrete Wiederaufnahmeaktion.

Der Bericht enthält Paperless-Dokument-IDs und lokale Links, aber weder Bontext noch OCR-Auszug. Links enthalten keine Tokens.

## 12. Editierbare Markdown-Entscheidungen

Vorschläge mittlerer Konfidenz werden einzeln dokumentiert:

````markdown
### Vorschlag PA-000184 — Produktzuordnung

- Händler: REWE
- Filiale: Bahnhofstraße 15
- Fingerprint: `fp-v1:8c42…`
- Betroffene Dokumente: 3
- Konfidenz: 0,936
- Vorschlag: `Champignon-Baguette`
- Auswirkung: 3 Positionen; keine manuelle Zuordnung betroffen
- UI: http://127.0.0.1:5173/products?proposal=PA-000184

```ebon-decision
proposalId: PA-000184
revision: 4
proposalType: PRODUCT_ASSIGNMENT
action: DEFER
familyId:
newFamilyName:
variantId:
newVariantName:
comment:
```
````

Erlaubte Aktionen sind `CONFIRM`, `EDIT`, `REJECT`, `DEFER` und `SEND_TO_UI`.

Jeder Block besitzt genau einen Vorschlagstyp mit einem geschlossenen Satz editierbarer Zielfelder:

| Vorschlagstyp | In Markdown editierbare Zielfelder | Einschränkung |
|---|---|---|
| `MERCHANT_BRANCH_IDENTITY` | `storeName`, `storeBranch` | Änderung invalidiert Fingerprint-, Profil- und Produktphasen des Dokuments |
| `LINE_CLASSIFICATION` | `lineClass` | Nur eine definierte Zeilenklasse; kein Bontext im Block |
| `PROFILE_SCOPE` | `profileScope`, `storeBranch` | Profildefinition selbst wird nicht als beliebiges JSON in Markdown editiert |
| `PROFILE_PROPOSAL` | `candidateProfileId` | `CONFIRM` bestätigt nur die Aufnahme in Quarantäne; Aktivierung bleibt evidenzgesteuert |
| `PRODUCT_ASSIGNMENT` | `familyId`, `newFamilyName`, `variantId`, `newVariantName` | Familie und Variante werden serverseitig erneut validiert |
| `NO_PRODUCT` | keine | `CONFIRM` markiert ausschließlich eine serverseitig erneut als sichere Nicht-Produktzeile validierte Position |

`EDIT` darf nur die zum Vorschlagstyp gehörenden Felder ändern. Strukturelle Änderungen einer Profildefinition, Regex-Bearbeitung, globale Regeln, `CONTAINS`-Regeln und konfliktbehaftete Merge-/Split-Entscheidungen werden mit `SEND_TO_UI` bearbeitet. Dadurch bleibt die blockweise Markdown-Prüfung kompakt und kann keine unkontrollierte Regeldefinition einschleusen.

Der Import liest ausschließlich `ebon-decision`-Blöcke mit geschlossenem Schema. Freitext außerhalb dieser Blöcke ist niemals eine Anweisung. Vorschlags-ID und Revision müssen exakt passen. Unbekannte Felder, doppelte IDs, ungültige Kombinationen oder veraltete Revisionen brechen den gesamten Import vor Mutationen ab.

Historisch wirkende Entscheidungen verwenden zwei Phasen:

1. `import --preview` erzeugt eine Prüflauf-ID und zeigt alle Auswirkungen;
2. `import --apply` benötigt diese unveränderte Prüflauf-ID.

Noch nicht importierte bearbeitete Blöcke werden bei einer Berichtserneuerung nicht überschrieben. Jede Anwendung wird ohne Bontext in `decision-history.jsonl` protokolliert.

## 13. Wiederaufnahme, Fehler und Rollback

- Ein Lock verhindert parallele Runnerinstanzen.
- Zustand und Bericht werden über temporäre Dateien und atomaren Austausch gespeichert.
- Paperless-Paginierungsfehler verwerfen die neue Inventur vollständig.
- Nicht ladbare Originale und OCR-Fehler bleiben dokumentbezogen und blockieren andere Dokumente nicht.
- Ungültige Markdown-Entscheidungen verursachen keine Teilanwendung.
- Sammeländerungen laufen transaktional über die eBon-API.
- Vor jeder Mutation wird der aktuelle manuelle Schutzstatus erneut geprüft.
- Jede Mutation trägt einen stabilen Idempotenzschlüssel.
- Vor dem ersten Apply-Lauf wird eine eBon-Datenbanksicherung erstellt und geprüft.
- Entscheidungsverlauf und API-Auditlog speichern Vorher-/Nachher-IDs, sodass ein Testblock kontrolliert zurückgenommen werden kann.
- Profilrollback suspendiert die neue Version und stößt einen begrenzten Reparse an; er reaktiviert keine nachweislich fehlerhafte Altversion.

## 14. Kosten- und Laufzeitkontrolle

- Deterministisches Parsing läuft immer zuerst.
- Lokale OCR läuft nur nach den definierten Gates und für drei Clusterstichproben.
- Codex-Arbeit erfolgt clusterweise und verwendet höchstens drei repräsentative Originale gleichzeitig, sofern keine Abweichung weitere Belege verlangt.
- Strukturell unveränderte Ergebnisse werden anhand Eingabe-, Parser- und Regelversion wiederverwendet.
- Limits für Dokumente, OCR-Seiten und Apply-Mutationen pro Lauf pausieren sauber an einem Checkpoint.
- Limits verändern niemals Qualitätsgrenzen.
- OpenRouter-Aufrufzähler des Audits müssen stets null bleiben.

## 15. Testlauf und Freigabestufen

### 15.1 Sicherung und Baseline

Vor Mutationen werden Datenbanksicherung, Parser-/Profil-/Regelversionen, Dokumentzahlen, offene Parses, Produktstatus und die geschützte Menge manueller Korrekturen erfasst.

### 15.2 Read-only-Inventur

Der erste Lauf inventarisiert Paperless vollständig, bildet Hashes, entdeckt Händler/Filialen/Fingerprints und erzeugt `progress.md`. Er verändert keine eBon-Daten.

### 15.3 Pilotcluster

Der Pilot umfasst mindestens:

- den größten Händler;
- einen Händler mit mehreren Filialen;
- Scan-/Fotobons mit OCR-Bedarf;
- mehrere Layouts desselben Händlers;
- einen Cluster mit weniger als drei Bons;
- Bons mit ungeklärten oder zusammengeführten Positionen.

Codex bearbeitet die Pilotcluster interaktiv. Erst nach erfolgreichem Parser-, OCR-, Markdown-Import- und Produktfluss beginnt der vollständige Bestand.

### 15.4 Vollständige Parser- und Produktprüfung

Alle eBons werden frisch geprüft. Profile werden regressionsgeprüft und bleiben bis zum Evidenzgate in Quarantäne. Produktprüfung folgt nur für verifizierte Bons. Direkte Korrekturen erfolgen ausschließlich in einem bewusst gestarteten Apply-Lauf.

### 15.5 Wiederholungstest

Ein zweiter Lauf ohne geänderte Eingaben darf keine zusätzliche Mutation, OCR- oder Codex-Prüfung erzeugen. Wiederaufnahme nach simuliertem Abbruch, Rollback eines Testblocks und Bericht-/Zustandsabgleich werden ebenfalls geprüft.

## 16. Automatisierte Verifikation

Tests und CI verwenden ausschließlich Paperless- und OpenRouter-Testdoubles. Der Audit-Testpfad muss einen OpenRouter-Aufruf aktiv als Fehler behandeln.

Abzudeckende Bereiche:

- vollständige und fehlerhafte Paperless-Paginierung;
- GET-only Paperless-Verhalten;
- Content-/Originalhash und inkrementelle Invalidation;
- Händler-, Filial- und Fingerprintstabilität;
- OCR-Trigger, struktureller Vergleich und sichere Temporärdateibereinigung;
- vollständige Zeilenklassifikation und `0,02-EUR`-Summengrenze;
- Profilquarantäne, Drei-Bon-Promotion, Schattenprüfung und Rollback;
- Produktkonfidenzgrenzen, Ähnlichkeitsschutz und Variantengrenze;
- Schutz manueller Korrekturen;
- geschlossenes Markdown-Schema, Revisionen, Preview/Apply und Atomizität;
- Idempotenz und Wiederaufnahme;
- Abwesenheit privater Inhalte und Secrets in Dateien, Logs und Testartefakten;
- nachweislich null OpenRouter-Aufrufe im Audit.

## 17. Abnahmekriterien

Der erste Bestandsaudit ist abgeschlossen, wenn:

- jedes aktuelle Paperless-eBon genau einem Auditstatus zugeordnet ist;
- jeder Händler, jede erkannte Filiale und jeder Fingerprint im Bericht erscheint;
- kein plausibler Positions- oder Preistext still verworfen wurde;
- alle verifizierten Bons Pflichtfelder, zusammenhängende Positionsindizes und die `0,02-EUR`-Summengrenze erfüllen;
- jeder Cluster einen dokumentierten Profilstatus besitzt;
- jede verifizierte Position eine Produktentscheidung oder einen eindeutig offenen Prüfstatus besitzt;
- keine manuelle Korrektur überschrieben wurde;
- Audit-Paperless-Zugriffe ausschließlich lesend waren;
- der Audit keine OpenRouter-Aufrufe ausgeführt hat;
- keine Bontexte, OCR-Auszüge, Tokens oder Originaldateien im Arbeitsbereich oder in Git liegen;
- ein unveränderter Wiederholungslauf idempotent ist;
- alle Restfälle über Markdown oder UI erreichbar sind;
- Fehler und Limits ohne Neubeginn fortgesetzt werden können.

## 18. Implementierungsgrenzen

Die Umsetzung wird in getrennte, jeweils testbare Pläne zerlegt:

1. Dateibasierter Runner, Auditstatus, Inventur und Wiederaufnahme;
2. lokale OCR und strukturierter Quellenvergleich;
3. Händler-/Filialinventur sowie Parser-/Profilprüfung;
4. Produktprüfung und sichere direkte Korrekturen;
5. Markdown-Bericht, Entscheidungsimport und UI-Weiterleitung;
6. Pilot, vollständiger Codex-Audit und Abschlussverifikation.

Die vorhandenen Adaptive-Processing-Pläne bleiben die Quelle für Profilpersistenz, Quarantäne, Promotion, Schattenprüfung und Rollback. Der neue Implementierungsplan darf diese Mechanismen nicht parallel neu erfinden, sondern muss fehlende Lifecycle-Meilensteine zuerst fertigstellen und danach den Audit-Runner darauf aufsetzen.
