# eBon-Web

Repository initialized in the workspace.

This repository was created by GitHub Copilot on your request.

## API — Sync endpoints

This project exposes a small sync API to trigger and inspect Paperless synchronization.

- `POST /api/sync` — trigger a full sync of new Paperless documents. Returns `202 Accepted`.
- `POST /api/sync/document/{id}` — trigger sync for a single Paperless document id. Returns `200 OK` on success or `500 Internal Server Error` on failure.
- `GET /api/sync/status` — returns JSON with sync status fields: `lastSyncAt`, `lastSyncedCount`, `lastErrorCount`, `lastDurationMs`.

Example using `curl`:

```bash
curl -X POST http://localhost:8080/api/sync
curl -X POST http://localhost:8080/api/sync/document/123
curl http://localhost:8080/api/sync/status
```

