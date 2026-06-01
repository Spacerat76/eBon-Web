# Fehlerbehebung aktuelle Phase

Kopiere diesen Prompt, wenn eine umgesetzte Phase beim Testen fehlschlaegt.

```text
Die aktuelle Phase ist umgesetzt, aber beim Pruefen tritt folgender Fehler auf:

<FEHLER HIER EINFUEGEN>

Bitte behebe nur diesen Fehler.

Vorgaben:
- Lies zuerst AGENTS.md.
- Lies die fuer die aktuelle Phase relevanten Abschnitte aus ebon-specification.md.
- Verwende die passenden Skills aus .codex/skills/.
- Fuehre keine neuen Features ein.
- Veraendere keine fachlichen Entscheidungen.
- Revertiere keine fremden oder unrelated Aenderungen.
- Fuehre danach dieselben Pruefkommandos wie in der Phase aus.

Am Ende bitte knapp berichten:
- Ursache
- geaenderte Dateien
- ausgefuehrte Pruefkommandos
- wie ich erneut testen kann
```

