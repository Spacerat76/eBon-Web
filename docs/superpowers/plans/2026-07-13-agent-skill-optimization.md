# Agent and Skill Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace duplicated agent guidance with a compact project contract, six validated and precisely triggered project skills, and a risk-based verification workflow.

**Architecture:** `AGENTS.md` remains the always-loaded cross-project contract and routes work to small domain skills. Existing skills receive standard YAML frontmatter and retain only non-obvious domain guidance; a new adaptive-processing skill owns the cross-domain learning lifecycle. Official validation, static assertions, word budgets, and diff checks prove the result.

**Tech Stack:** Markdown, YAML frontmatter, PowerShell, bundled Python, Git, Codex project skills.

## Global Constraints

- Follow `docs/superpowers/specs/2026-07-13-agent-skill-optimization-design.md`.
- Preserve all data-loss, privacy, secret, manual-edit, and external-call safeguards.
- Do not duplicate the implementation phase list from `ebon-specification.md` in `AGENTS.md`.
- `AGENTS.md` target is 500–650 words.
- Every `SKILL.md` stays below 500 words; `ebon-qa` targets at most 350 words.
- Every skill frontmatter contains only `name` and `description`; description begins with `Use when` and contains triggers, not workflow steps.
- Complete one skill's RED/GREEN validation before editing the next skill.
- Do not create `agents/openai.yaml` for these repo-local skills.
- No backend, frontend, database, or runtime behavior changes are part of this plan.

---

### Task 1: Compact `AGENTS.md` into the project contract and skill router

**Files:**
- Modify: `AGENTS.md`

**Interfaces:**
- Produces: always-loaded project rules and routing entries for all six `.codex/skills/*/SKILL.md` files.
- Consumes: `ebon-specification.md` as the product source of truth.

- [ ] **Step 1: Record the failing baseline**

Run:

```powershell
$text = Get-Content -Raw AGENTS.md
$words = ($text -split '\s+' | Where-Object { $_ }).Count
if ($words -le 650) { throw "Expected current AGENTS.md to exceed 650 words" }
if ($text -notmatch '## Implementation Order') { throw "Expected duplicated implementation order" }
"RED: AGENTS.md has $words words and duplicates implementation order"
```

Expected: output reports approximately 1,122 words and confirms the duplicated phase list.

- [ ] **Step 2: Replace the file with the compact contract**

Use these exact sections and responsibilities:

```markdown
# Agent Instructions for eBon-Web

## Source of Truth and Workflow
[spec-first, inspect-before-edit, small increments, preserve user work, devcontainer-first]

## Project Skills
[routing table for ebon-devcontainer, ebon-backend, ebon-parser,
ebon-frontend, ebon-adaptive-processing, ebon-qa]

## Cross-Cutting Guardrails
[private data/secrets, mocked external calls, manual edits, TAG_REMOVED,
uncategorized NULL semantics, explicit transactional destructive operations,
DTO/OpenAPI/frontend consistency]

## Verification
[focused checks during work; accumulated completion gates by changed surface;
never claim unavailable/skipped checks passed]
```

The routing table must say `ebon-qa` is required before completion and during test/acceptance review. Remove the 17-step implementation-order copy and all details owned by a routed skill.

- [ ] **Step 3: Verify size, routing, and retained safeguards**

Run:

```powershell
$text = Get-Content -Raw AGENTS.md
$words = ($text -split '\s+' | Where-Object { $_ }).Count
if ($words -lt 500 -or $words -gt 650) { throw "AGENTS word budget: $words" }
foreach ($skill in 'ebon-devcontainer','ebon-backend','ebon-parser','ebon-frontend','ebon-adaptive-processing','ebon-qa') {
  if ($text -notmatch [regex]::Escape($skill)) { throw "Missing route: $skill" }
}
foreach ($guard in 'TAG_REMOVED','category_id = NULL','Paperless-NGX','OpenRouter','manual') {
  if ($text -notmatch [regex]::Escape($guard)) { throw "Missing guardrail: $guard" }
}
if ($text -match '## Implementation Order') { throw 'Implementation order still duplicated' }
"GREEN: AGENTS.md $words words"
```

Expected: `GREEN`, all routes/guardrails found, 500–650 words.

- [ ] **Step 4: Run Markdown verification and commit**

Run: `git diff --check -- AGENTS.md`

Expected: exit `0` and no output.

```bash
git add AGENTS.md
git commit -m "docs: streamline agent project contract"
```

---

### Task 2: Convert `ebon-qa` into the concise completion gate

**Files:**
- Modify: `.codex/skills/ebon-qa/SKILL.md`

**Interfaces:**
- Produces: risk classification and accumulated completion gates used by every other project skill.
- Consumes: routing and cross-cutting rules from `AGENTS.md`.

- [ ] **Step 1: Verify the existing skill is RED**

Run:

```powershell
$python='C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$validator='C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\quick_validate.py'
& $python $validator .codex/skills/ebon-qa
if ($LASTEXITCODE -eq 0) { throw 'Expected missing frontmatter failure' }
```

Expected: `No YAML frontmatter found`.

- [ ] **Step 2: Add minimal frontmatter and refactor the body**

Use this frontmatter:

```yaml
---
name: ebon-qa
description: Use when reviewing eBon changes, selecting verification, defining acceptance tests, or preparing a completion claim, especially for parser, sync, data-loss, secret, backup, restore, product, and external-integration risks.
---
```

Body contract:

- overview: evidence before completion;
- focused checks during implementation;
- table mapping docs/backend/frontend/Compose/full-integration surfaces to final commands;
- high-risk invariants: manual edits, destructive operations, external mocks, secret/private-text absence, conservative product matching;
- final report states commands, outcomes, and unverified items;
- refer to `AGENTS.md`; do not repeat backend/parser/frontend feature catalogs.

- [ ] **Step 3: Verify validator, triggers, and word budget**

Run:

```powershell
$python='C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$validator='C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\quick_validate.py'
& $python $validator .codex/skills/ebon-qa
if ($LASTEXITCODE -ne 0) { throw 'ebon-qa validation failed' }
$text=Get-Content -Raw .codex/skills/ebon-qa/SKILL.md
$words=($text -split '\s+' | Where-Object {$_}).Count
if ($words -gt 350) { throw "ebon-qa word budget: $words" }
if ($text -notmatch 'focused' -or $text -notmatch 'mvn verify' -or $text -notmatch 'npm run build') { throw 'QA contract incomplete' }
"GREEN: ebon-qa $words words"
```

Expected: `Skill is valid!` and at most 350 words.

- [ ] **Step 4: Commit the verified skill**

```bash
git add .codex/skills/ebon-qa/SKILL.md
git commit -m "docs(skills): focus ebon qa verification"
```

---

### Task 3: Validate and slim `ebon-devcontainer`

**Files:**
- Modify: `.codex/skills/ebon-devcontainer/SKILL.md`

**Interfaces:**
- Produces: environment-only guidance for devcontainer, Docker Compose, scaffold, and `.env.example` work.

- [ ] **Step 1: Run the official validator and confirm RED**

Use the Python and validator variables from Task 2, run `& $python $validator .codex/skills/ebon-devcontainer`.

Expected: `No YAML frontmatter found`.

- [ ] **Step 2: Add frontmatter and retain only environment guidance**

```yaml
---
name: ebon-devcontainer
description: Use when changing eBon devcontainer files, Docker Compose, environment examples, toolchain images, ports, local PostgreSQL, or initial project scaffolding.
---
```

Keep: devcontainer-first invariant, required environment files, safe fake secrets, ports `5173/8080/5432`, `.env.example` synchronization, `docker compose config`, and tool version checks. Remove generic rules already in `AGENTS.md`.

- [ ] **Step 3: Verify GREEN and size**

Run official validator, then assert word count is at most 250 and the text contains `.env.example`, `docker compose config`, and all three ports.

Expected: validator passes and budget assertion succeeds.

- [ ] **Step 4: Commit**

```bash
git add .codex/skills/ebon-devcontainer/SKILL.md
git commit -m "docs(skills): validate devcontainer guidance"
```

---

### Task 4: Refactor `ebon-backend` around backend invariants

**Files:**
- Modify: `.codex/skills/ebon-backend/SKILL.md`

**Interfaces:**
- Produces: backend-specific guardrails for JPA/Flyway, API/security, Paperless/OpenRouter, backup/restore/reset, and product persistence.
- Consumes: `ebon-qa` for verification and `ebon-parser` for parser internals.

- [ ] **Step 1: Confirm validator RED**

Run official validator for `.codex/skills/ebon-backend`.

Expected: `No YAML frontmatter found`.

- [ ] **Step 2: Add trigger-only frontmatter and concise body**

```yaml
---
name: ebon-backend
description: Use when changing eBon Spring Boot code, persistence, Flyway migrations, REST DTOs, OpenAPI, security, settings, Paperless sync, OpenRouter integration, backup, restore, reset, or backend tests.
---
```

Body sections:

- `Spec routing`: locate headings with `rg`, then read only affected sections;
- `Persistence/API`: Flyway, DTOs not entities, validation/OpenAPI/frontend type alignment, bearer security;
- `Integrity`: soft-delete, complete pagination before `TAG_REMOVED`, NULL uncategorized state, transactional confirmed destructive work, manual preservation;
- `Integrations/privacy`: auth boundaries, mocked tests, secret masking, prompt/raw response absence;
- `Products`: one assignment, family/variant separation, trusted history excludes AI-only evidence, default category fills empty only;
- `Verification`: focused test then required `mvn verify`, referencing `ebon-qa`.

- [ ] **Step 3: Verify GREEN and retained rules**

Run official validator. Assert at most 500 words and presence of `Flyway`, `TAG_REMOVED`, `category_id = NULL`, `parse_rule_suggestion`, `transactional`, `AI-only`, and `mvn verify`.

Expected: validator and all assertions pass.

- [ ] **Step 4: Commit**

```bash
git add .codex/skills/ebon-backend/SKILL.md
git commit -m "docs(skills): focus backend invariants"
```

---

### Task 5: Refactor `ebon-parser` around deterministic parsing

**Files:**
- Modify: `.codex/skills/ebon-parser/SKILL.md`

**Interfaces:**
- Produces: parser contract, normalization, corpus, AI fallback, and parser-rule boundaries.
- Consumes: `ebon-adaptive-processing` only for format-profile lifecycle work.

- [ ] **Step 1: Confirm validator RED**

Run official validator for `.codex/skills/ebon-parser`.

Expected: `No YAML frontmatter found`.

- [ ] **Step 2: Add frontmatter and compress the parser contract**

```yaml
---
name: ebon-parser
description: Use when changing eBon receipt parsing, OCR normalization, parser rules, parse validation, parser corpus fixtures, AI parsing fallback, parse traces, or parser reparse behavior.
---
```

Keep exact invariants: required `PARSED` fields, `0.02` sum tolerance, German number normalization, contiguous indices, faithful discounts/deposits/package data, no product inference, fixed AI schema/adoption checks, call limit, `FULL_TEXT` confirmation, prompt-free logs, suggestion-before-active-rule, and paired corpus fixtures.

Add one conditional: for merchant profiles, quarantine, shadow checks, rollback, or bootstrap, require `ebon-adaptive-processing`.

- [ ] **Step 3: Verify GREEN and retained rules**

Run official validator. Assert at most 500 words and presence of `0.02`, `position_index`, `FULL_TEXT`, `parse_rule_suggestion`, `.expected.json`, and `ebon-adaptive-processing`.

Expected: validator and assertions pass.

- [ ] **Step 4: Commit**

```bash
git add .codex/skills/ebon-parser/SKILL.md
git commit -m "docs(skills): focus parser contract"
```

---

### Task 6: Refactor `ebon-frontend` around operational UI behavior

**Files:**
- Modify: `.codex/skills/ebon-frontend/SKILL.md`

**Interfaces:**
- Produces: frontend/API/secret, German UX, review, and destructive-action UI guidance.
- Consumes: backend DTOs and `ebon-qa` completion gates.

- [ ] **Step 1: Confirm validator RED**

Run official validator for `.codex/skills/ebon-frontend`.

Expected: `No YAML frontmatter found`.

- [ ] **Step 2: Add frontmatter and reduce feature-catalog duplication**

```yaml
---
name: ebon-frontend
description: Use when changing eBon React, TypeScript, Vite, routing, API clients, German operational UI, forms, tables, dashboards, reports, review queues, settings, or frontend tests.
---
```

Keep: German utilitarian UI, dense/scannable layout, system theme, API token boundary, no hardcoded secrets/URLs, `paperlessDocumentUrl`, DTO/type alignment, NULL uncategorized semantics, visible async/error states, explicit `FULL_TEXT` and destructive confirmations, family/variant distinction, preview-before-history changes, focused tests then build.

Remove the long core-flow catalog already represented by routes/specification.

- [ ] **Step 3: Verify GREEN and retained rules**

Run official validator. Assert at most 500 words and presence of `Deutsch`, `paperlessDocumentUrl`, `categoryId = null`, `FULL_TEXT`, `product family`, `preview`, and `npm run build`.

Expected: validator and assertions pass.

- [ ] **Step 4: Commit**

```bash
git add .codex/skills/ebon-frontend/SKILL.md
git commit -m "docs(skills): focus frontend guidance"
```

---

### Task 7: Create and validate `ebon-adaptive-processing`

**Files:**
- Create: `.codex/skills/ebon-adaptive-processing/SKILL.md`
- Remove after initialization: `.codex/skills/ebon-adaptive-processing/agents/openai.yaml`

**Interfaces:**
- Produces: lifecycle invariants for declarative merchant/branch profiles, Paperless bootstrap, category evidence, and product-family creation.
- Consumes: `docs/superpowers/specs/2026-07-13-adaptive-receipt-processing-design.md` and `docs/superpowers/plans/2026-07-13-adaptive-receipt-processing.md`.

- [ ] **Step 1: Verify the new skill is RED**

Run:

```powershell
if (Test-Path .codex/skills/ebon-adaptive-processing/SKILL.md) { throw 'Expected skill to be absent' }
"RED: ebon-adaptive-processing is missing"
```

Expected: `RED` message.

- [ ] **Step 2: Initialize the skill with the official generator**

Run:

```powershell
$python='C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$creator='C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\init_skill.py'
& $python $creator ebon-adaptive-processing --path .codex/skills `
  --interface 'display_name=eBon Adaptive Processing' `
  --interface 'short_description=Safely evolve receipt profiles and learning' `
  --interface 'default_prompt=Apply the eBon adaptive-processing invariants to this receipt-learning change.'
```

Expected: new skill directory and template. Delete the generated `agents/openai.yaml` because the approved design keeps repo-local skills out of user-facing metadata.

- [ ] **Step 3: Replace the template with the minimal skill**

Use frontmatter:

```yaml
---
name: ebon-adaptive-processing
description: Use when changing eBon merchant or branch format profiles, layout fingerprints, AI profile quarantine, promotion evidence, shadow verification, rollback, Paperless profile bootstrap, learned category rules, or automatic product-family creation.
---
```

Body sections and exact rules:

- `Read only when needed`: direct links to adaptive design/master plan; also use `ebon-parser`, `ebon-backend`, or `ebon-frontend` only for changed surfaces;
- `Parsing profiles`: declarative/versioned, separate from `parse_rule`, branch optional, stable fingerprint, every plausible line classified, unresolved means review;
- `Lifecycle`: current valid AI fallback may be adopted, candidate remains quarantine, promotion after three distinct complete receipts, shadow hits 1–5 then every tenth, mismatch suspends and reparses bounded window while protecting manual corrections;
- `Bootstrap`: Paperless GET only, preview then explicit Apply, fresh Legacy comparison, no persisted manual truth, Legacy-unknown positions remain unresolved/quarantine, idempotent and no raw receipt export;
- `Category/product learning`: confirmed items only; automatic category rules are store-specific normalized exact after three conflict-free receipts or explicit manual confirmation; broad rules require confirmation; new family requires `>= 0.98`, no duplicate at similarity `>= 0.85`, safe line type, sizes/packages become variants, AI-only history remains untrusted;
- `Tests`: mocks only, lifecycle boundaries, rollback/manual protection, no private text.

- [ ] **Step 4: Verify GREEN, references, and word budget**

Run official validator. Then assert at most 500 words, both linked files exist, `agents/openai.yaml` is absent, and the skill contains `three`, `first five`, `every tenth`, `GET`, `Legacy`, `0.98`, and `0.85`.

Expected: validator and all assertions pass.

- [ ] **Step 5: Commit the new skill**

```bash
git add .codex/skills/ebon-adaptive-processing/SKILL.md
git commit -m "docs(skills): add adaptive processing guidance"
```

---

### Task 8: Run the accumulated guidance quality gate

**Files:**
- Verify: `AGENTS.md`
- Verify: `.codex/skills/*/SKILL.md`
- Verify: `docs/superpowers/specs/2026-07-13-agent-skill-optimization-design.md`

**Interfaces:**
- Produces: evidence that routing, validation, budgets, privacy rules, and repository hygiene are complete.

- [ ] **Step 1: Validate all skills independently**

```powershell
$python='C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$validator='C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\quick_validate.py'
foreach($dir in Get-ChildItem .codex/skills -Directory | Sort-Object Name) {
  & $python $validator $dir.FullName
  if($LASTEXITCODE -ne 0){ throw "Skill validation failed: $($dir.Name)" }
}
```

Expected: six `Skill is valid!` results.

- [ ] **Step 2: Check global budgets and routing**

```powershell
$agentWords=((Get-Content -Raw AGENTS.md) -split '\s+' | Where-Object {$_}).Count
if($agentWords -lt 500 -or $agentWords -gt 650){ throw "AGENTS budget: $agentWords" }
foreach($dir in Get-ChildItem .codex/skills -Directory){
  $file=Join-Path $dir.FullName 'SKILL.md'
  $words=((Get-Content -Raw $file) -split '\s+' | Where-Object {$_}).Count
  if($words -gt 500){ throw "$($dir.Name) budget: $words" }
  if((Get-Content -Raw AGENTS.md) -notmatch [regex]::Escape($dir.Name)){ throw "Unrouted skill: $($dir.Name)" }
  "$($dir.Name): $words words"
}
"AGENTS.md: $agentWords words"
```

Expected: all budgets and routes pass.

- [ ] **Step 3: Scan for placeholders, whitespace, and accidental private data**

Run:

```powershell
$markers=@(('T'+'BD'),('TO'+'DO'),('FIX'+'ME'),('PLACE'+'HOLDER'))
foreach($marker in $markers){
  rg -n -F -- $marker AGENTS.md .codex/skills
  if($LASTEXITCODE -eq 0){ throw "Marker found: $marker" }
  if($LASTEXITCODE -ne 1){ throw "Marker scan failed: $marker" }
}
rg -n 'sk-[A-Za-z0-9]|Token [A-Za-z0-9]' AGENTS.md .codex/skills
if($LASTEXITCODE -eq 0){ throw 'Potential secret found' }
if($LASTEXITCODE -ne 1){ throw 'Secret scan failed' }
git diff --check
```

Expected: no placeholder/secret matches and `git diff --check` exits `0`.

- [ ] **Step 4: Compare before/after context size**

Report the committed baseline (`AGENTS.md` 1,122 words; skills 2,972 total words) and current totals. Confirm rules moved rather than disappeared by listing the single authoritative location for `TAG_REMOVED`, `FULL_TEXT`, `parse_rule_suggestion`, uncategorized NULL state, adaptive rollback, and product-family thresholds.

- [ ] **Step 5: Inspect final Git state**

Run: `git status --short`, `git log -8 --oneline`, and `git diff HEAD~7 --check` using the actual number of implementation commits.

Expected: no uncommitted changes, focused commits per guidance unit, and no diff errors.
