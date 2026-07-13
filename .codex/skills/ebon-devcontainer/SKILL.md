---
name: ebon-devcontainer
description: Use when changing eBon devcontainer files, Docker Compose, environment examples, toolchain images, ports, local PostgreSQL, or initial project scaffolding.
---

# eBon Devcontainer

## Contract

Keep development deterministic without host-level Java, Maven, Node.js, or PostgreSQL installations. Use `.devcontainer/`, Compose files, `.env.example`, and the setup section of `README.md` as one synchronized environment contract.

Read only affected parts of `ebon-specification.md` sections 3.1, 10, 11, 16, and 17.

## Required Environment

- Maintain `.devcontainer/devcontainer.json`, its Dockerfile/Compose inputs, root `docker-compose.yml`, and `.env.example` together.
- Provide Java, Maven, Node.js, Docker CLI, PostgreSQL client, Git, curl, and jq.
- Forward `5173`, `8080`, and `5432`; provide local PostgreSQL with obviously non-production credentials.
- Keep example secrets fake. Add every new setting to `.env.example`, including relevant `AI_PARSING_*` and `OPENROUTER_PARSING_*` variables.
- Pin or explain tool/image versions. Document a safe fallback when a requested Java or PostgreSQL image is unavailable.
- Do not change production behavior merely to simplify local setup.

## Verification

Run focused configuration checks while editing. Before completion run:

```bash
java -version
mvn -version
node --version
npm --version
docker compose config
```

Also verify the devcontainer can start when runtime files changed. Report unavailable tools instead of assuming success. Use `ebon-qa` for the accumulated completion gate.
