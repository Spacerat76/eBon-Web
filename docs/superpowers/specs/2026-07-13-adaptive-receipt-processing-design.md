# Adaptive Bonverarbeitung mit Formatprofilen

**Status:** fachlich freigegeben
**Datum:** 2026-07-13

## 1. Ziel

Neue Händler, abweichende Filiallayouts und OCR-Artefakte sollen ohne wiederholte Softwareänderungen stabil verarbeitet werden. Das System soll aus kontrolliert übernommenen KI-Parses deklarative Händlerformat-Profile lernen, diese zunächst im Schattenmodus prüfen, nach belastbarer Evidenz automatisch aktivieren und bei Abweichungen automatisch zurückrollen.

Der Ansatz umfasst vier getrennte, aber aufeinander abgestimmte Stufen:

1. Bontext deterministisch in Felder, Positionen und klassifizierte Zeilen überführen.
2. Unsichere oder unbekannte Formate kontrolliert per KI parsen und daraus Formatprofile lernen.
3. Erfolgreich geparste Positionen kategorisieren und konservative Kategorisierungsregeln lernen.
4. Positionen vorhandenen Produktfamilien und Varianten zuordnen oder bei sehr hoher Sicherheit neue Produktfamilien anlegen.

Parsing, Kategorisierung und Produktzuordnung bleiben getrennte fachliche Stufen. Unsicherheit aus einer früheren Stufe darf nicht als Wissen in eine spätere Stufe gelangen.

## 2. Ausgangslage und Ursachen

Der aktuelle Code enthält bereits KI-Parsing, `parse_rule_suggestion`, akzeptierbare `parse_rule`-Einträge und KI-Kategorisierung. Der gewünschte Lernkreislauf ist dennoch nicht vollständig wirksam:

- Dynamische `ITEM_PATTERN`-Regeln werden nur als Fallback verwendet, wenn der generische Parser gar keine Position erkannt hat. Ein Teilparse mit mindestens einer erkannten Position ignoriert dynamische Item-Regeln.
- Der KI-Fallback startet nur, wenn der regelbasierte Parse formal fehlschlägt. Ein formal valider Parse kann nicht erklärte oder falsch zusammengeführte OCR-Zeilen enthalten, ohne einen Schattenvergleich auszulösen.
- Regelvorschläge werden gegen den auslösenden Bon validiert, aber nicht gegen mehrere echte Bons desselben Formats, nicht auf Regressionen geprüft und nach Aktivierung nicht weiter überwacht.
- Für aktive Parserregeln fehlen ein belastbarer Versionslebenszyklus, automatische Suspendierung und die gezielte Ermittlung der seit der letzten Kontrolle betroffenen Bons.
- Die Kategorisierung verarbeitet nur die vom Parser erzeugten Positionen. Sie erhält im KI-Fallback im Wesentlichen Händler und Beschreibung und besitzt keinen automatischen, konservativen Lernkreislauf.
- Produktzuordnung existiert nachgelagert, ist aber noch nicht in denselben kontrollierten Lern- und Reviewprozess eingebunden.

Ein bloßes Erweitern der vorhandenen Regex-Sammlung würde diese strukturellen Grenzen nicht beseitigen.

## 3. Leitentscheidungen

- Händlerformate werden durch versionierte, deklarative Profile beschrieben.
- Ein Profil kann händlerweit oder optional filialbezogen gelten.
- Der Geltungsbereich wird zusätzlich durch einen strukturellen Layout-Fingerabdruck begrenzt.
- KI-generierte Profile beginnen immer in Quarantäne.
- Drei echte, vollständig übereinstimmende Bons desselben Geltungsbereichs sind für automatische Promotion erforderlich.
- Der erste, erfolgreich per KI übernommene Bon zählt als erster Beleg.
- Nach Promotion werden die ersten fünf Treffer vollständig per KI im Schattenmodus geprüft; anschließend wird jeder zehnte Treffer geprüft.
- Jede relevante Abweichung suspendiert das Profil sofort und löst einen gezielten Reparse seit der letzten erfolgreichen Prüfung aus.
- Manuelle Änderungen werden niemals still überschrieben.
- Keine plausible Positionszeile darf still verworfen werden.
- Automatische Kategorisierungsregeln sind ausschließlich händlerspezifische Regeln auf konservativ normalisierter vollständiger Beschreibung.
- Breitere Kategorie-Regeln benötigen Nutzerbestätigung.
- Neue Produktfamilien dürfen nach einer einzelnen KI-Zuordnung ab Konfidenz `0,98` angelegt werden, wenn alle deterministischen Schutzprüfungen bestehen.

## 4. Gesamtarchitektur

### 4.1 Komponenten

#### Bontext-Normalisierung

Erzeugt aus dem Paperless-OCR-Text eine unveränderliche Folge nummerierter Zeilen. Normalisierte Darstellungen dürfen für Matching verwendet werden, müssen aber auf Originalzeilen und Offsets zurückführbar bleiben.

#### Format-Identifikation

Bestimmt:

- normalisierten Händler,
- optional normalisierte Filiale,
- strukturellen Layout-Fingerabdruck.

#### Profilinterpreter

Führt ein fest versioniertes, deklaratives Profil deterministisch aus. Das Profil darf keine Skripte oder generierten Java-Code enthalten.

#### Legacy-Strategie

Der bestehende `RuleBasedReceiptParser` bleibt während der Migration als sichere Kompatibilitätsstrategie erhalten. Dynamische Regeln müssen dabei Teilparses ergänzen können und dürfen nicht nur bei null erkannten Positionen greifen.

#### Parse-Qualitätsprüfung

Validiert Pflichtfelder, Positionen, Summen, Indizes und Zeilenabdeckung. Sie erzeugt `PARSED`, `PARSE_REVIEW` oder `PARSE_ERROR`.

#### KI-Fallback und Profilgenerator

Übernimmt nur schema- und qualitätsgeprüfte KI-Ergebnisse. Erzeugt daraus eine deklarative Profilversion in Quarantäne.

#### Evidenz- und Promotionsdienst

Vergleicht Quarantäneprofile im Schattenmodus mit akzeptierten KI-Ergebnissen, verwaltet Evidenzserien und aktiviert Profile transaktional.

#### Überwachungs- und Rollbackdienst

Plant Schattenprüfungen aktiver Profile, suspendiert abweichende Versionen und reiht betroffene Bons idempotent zur erneuten Verarbeitung ein.

#### Kategorie-Lerndienst

Sammelt konsistente KI-Zuordnungen und erzeugt ausschließlich eng begrenzte händlerspezifische Exact-Regeln automatisch.

#### Produktzuordnungsdienst

Verwendet Produktregeln, vertrauenswürdige Historie und KI. Er kann bei sehr hoher Sicherheit Produktfamilien und gegebenenfalls Varianten anlegen.

### 4.2 Verarbeitungsreihenfolge

1. OCR-Text normalisieren und Zeilenreferenzen erhalten.
2. Händler, optionale Filiale und Layout-Fingerabdruck bestimmen.
3. Passendes aktives Profil auswählen.
4. Profil oder Legacy-Strategie deterministisch ausführen.
5. Parse-Trace und Qualitätsstatus erzeugen.
6. Bei unbekanntem Format, `PARSE_ERROR` oder ungeklärten Zeilen KI-Fallback versuchen.
7. Gültigen KI-Parse übernehmen und Profilkandidat aktualisieren.
8. Nur eindeutig erkannte Positionen kategorisieren.
9. Nur eindeutig erkannte Positionen Produkten zuordnen.
10. Evidenz, Schattenprüfungen und gegebenenfalls Rollback asynchron, persistent und idempotent bearbeiten.

## 5. Händler-, Filial- und Formatidentität

### 5.1 Geltungsbereich

Ein Profil besitzt einen der beiden Geltungsbereiche:

- `STORE`: gilt für den normalisierten Händler unabhängig von der Filiale.
- `BRANCH`: gilt zusätzlich nur für eine normalisierte Filiale.

Auswahlpriorität:

1. aktives Filialprofil mit passendem Fingerabdruck,
2. aktives Händlerprofil mit passendem Fingerabdruck,
3. Legacy-Strategie.

Eine abweichende Adresse allein begründet kein Filialprofil. Ein Filialprofil entsteht nur, wenn die strukturellen Merkmale belastbar vom händlerweiten Profil abweichen.

### 5.2 Layout-Fingerabdruck

Der Fingerabdruck verwendet stabile Strukturmerkmale, beispielsweise:

- normalisierte Ankerwörter und ihre Reihenfolge,
- Kopf-, Positions- und Fußbereich,
- Spalten- und Trennzeichenstruktur,
- Folge abstrakter Zeilentypen,
- Vorhandensein wiederkehrender Tabellenüberschriften,
- relative Position von Summe, Steuer- und Zahlungsbereich.

Ignoriert werden mindestens:

- Datum und Uhrzeit,
- Bon-, Kassen- und Transaktionsnummern,
- Filialadresse bei händlerweitem Profil,
- konkrete Artikeltexte und Preise,
- kleine OCR-Abweichungen, Whitespace und Satzzeichen.

Fingerabdruckalgorithmus und Normalisierung werden versioniert. Eine Algorithmusänderung darf bestehende Profile nicht still einem neuen Fingerabdruck zuordnen.

## 6. Deklaratives Formatprofil

Das Profil wird als JSON gegen ein festes, versioniertes Schema gespeichert. Es beschreibt mindestens:

- Geltungsbereich und erforderliche Anker,
- Händler-, Filial-, Datums-, Zeit- und Summenextraktion,
- Beginn und Ende des Positionsbereichs,
- ein- und mehrzeilige Positionsmuster,
- Zuordnung von Capture-Gruppen zu Beschreibung, Menge, Einheit, Einzelpreis, Gesamtpreis und Rabatt,
- Rabatt-, Pfand-, Coupon-, Steuer-, Summen-, Zahlungs- und Metadatenzeilen,
- Regeln zum Verbinden mehrzeiliger Beschreibungen,
- Pflichtfelder und Profil-spezifische Plausibilitätsbedingungen,
- Schema- und Interpreterversion.

Sicherheitsregeln:

- keine Skripte oder Ausdrücke mit beliebiger Programmausführung,
- Regex-Syntaxprüfung vor Speicherung,
- begrenzte Regex-Länge und Ausführungszeit,
- Prüfung gegen Steuer-, Summen-, TSE- und Zahlungszeilen-Kollisionen,
- nur bekannte Extraktionsfelder und Zeilentypen,
- unveränderliche Profilversionen; Änderungen erzeugen eine neue Version.

## 7. Parse-Trace und Qualitätsstatus

### 7.1 Zeilenklassifikation

Jede plausible relevante Zeile erhält genau eine Klassifikation:

- `POSITION`
- `METADATA`
- `PAYMENT`
- `TOTAL`
- `TAX`
- `IGNORED_SAFE`
- `UNRESOLVED`

Der Trace speichert Zeilennummer, Klassifikation, extrahierte Feldzuordnungen, Profilversion und Begründung. Rohtext wird nicht unnötig dupliziert; Zeilenreferenzen zeigen auf den bereits am Bon gespeicherten Text.

### 7.2 Status

#### `PARSED`

Alle bestehenden `PARSED`-Bedingungen sind erfüllt und keine plausible relevante Zeile ist ungeklärt.

#### `PARSE_REVIEW`

Pflichtfelder und erkannte Positionen sind speicherbar, aber mindestens eine plausible relevante Zeile bleibt `UNRESOLVED`. Automatische Kategorisierung und Produktzuordnung laufen nur für eindeutig erkannte Positionen. Ungeklärte Zeilen erzeugen kein Lernwissen.

#### `PARSE_ERROR`

Pflichtfelder, Schema, Summenprüfung oder grundlegende Konsistenz sind nicht erfüllt. Ein Teilparse darf zur Diagnose erhalten bleiben, gilt aber nicht als erfolgreicher Parse.

## 8. KI-Fallback und Profilkandidaten

Der KI-Fallback bleibt kontrolliert:

- festes JSON-Schema,
- fortlaufende Positionsindizes,
- Pflichtfelder,
- Summentoleranz `0,02`,
- Mindestkonfidenz,
- konfigurierbares Sync-Call-Limit,
- `FULL_TEXT` bei manuellem Reparse nur nach Bestätigung,
- keine vollständigen Prompts oder Rohantworten in der Standardpersistenz.

Ein valider KI-Parse darf den aktuellen Bon übernehmen. Parallel erzeugt die KI ein deklaratives Profil oder eine neue Profilversion. Der Server validiert das Profil unabhängig; die KI aktiviert es niemals direkt.

## 9. Profil-Lebenszyklus

### 9.1 Zustände

- `QUARANTINE`: nur Schattenausführung
- `ACTIVE`: produktiv auswählbar
- `SUSPENDED`: wegen Abweichung deaktiviert
- `RETIRED`: durch neuere Version ersetzt

### 9.2 Evidenz und Promotion

Eine Profilversion wird automatisch aktiv, wenn sie auf drei echten Bons desselben Händler-/Filialumfangs und Fingerabdrucks vollständig mit dem jeweils akzeptierten KI-Ergebnis übereinstimmt. Der auslösende Bon zählt als erster Beleg.

Vollständige Übereinstimmung umfasst:

- Pflichtfelder und Gesamtbetrag,
- Anzahl und Reihenfolge der Positionen,
- konservativ normalisierte Beschreibungen,
- Menge, Einheit, Einzelpreis, Gesamtpreis und Rabatt,
- vollständige Zeilenklassifikation ohne `UNRESOLVED`.

Die drei Belege müssen drei verschiedene Bons sein. OCR-Unterschiede im Rohtext sind zulässig, wenn die fachlichen Ergebnisse übereinstimmen.

Eine Abweichung setzt die Evidenzserie der Version zurück. Aus dem neuen KI-Ergebnis kann eine verbesserte Version in neuer Quarantäne entstehen. Pro Geltungsbereich und Fingerabdruck darf nur eine Profilversion aktiv sein.

### 9.3 Überwachung

Nach Promotion werden:

- die ersten fünf produktiven Treffer vollständig per KI im Schattenmodus geprüft,
- anschließend deterministisch jeder zehnte Treffer geprüft.

Schattenprüfungen sind persistent. Bei ausgeschöpftem KI-Budget werden sie eingereiht und nicht still übersprungen.

Als relevante Abweichung gilt jede Differenz in den Promotionskriterien aus Abschnitt 9.2 oder eine neu auftretende `UNRESOLVED`-Zeile. Warnungen zu optionalen Feldern wie `storeBranch` oder `receiptTime` lösen nur dann eine Suspendierung aus, wenn das Profil einen abweichenden konkreten Wert persistieren würde. Ein ungültiges oder zu unsicheres KI-Ergebnis ist kein Beweis gegen das Profil und wird als fehlgeschlagene Schattenprüfung erneut eingeplant.

## 10. Automatisches Rollback

Eine relevante Abweichung zwischen aktivem Profil und gültigem Schatten-KI-Ergebnis löst aus:

1. aktuelle Profilversion sofort `SUSPENDED`,
2. letzte nachweislich sichere Version reaktivieren, falls vorhanden,
3. alle mit der fehlerhaften Version seit der letzten erfolgreichen Schattenprüfung verarbeiteten Bons bestimmen,
4. betroffene Bons idempotent zur erneuten Verarbeitung einreihen,
5. sicheren Profilpfad, Legacy-Strategie und gegebenenfalls KI-Fallback verwenden,
6. manuelle Änderungen übertragen oder als Konflikt sichtbar machen,
7. Kategorisierung und Produktzuordnung erst nach erfolgreichem Reparse neu ableiten.

Ein Rollback ersetzt Ergebnisse nur transaktional. Ein bestehendes Ergebnis wird nicht vor erfolgreicher Speicherung des Ersatzes gelöscht.

## 11. Lernende Kategorisierung

### 11.1 Reihenfolge

1. Manuelle Kategorie beziehungsweise manuelles Leeren schützen.
2. Aktive Kategorisierungsregel anwenden.
3. KI nur für weiterhin unklare, eindeutig geparste Positionen verwenden.
4. Unsichere oder unbekannte Ergebnisse ohne Kategorie belassen.

### 11.2 Automatische Exact-Regeln

Für ausreichend sichere KI-Zuordnungen wird Evidenz über Händler, konservativ normalisierte vollständige Beschreibung, Zielkategorie, Konfidenz und Bon gespeichert. Ausreichend sicher bedeutet: bekannte aktive Kategorie, kein Regel- oder Nutzerkonflikt und Konfidenz mindestens gemäß `ai_categorization_min_confidence` mit Default `0,900`.

Nach drei konsistenten Treffern aus drei verschiedenen Bons wird eine händlerspezifische `NORMALIZED_EXACT`-Regel aktiviert. Eine abweichende KI- oder Nutzerentscheidung setzt die Evidenzserie zurück.

Eine manuelle Kategorieänderung erzeugt erst dann eine Regel, wenn der Nutzer zusätzlich „Als Regel übernehmen“ bestätigt. Diese bestätigte händlerspezifische Exact-Regel darf sofort aktiv werden.

Automatische `CONTAINS`-, Regex- und globale Regeln sind ausgeschlossen. Solche Verallgemeinerungen bleiben Vorschläge bis zur Nutzerbestätigung.

Korrigiert der Nutzer eine automatisch gelernte Regelzuordnung, wird die Regel suspendiert. Alle seit ihrer Aktivierung durch diese Regel erzeugten, nicht manuell bestätigten Zuordnungen werden erneut geprüft; manuelle Zuordnungen bleiben unverändert.

## 12. Produktfamilien und Varianten

### 12.1 Reihenfolge

1. Produktregel oder Synonym
2. vertrauenswürdige Historie
3. KI-Fallback
4. `NEEDS_REVIEW` oder `NO_PRODUCT`

Die KI erhält nur normalisierten Positionstext, Händler, Preis, Menge und Einheit, niemals den vollständigen Bontext.

### 12.2 Vorhandene Produktfamilie

Eine vorhandene Familie darf bei ausreichender Sicherheit zugeordnet werden. Eine Variante wird nur gesetzt, wenn Größe, Einheit und Packungsstruktur eindeutig sind. Verschiedene Größen oder Packungsstrukturen dürfen nicht still zusammengeführt werden.

### 12.3 Neue Produktfamilie

Eine Familie darf nach einer einzelnen KI-Zuordnung sofort angelegt werden, wenn alle Bedingungen erfüllt sind:

- Konfidenz mindestens `0,98`,
- eindeutiger normalisierter Familienname,
- keine ausreichend ähnliche vorhandene Familie,
- keine Rabatt-, Coupon-, Zahlungs- oder Rundungszeile,
- Größe, Einheit und Packungsstruktur werden als Variante statt als Teil des Familiennamens modelliert,
- bestehende oder manuelle Kategorie wird nicht überschrieben.

Ist Größe oder Packung nicht eindeutig, wird nur die Familie angelegt beziehungsweise zugeordnet.

Die Dublettenprüfung vergleicht normalisierte Familiennamen, Synonyme und Aliasnamen exakt. Zusätzlich blockiert eine deterministische Namensähnlichkeit von mindestens `0,85` die automatische Neuanlage und erzeugt stattdessen einen Merge-/Review-Kandidaten. Unterschiedliche Größen oder Packungsstrukturen werden bei diesem Vergleich nicht als getrennte Familien interpretiert.

Zusätzlich entsteht eine eng begrenzte händlerspezifische Exact-Zuordnung für denselben normalisierten Positionstext. Ihre Quelle bleibt `AI_HIGH_CONFIDENCE`. Ihre Treffer gelten nicht als vertrauenswürdige Historie für spätere unsichere Variantenzuordnungen.

Eine spätere manuelle Korrektur suspendiert die KI-Zuordnung und prüft betroffene Positionen erneut. Bei Namensähnlichkeit bietet das System Zusammenführen statt einer zweiten Familie an. Anlage, Zuordnung, Korrektur und Zusammenführung bleiben auditierbar und reversibel.

## 13. Datenmodell

Die konkrete Migration wird im Implementierungsplan festgelegt. Das Design benötigt mindestens folgende Konzepte:

### `receipt_format_profile`

- Händler und optional Filiale
- Geltungsbereich
- Fingerabdruck und Fingerabdruckversion
- Profil-JSON und Profil-Schemaversion
- Profilversion und Vorgängerversion
- Zustand und Herkunft
- Aktivierungs-, Suspendierungs- und Ersetzungszeitpunkte
- Suspendierungs- beziehungsweise Rollback-Grund
- Treffer- und Überwachungszähler

### `format_profile_evidence`

- Profilversion
- Bon und KI-Parsing-Log
- Modus `QUARANTINE`, `POST_PROMOTION` oder `SAMPLE`
- Vergleichsergebnis und strukturierter Diff
- Zeitpunkt und Zählerstand

### `receipt_parse_trace`

- Bon, Profilversion und Zeilennummer
- Klassifikation
- zugeordneter Positionsindex
- extrahierte Felder oder Feldreferenzen
- Begründung und Reviewstatus

Eindeutig extrahierte `receipt_item`-Einträge erhalten zusätzlich einen ableitbaren beziehungsweise persistierten Extraktionsstatus `CONFIRMED` oder `NEEDS_REVIEW`. Kategorisierung und Produktzuordnung dürfen nur `CONFIRMED` automatisch verarbeiten.

### `adaptive_processing_job`

- Jobtyp für Schattenprüfung oder Reparse
- Bon, Profilversion und idempotenter Schlüssel
- Status, Versuche, nächster Versuch und Fehlgrund

### Kategorie- und Produkterweiterungen

- Evidenz für automatisch lernende Kategorie-Exact-Regeln
- Quellen- und Lebenszyklusdaten automatisch gelernter Regeln
- Auditdaten automatisch angelegter Produktfamilien und Exact-Zuordnungen
- Referenz vom Bon auf verwendete Profilversion

## 14. Oberfläche

### 14.1 Bonansicht

Die Bonansicht zeigt:

- Parse-Status und Quelle,
- verwendete Profilversion,
- Profil-/KI-Differenzen,
- Zeilenklassifikation und ungeklärte Zeilen,
- ausstehende Schattenprüfung,
- automatisch angelegte oder vorgeschlagene Produktfamilien.

Ungeklärte Zeilen können:

- als neue Position übernommen,
- mit vorheriger oder folgender Position verbunden,
- als Metadaten-, Zahlungs-, Steuer- oder Summenzeile markiert,
- bewusst als sicher ignorierbar bestätigt,
- in ihren extrahierten Werten korrigiert werden.

Eine bestätigte Korrektur erzeugt hochwertige Evidenz und eine neue Profilversion in Quarantäne. Ein aktives Profil wird niemals direkt verändert.

### 14.2 Lernende Verarbeitung

Ein zentraler Bereich zeigt:

- Quarantäneprofile mit Fortschritt `1/3`, `2/3`, `3/3`,
- aktive Profile und Überwachung `1/5` bis `5/5`, danach Stichprobenstatus,
- Händler, optionale Filiale und Fingerabdruck,
- strukturierte Profil-/KI-Differenzen,
- suspendierte Versionen und Rollback-Gründe,
- wartende Schattenprüfungen und Reparses,
- Kategorie-Evidenz und automatisch gelernte Regeln,
- automatisch angelegte Produktfamilien und Review-Kandidaten.

Dashboard-Zähler verlinken auf offene Parse-Prüfungen, suspendierte Profile, Kategorie-Konflikte und Produktprüfungen.

## 15. Transaktionen, Parallelität und Idempotenz

- Pro Profilgeltungsbereich darf Promotion oder Suspendierung nur unter Sperre erfolgen.
- Ein partieller Sync darf keine Profilevidenz fälschlich als vollständig markieren.
- Schattenprüfung und Reparse verwenden persistente, idempotente Jobs.
- Derselbe Bon und dieselbe Profilversion dürfen pro Prüfmodus nur einen offenen Job besitzen.
- Rollback ermittelt betroffene Bons über die gespeicherte Profilversion und den Zeitpunkt der letzten erfolgreichen Prüfung.
- Profilaktivierung und Deaktivierung sind transaktional.
- Ein Bonergebnis wird erst ersetzt, wenn Parse, Positionen, Trace und Folgeaufträge gemeinsam gespeichert werden können.
- Bestätigte manuelle Positionen, Kategorien und Produktzuordnungen werden niemals still überschrieben.

## 16. Datenschutz und Sicherheit

- Paperless- und OpenRouter-Tokens werden weder in Profilen noch in Logs gespeichert.
- Vollständige KI-Prompts und Rohantworten werden standardmäßig nicht persistiert.
- `FULL_TEXT` bei manuellem Reparse benötigt weiterhin ausdrückliche Bestätigung.
- Automatische Produktzuordnung sendet niemals vollständigen Bontext.
- Profil-JSON wird serverseitig gegen ein geschlossenes Schema validiert.
- Regexe werden begrenzt, auf Kollisionen geprüft und mit Laufzeitgrenzen ausgeführt.
- API-DTOs enthalten strukturierte Diffs und Begründungen, aber keine geheimen oder unnötigen Rohdaten.

## 17. Backup, Restore und Reset

Backup und Restore umfassen:

- Profile und Versionen,
- Evidenz und Parse-Traces,
- adaptive Jobs und Auditdaten,
- Kategorie-Lernzustand,
- Produktfamilien, Varianten, Regeln und Zuordnungen.

Vollständige Prompts und KI-Rohantworten bleiben ausgeschlossen.

Der Reset importierter Bons löscht bonbezogene Traces, Evidenz, Jobs und Zuordnungen. Aktive Formatprofile, Kategorien, Kategorisierungsregeln, Produktfamilien und Produktregeln bleiben erhalten. Abgeleitete Evidenzzähler werden anschließend aus verbleibenden Belegen neu berechnet.

Ein separater expliziter Produktdaten-Reset bleibt erforderlich.

## 18. Messbarkeit

Mindestens folgende Kennzahlen werden erfasst:

- Parse-Erfolgsquote nach Legacy, Profil und KI,
- Anteil `PARSE_REVIEW` und ungeklärter Zeilen,
- KI-Fallback-Aufrufe und wartende Budgetjobs,
- Promotionen, Schattenabweichungen und Rollbacks,
- Treffer- und Fehlerrate je Profilversion,
- automatisch gelernte Kategorie-Regeln und spätere Korrekturen,
- automatisch angelegte Produktfamilien und spätere Korrekturen oder Zusammenführungen.

Technische Logs bleiben strukturiert, maskiert und über Bon-, Profil- und Job-ID korrelierbar.

## 19. Teststrategie und Akzeptanzkriterien

### 19.1 Profilinterpreter und Fingerabdruck

- Fingerabdruck bleibt bei wechselnden Preisen, Datum, Bonnummer und kleinen OCR-Abweichungen stabil.
- Strukturell unterschiedliche Händler- oder Filiallayouts erhalten unterschiedliche Fingerabdrücke.
- Ein- und mehrzeilige Positionen werden deterministisch extrahiert.
- Jede plausible relevante Zeile wird klassifiziert.
- Ungültige, kollidierende oder zu langsame Regexe werden abgelehnt.

### 19.2 Profil-Lernzyklus

- Ein und zwei Belege aktivieren kein Profil.
- Drei verschiedene, vollständig übereinstimmende Bons aktivieren genau eine Version.
- Eine Abweichung verhindert Promotion und setzt die Evidenzserie zurück.
- Die ersten fünf produktiven Treffer erzeugen Schattenprüfungen.
- Danach erzeugt jeder zehnte Treffer eine Schattenprüfung.
- Budgetmangel erzeugt einen persistenten Job statt stillen Verzichts.
- Eine relevante Abweichung suspendiert das Profil sofort.
- Nur Bons seit der letzten erfolgreichen Prüfung werden automatisch neu geparst.
- Manuelle Änderungen bleiben geschützt.

### 19.3 Kategorisierung

- Drei konsistente KI-Treffer aus drei Bons erzeugen eine händlerspezifische `NORMALIZED_EXACT`-Regel.
- Eine bestätigte manuelle Korrektur kann sofort eine solche Regel erzeugen.
- Ohne Bestätigung entsteht keine automatische globale, Regex- oder `CONTAINS`-Regel.
- Ein manueller Widerspruch suspendiert eine automatisch gelernte Regel.
- Ungeklärte Parsezeilen erzeugen keine Kategorie-Evidenz.

### 19.4 Produktfamilien

- Unter `0,98` wird keine neue Familie automatisch angelegt.
- Ab `0,98` wird nur bei bestandener Dubletten-, Zeilentyp- und Größenprüfung automatisch angelegt.
- Größen und Packungsstrukturen erzeugen Varianten statt eigener Familien.
- Rabatt-, Coupon-, Zahlungs- und Rundungszeilen erzeugen keine Familien.
- Bestehende oder manuelle Kategorien werden nicht überschrieben.
- KI-only-Zuordnungen begründen keine vertrauenswürdige Variantenhistorie.

### 19.5 Integration und Oberfläche

- Sync, Einzel-Reparse, Bulk-Reparse und Rollback sind idempotent.
- Backup/Restore erhält alle neuen Master- und Auditdaten ohne vollständige KI-Rohdaten.
- Der Importdaten-Reset hält Masterdaten und entfernt bonbezogene Evidenz korrekt.
- UI-Tests decken Trace-Review, Profilfortschritt, Suspendierung, Rollback, Kategorie-Lernen und automatische Produktfamilienanlage ab.
- E2E- und CI-Tests verwenden ausschließlich Mocks und anonymisierte Fixtures.

## 20. Einführung

Die Einführung erfolgt in kontrollierten Schritten mit getrennten Kill-Switches:

1. Aktuellen Teilparse-Fehler beheben und Parse-Trace einführen.
2. Profilinterpreter nur im Schattenmodus betreiben.
3. KI-Profilgenerierung und Quarantäne aktivieren.
4. Automatische Promotion und Rollback aktivieren.
5. Lernende Kategorisierung aktivieren.
6. Automatische Produktfamilienanlage zuletzt aktivieren.

Kill-Switches müssen mindestens Profilpromotion, Kategorienlernen und Produktfamilien-Neuanlage unabhängig deaktivieren können, ohne vorhandene aktive Regeln oder reguläres Parsing abzuschalten.

## 21. Änderungen am Produktvertrag

Die spätere Implementierung muss `ebon-specification.md` konsistent anpassen. Insbesondere ändern sich folgende bisherige Grenzen:

- Valide KI-generierte Parserprofile dürfen nach drei echten Schattenbelegen automatisch aktiviert werden.
- Eng begrenzte händlerspezifische Kategorie-Exact-Regeln dürfen nach drei konsistenten KI-Treffern automatisch aktiviert werden.
- `PARSE_REVIEW` wird als eigener Qualitätsstatus ergänzt.
- Formatprofile können optional filialbezogen sein.
- Neue Produktfamilien dürfen bei einer einzelnen KI-Entscheidung ab `0,98` und bestandenen Schutzprüfungen automatisch angelegt werden.

Bestehende Guardrails zu Summentoleranz, Secret-Masking, manuellen Änderungen, Produktvarianten, `NO_PRODUCT`, Backup/Restore und externen Testaufrufen bleiben unverändert bindend.

## 22. Abgrenzung

- OCR selbst bleibt Aufgabe von Paperless-NGX; eBon verarbeitet den gelieferten Text.
- Die KI generiert keine ausführbaren Parserprogramme.
- Vollautomatische globale oder breite Kategorisierungsregeln sind ausgeschlossen.
- Unsichere KI-Produktfamilien werden nicht automatisch angelegt.
- Externe Produktdatenbanken und Barcode-Workflows bleiben außerhalb des Scopes.
- Ein eigenes Store-Stammdatenmodell wird nicht eingeführt; Händler und optionale Filiale bleiben Attribute des Bons und des Profilgeltungsbereichs.
