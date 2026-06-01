# eBon-Web

eBon-Web is a single-user expense tracker for electronic receipts imported from Paperless-NGX.

The project is built incrementally from `ebon-specification.md`. Phase 1 only provides the reproducible development environment and Docker foundation; backend and frontend applications are intentionally not scaffolded yet.

## Prerequisites

- Docker Desktop or Docker Engine with Docker Compose
- VS Code with the Dev Containers extension, or another Devcontainer-compatible IDE

You do not need Java, Maven, Node.js, or PostgreSQL installed on the host. The Devcontainer provides them.

## First Setup

1. Copy `.env.example` to `.env`.
2. Replace example secrets in `.env` before connecting to real services.
3. Open the repository in VS Code.
4. Run `Dev Containers: Reopen in Container`.

The Devcontainer starts a local PostgreSQL database and forwards these ports:

- `5432` PostgreSQL
- `8080` future backend API
- `5173` future Vite frontend

## Verify the Devcontainer

Inside the Devcontainer, run:

```bash
java -version
mvn -version
node --version
npm --version
```

From the host or inside the Devcontainer, validate Compose files with:

```bash
docker compose config
docker compose -f .devcontainer/docker-compose.devcontainer.yml config
```

## Current Docker Compose Scope

The root `docker-compose.yml` currently starts only PostgreSQL. Backend and frontend services will be added in later phases after their projects exist.

Start the development database with:

```bash
docker compose up db
```

## Version Notes

The Devcontainer targets Java 25 with `mcr.microsoft.com/devcontainers/java:1-25-bookworm`, Maven 3.9.x, Node.js 22, and PostgreSQL 18. If the Java 25 Devcontainer base image is not available in your environment, use the documented fallback from `ebon-specification.md`: Java 21 LTS, and record that deviation before continuing.

## Safety

- Do not commit `.env`.
- Do not commit real Paperless-NGX or OpenRouter tokens.
- Use `.env.example` only for safe placeholder values.
