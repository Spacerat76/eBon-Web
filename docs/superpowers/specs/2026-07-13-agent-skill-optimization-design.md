# Agent and Skill Optimization Design

## 1. Goal

Improve AI-agent implementation quality while reducing routinely loaded context and repeated reasoning. Project-wide safety rules remain mandatory; detailed domain guidance loads only when the affected skill is relevant.

Success means:

- every local project skill passes the official skill validator,
- `AGENTS.md` is a compact project contract and skill router,
- duplicated guidance has one authoritative home,
- verification is selected from changed surfaces and risk,
- adaptive receipt processing has focused reusable guidance,
- no quality, privacy, data-integrity, or external-call guardrail is lost.

## 2. Baseline

The current state has five useful local skills, but all fail `quick_validate.py` because their `SKILL.md` files have no YAML frontmatter. `AGENTS.md` contains 1,122 words and repeats rules found in multiple skills. Examples include `FULL_TEXT`, `parse_rule_suggestion`, `TAG_REMOVED`, verification commands, and uncategorized-item semantics.

This creates two costs:

1. `AGENTS.md` is loaded for every task, even when most domain details are irrelevant.
2. Agents repeatedly reconcile almost-identical wording across `AGENTS.md`, backend, parser, frontend, and QA skills.

## 3. Selected Approach

Use a compact global contract plus targeted, validated project skills.

Rejected alternatives:

- Adding frontmatter only fixes discovery but retains duplication and context cost.
- One large all-domain skill reduces duplication but loads excessive context for small changes and weakens routing precision.

## 4. `AGENTS.md` Contract

`AGENTS.md` will contain only rules that apply across nearly every task:

- specification is the product source of truth,
- devcontainer-first execution and preservation of user changes,
- secrets/private receipt data never enter source, logs, fixtures, or responses,
- tests never call real Paperless or OpenRouter,
- schema/DTO/OpenAPI/frontend contract consistency,
- explicit use of the smallest relevant project-skill set,
- risk-based verification and honest reporting of unavailable checks.

It will also contain:

- a concise project-shape summary,
- a routing table from changed surface to project skill,
- a verification matrix,
- a short list of cross-domain invariants whose omission could cause data loss.

The implementation phase list and detailed feature rules will not be repeated. Agents will read only relevant sections of `ebon-specification.md`, using heading search before loading section bodies.

Target: approximately 500–650 words without weakening cross-cutting safety.

## 5. Existing Skill Refactoring

All five existing skills receive valid YAML frontmatter with:

- a lowercase hyphenated name matching the directory,
- a third-person `description` beginning with `Use when`,
- trigger conditions only, not a workflow summary.

Each skill keeps only non-obvious domain decisions and a compact workflow:

- `ebon-devcontainer`: environment, Compose, `.env.example`, scaffold verification.
- `ebon-backend`: persistence, APIs, security, integrations, backup/reset invariants.
- `ebon-parser`: deterministic parsing, corpus contract, AI fallback boundaries.
- `ebon-frontend`: German operational UI, API/secret handling, review workflows.
- `ebon-qa`: risk classification, focused-to-full verification ladder, final evidence contract.

Repeated generic rules move to `AGENTS.md` or `ebon-qa`, not both. Skills reference one another by name only when a second skill is truly required.

Target: normally below 500 words per skill; the frequently selected QA skill should be closer to 300 words.

Repo-local skills will not add `agents/openai.yaml`: they are routed by `AGENTS.md` and YAML descriptions rather than exposed as user-facing marketplace skills. This avoids metadata files with no project benefit.

## 6. New `ebon-adaptive-processing` Skill

Create one new project-specific skill because adaptive processing spans parser, backend, category, product, sync, API, and operational safety. Without a focused skill, future work would repeatedly load several broad skills and reconstruct the same lifecycle rules.

Trigger examples:

- merchant/branch format profiles and layout fingerprints,
- quarantined AI profile proposals and promotion evidence,
- shadow verification, suspension, rollback, and reparse windows,
- read-only Paperless profile bootstrap,
- learned store-specific category rules,
- high-confidence product-family creation.

The skill will define the essential invariants:

- profiles are declarative/versioned and separate from `parse_rule`,
- every plausible line is classified; unresolved lines create review state,
- promotion requires three distinct complete receipts,
- first five hits and every tenth hit receive shadow checks,
- mismatch suspends immediately and protects manual corrections,
- Paperless bootstrap uses GET-only data and fresh Legacy parses without manual assignments,
- category automation creates only store-specific normalized-exact rules,
- product-family creation requires confidence `>= 0.98` and deterministic duplicate/variant safety.

It links directly to the adaptive design and master implementation plan rather than restating their detailed task lists.

## 7. Verification Strategy

Verification follows two levels:

### During implementation

Run the narrowest test that proves the changed behavior. This shortens feedback loops and avoids repeatedly running unrelated suites.

### Before completion

Run the full gate for every changed surface:

| Changed surface | Required completion gate |
|---|---|
| Markdown/spec/skills only | `git diff --check` plus skill validation |
| Backend | `cd backend && mvn verify` |
| Frontend | `cd frontend && npm run build` |
| Compose/devcontainer | `docker compose config` |
| Full integration/runtime behavior | Docker rebuild and focused smoke/E2E |

When multiple surfaces change, their gates accumulate. A skipped or unavailable command is reported with the reason; it is never described as passing.

## 8. Skill TDD and Validation

The already observed RED baseline is that all five existing skills fail the official validator with `No YAML frontmatter found`.

Implementation will proceed one skill at a time:

1. Preserve the failing baseline output.
2. Make the minimum content/frontmatter change.
3. Run `quick_validate.py` and targeted static assertions.
4. Check routing triggers, required guardrails, references, and word budget.
5. Continue to the next skill only after the current one passes.

The new skill starts with a failing existence/validation assertion, then receives its minimal implementation and the same validation gate.

Because subagent spawning is not authorized for this task, validation uses reproducible static scenarios rather than subagent pressure tests. This limitation will be stated in the final handoff; future real-task usage remains the forward-test surface.

## 9. Acceptance Criteria

- All six skill directories pass `quick_validate.py`.
- Every skill description is trigger-only and starts with `Use when`.
- `AGENTS.md` routes adaptive work to `ebon-adaptive-processing`.
- Cross-cutting data-loss, secret, private-receipt, external-call, and manual-edit protections remain discoverable.
- `AGENTS.md` and skill word budgets are measured and reported before/after.
- No duplicate implementation-phase list remains in `AGENTS.md`.
- Relevant specification and plan links resolve.
- No placeholders or trailing whitespace remain.
- `git diff --check` passes.
