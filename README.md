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

The Devcontainer starts a local PostgreSQL database and forwards this port:

- `5432` PostgreSQL

Backend port `8080` and frontend port `5173` are intentionally not forwarded in Phase 1 because no backend or frontend service exists yet. They will be added when those phases are implemented.

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

The Devcontainer uses the documented fallback Java 21 LTS image `mcr.microsoft.com/devcontainers/java:1-21-bookworm`, Maven 3.9.x, Node.js 22, Docker CLI, and PostgreSQL 18.

PostgreSQL 18 volumes are mounted at `/var/lib/postgresql` rather than `/var/lib/postgresql/data`, matching the official image layout for PostgreSQL 18+.

Deviation from target versions: `ebon-specification.md` targets Java 25, but the Java 25 Devcontainer image was not available as `mcr.microsoft.com/devcontainers/java:1-25-bookworm`. Per the specification's fallback rule, local development uses Java 21 LTS until a Java 25 Devcontainer image is available.

The Devcontainer intentionally installs Maven, Node.js, and Docker CLI in `.devcontainer/Dockerfile` instead of using Devcontainer Features from `ghcr.io`. This avoids failures in environments where the Feature registry cannot resolve `ghcr.io/devcontainers/features/*`.

## Safety

- Do not commit `.env`.
- Do not commit real Paperless-NGX or OpenRouter tokens.
- Use `.env.example` only for safe placeholder values.
