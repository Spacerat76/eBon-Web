# Restore-Runbook

Dieses Runbook beschreibt die Wiederherstellung eines eBon-Backups. Ein Restore ist destruktiv: Die vorhandenen Anwendungsdaten werden vollständig durch den Inhalt der Backup-ZIP ersetzt.

## Voraussetzungen

- Docker Desktop läuft.
- Die Anwendung ist im Devcontainer oder per `docker compose up` erreichbar.
- PostgreSQL ist erreichbar und Flyway-Migrationen sind auf dem aktuellen Stand.
- Ein gültiger `APP_API_TOKEN` ist vorhanden.
- Eine Backup-ZIP aus `GET /api/backup/download` liegt lokal vor.

## Backup Erstellen

UI:

1. Frontend öffnen.
2. `Einstellungen` öffnen.
3. Tab `Backup` wählen.
4. `Backup herunterladen` ausführen.

API:

```bash
curl -L \
  -H "Authorization: Bearer $APP_API_TOKEN" \
  -o ebon-backup.zip \
  http://localhost:8080/api/backup/download
```

Die Backup-ZIP enthält `manifest.json` und je eine JSON-Datei pro gesicherter Anwendungstabelle. Secret-Werte aus `app_settings` werden nicht im Klartext exportiert.

## Dry-Run Validieren

Vor jedem Restore muss die ZIP validiert werden.

UI:

1. `Einstellungen` > `Backup` öffnen.
2. Backup-ZIP auswählen.
3. `Dry-Run prüfen` ausführen.
4. Fehler und Record-Counts prüfen.

API:

```bash
curl \
  -H "Authorization: Bearer $APP_API_TOKEN" \
  -F "file=@ebon-backup.zip" \
  http://localhost:8080/api/backup/validate
```

Der Dry-Run verändert keine Daten. Ein valider Bericht enthält `valid: true`, die kompatible Manifest-Version und Record-Counts pro Bereich.

## Restore Durchführen

UI:

1. `Einstellungen` > `Backup` öffnen.
2. Backup-ZIP auswählen.
3. Erfolgreichen Dry-Run ausführen.
4. Exakt `RESTORE_BACKUP` in das Bestätigungsfeld eingeben.
5. `Backup wiederherstellen` ausführen.

API:

```bash
curl \
  -H "Authorization: Bearer $APP_API_TOKEN" \
  -F "file=@ebon-backup.zip" \
  http://localhost:8080/api/backup/restore
```

Während Backup und Restore sind andere schreibende API-Operationen gesperrt. Bei einem Importfehler wird die Restore-Transaktion vollständig zurückgerollt.

## Nacharbeiten

Nach einem Restore müssen maskierte Secrets neu gesetzt werden:

- `PAPERLESS_API_TOKEN`
- `OPENROUTER_API_KEY`

Das kann über `Einstellungen` im Frontend oder über die Umgebungsvariablen erfolgen. Danach die Verbindungen testen:

1. `Einstellungen` > `Allgemein` öffnen.
2. Paperless- und OpenRouter-Daten prüfen.
3. `Paperless testen` und bei Bedarf `OpenRouter testen` ausführen.

## Validierung Nach Restore

Empfohlene Checks:

```bash
curl -H "Authorization: Bearer $APP_API_TOKEN" http://localhost:8080/api/dashboard
curl -H "Authorization: Bearer $APP_API_TOKEN" "http://localhost:8080/api/receipts?page=0&size=5&sortBy=receiptDate&sortDir=desc"
curl -H "Authorization: Bearer $APP_API_TOKEN" "http://localhost:8080/api/categories?includeInactive=true"
```

Optional direkt in PostgreSQL:

```bash
PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" \
  -c "select parse_status, count(*) from receipt group by parse_status;"
```

## Fehlerbilder

- `401 Unauthorized`: `APP_API_TOKEN` fehlt oder ist falsch.
- `422 Unprocessable Content`: Backup ist unvollständig, inkompatibel oder enthält nicht importierbare Daten.
- `423 Locked`: Backup oder Restore läuft bereits; schreibende Operation später erneut ausführen.
- Verbindung zu Paperless/OpenRouter schlägt nach Restore fehl: API-Schlüssel neu setzen, weil Secrets nicht im Backup enthalten sind.
