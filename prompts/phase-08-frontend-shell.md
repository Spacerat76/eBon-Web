# Phase 08 - Frontend Shell und API Client

```text
Setze Phase 8 aus ebon-specification.md Abschnitt 16 um.

Ziel:
- React/Vite/TypeScript Frontend unter frontend/ mit aktuellen Zielversionen anlegen: React/React DOM 19.2.7, Vite 8.0.16, `@vitejs/plugin-react` 6.0.2, TypeScript 6.0.3
- Tailwind CSS 4.3.0 und shadcn/ui vorbereiten
- Recharts 3.8.1 und lucide-react 1.17.0 fuer Dashboard/Icons installieren
- Devcontainer-Portweiterleitung fuer Vite auf 5173 ergaenzen
- Grundlayout mit Sidebar/Desktop und mobiler Navigation anlegen
- Dashboard als erste nutzbare Seite vorbereiten
- API-Client mit Bearer Token-Unterstuetzung anlegen
- Vite-Proxy fuer `/api` auf das lokale Backend konfigurieren, damit in der Entwicklung kein Backend-CORS noetig ist
- Basis-Routing anlegen
- Build muss erfolgreich sein

Bitte:
- Lies zuerst AGENTS.md.
- Lies ebon-specification.md Abschnitte 8, 9, 14, 16 und 17.
- Verwende die Skills .codex/skills/ebon-frontend und .codex/skills/ebon-qa.
- Keine Marketing-Landingpage bauen.
- UI auf Deutsch.
- Keine echten Secrets im Frontend hartcodieren.
- Keine API-Tokens in den Code schreiben; Token nur lokal ueber UI, lokale Env-Konfiguration oder nicht versionierte Entwicklungseinstellungen verwenden.
- API-Typen an DTOs aus Abschnitt 8.4 ausrichten.
- API-Client soll relative `/api`-URLs verwenden und sich im Dev-Server auf den Vite-Proxy stuetzen.

Pruefkommandos:
- cd frontend && npm run build
- git diff --check

Stoppe nach dieser Phase.

Am Ende bitte zusammenfassen:
- Frontend-Struktur
- Start-/Build-Kommandos
- wie Port 5173 im Devcontainer geoeffnet ist und wie der Vite-Proxy `/api` weiterleitet
- geaenderte Dateien
- ausgefuehrte Pruefkommandos
- wie ich die UI oeffne
- offene Punkte
```
