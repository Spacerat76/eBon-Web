# eBon Devcontainer Skill

Use this skill when creating or changing the development environment, Docker Compose setup, `.env.example`, or project scaffolding.

## Read First

- `ebon-specification.md` sections 3.1, 10, 11, 16, 17.
- `AGENTS.md`.

## Goals

- The project must be usable through a Devcontainer without host-level Java, Maven, Node.js, or PostgreSQL installs.
- Devcontainer setup must be deterministic and documented.
- Example secrets must be safe fake values.

## Required Files

- `.devcontainer/devcontainer.json`
- `.devcontainer/Dockerfile`
- `.devcontainer/docker-compose.devcontainer.yml`
- `.env.example`
- `docker-compose.yml`
- `README.md` setup instructions

## Checklist

- Forward ports `5173`, `8080`, and `5432`.
- Include Java, Maven, Node.js, Docker CLI, PostgreSQL client, Git, curl, and jq.
- Start a local PostgreSQL development database.
- Keep dev database credentials obviously non-production.
- Never write real API tokens into tracked files.
- Document fallback version choices if Java 25 or PostgreSQL 18 images are unavailable.

## Verification

Run or document why unavailable:

```bash
java -version
mvn -version
node --version
npm --version
docker compose config
```

