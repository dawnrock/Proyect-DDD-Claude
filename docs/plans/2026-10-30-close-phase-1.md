# Plan: Close phase 1 — final documentation, `v1-monolito` tag and `Hexagonal` branch

- **Issue:** [#13](https://github.com/dawnrock/Proyect-DDD-Claude/issues/13)
- **Type:** docs (closes the phase; no production code changes)
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-30
- **Status:** in progress
- **Total estimate:** ~65 min (8 tasks: 6 in the PR + 2 after it is merged)

## Goal

Leave the layered-monolith snapshot documented as it really ended up, freeze it with the `v1-monolito` tag and open phase 2 by creating `Hexagonal` from `Monolithic` and making it the repository's default branch. Nothing under `src/` changes: the code at the tag is exactly what `Monolithic` holds today.

### Out of scope

- Translating the rest of `docs/` (glossary, ADRs, analysis, `docs/ia` annexes, `fase-2-hexagonal.md`): it stays in its own issue. Only the three documents this issue rewrites are translated (see the first open question).
- Any code, test or `openapi.yaml` change.
- Phase 2 work (#14 onwards).
- Changes to `CLAUDE.md` or `.claude/` (they need the user's explicit approval; none is required here).

## Context

| Area | File | Change |
| ---- | ---- | ------ |
| docs | `docs/arquitectura/fase-1-capas.md` | Final state in English: layers, dependency rules, ArchUnit rules, REST endpoints, screens, security, how to run; the "Implemented in #N" blocks become one feature → issue/PR table; the "Issues of the phase" table includes #31, #48, #50, #54, #57 |
| docs | `README.md` | English; branches table with `Monolithic` frozen at `v1-monolito` and `Hexagonal` as the active branch; workflow includes `/implement-plan-team` |
| docs | `docs/ia/arquitectura-ia.md` | English; "Usage log · Phase 1": which plans and PRs each piece (skill, agent, hook, CI) produced and what had to be adjusted |
| docs | `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md` | Status as it will be after the merge and the tag (see implement-plan step 6.3) |
| git / GitHub | tag `v1-monolito`, branch `Hexagonal`, default branch | After the PR is merged, with the user's go-ahead |

Facts gathered for the documentation:

- REST (`openapi.yaml`): `GET/POST /api/accounts`, `GET /api/accounts/{id}`, `POST /api/accounts/{id}/deposits`, `POST /api/accounts/{id}/withdrawals`, `POST /api/transfers`.
- Web (`presentation.web`): `/`, `/accounts/new` (GET/POST), `/accounts/{id}`, `POST /accounts/{id}/deposits|withdrawals|transfers`, `/login`, `/error/403`.
- ArchUnit: `LayeredArchitectureTests` (D1-D5 and the #12/#54 rules) and `DomainIsolationTests`.
- Milestone "Fase 1": every issue is closed except #13.
- `ci.yml` already runs on `Monolithic` and `Hexagonal`; the default branch is `Monolithic` today and there is no tag yet.

## Tasks

### [ ] T1 — `fase-1-capas.md`: translate and consolidate the design (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-1-capas.md`
- **Depends on:** —
- **What to do:**
  1. Translate the layers, dependencies (D1-D5) and deposit-flow sections to English, keeping the glossary terms (R1-R19).
  2. Replace "Completed with the real state when the phase closes (#13)" with a line saying this is the final state, frozen at `v1-monolito`.
  3. Merge the #12 and #54 ArchUnit tables into one "ArchUnit rules" table (rule → what it covers → issue).
  4. If the Mermaid layer diagram changes (for example to show `presentation`'s shared security support), show it to the user before committing, in grayscale.
- **Acceptance criteria:**
  - [ ] The sections are in English and every rule in `LayeredArchitectureTests` appears in the table.
  - [ ] Every relative link in the document resolves.

### [ ] T2 — `fase-1-capas.md`: final state of the features (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-1-capas.md`
- **Depends on:** T1
- **What to do:**
  1. Add "REST endpoints" (method, path, role, codes; points to `openapi.yaml` as the source of truth) and "Screens" (route, what it shows, role) tables.
  2. Add a short "Security" section (ADR 0008: two chains, roles in URLs, ownership in `application`, 404 for someone else's account).
  3. Collapse the "Implemented in #N" blocks into a feature → issue → PR → notable decisions table; keep only the decisions a phase 2 reader needs (Immutables DTOs with Jackson 3, `MoneyTransferService` created with `new`, cookie-only sessions, `@ControllerAdvice` scoped by package).
  4. Update "Issues of the phase" with every issue of the milestone and its PR (from `historial.md`).
- **Acceptance criteria:**
  - [ ] The endpoint and screen tables match `openapi.yaml` and the `@GetMapping`/`@PostMapping` mappings in `presentation.web`.
  - [ ] The issues table lists all 17 issues of the milestone.

### [ ] T3 — `README.md` in English with the final state (~5 min)

- **Layer:** docs
- **Files:** `README.md`
- **Depends on:** —
- **What to do:**
  1. Translate it. In the branches table: `Monolithic` frozen at tag `v1-monolito`, `Hexagonal` as the default and active branch.
  2. Keep "Run" and "Login" as they are (translated); add `/implement-plan-team` to the workflow block; drop the "(+ Telegram notice)" note, which is optional now.
- **Acceptance criteria:**
  - [ ] The commands in "Run" still work (`./mvnw verify`, `./mvnw spring-boot:run`, `curl -u admin:demo …/api/accounts` returns 200).

### [ ] T4 — `arquitectura-ia.md` in English (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/arquitectura-ia.md`
- **Depends on:** —
- **What to do:**
  1. Translate the document as it is (map, pieces table, team flow, models, security). The Mermaid diagrams keep their structure; only their labels are translated.
- **Acceptance criteria:**
  - [ ] The pieces table still lists every file in `.claude/agents`, `.claude/skills`, `.claude/hooks` and `.github/workflows`.

### [ ] T5 — `arquitectura-ia.md`: usage log of phase 1 (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/arquitectura-ia.md`
- **Depends on:** T4
- **What to do:**
  1. Add "Usage log · Phase 1": a table of issue → plan (`docs/plans/`) → how it was implemented (`implement-plan` / `implement-plan-team` / by hand) → PR.
  2. Add "Adjustments made during the phase", one line each with its PR: Telegram toggle (#40), deny rule on hooks that never applied (#44), project status required by CI (#37) and split into status + annexes (#58), Lombok out of the domain (ADR 0009, #53/#55), Graphify only for dependency questions (ADR 0010, #58), status written as after the merge (#59).
- **Acceptance criteria:**
  - [ ] Every plan in `docs/plans/` appears in the table and every PR link resolves.

### [ ] T6 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1-T5
- **What to do:**
  1. `estado-del-proyecto.md` as it will be after the merge: #13 in "Done", phase 1 closed with tag `v1-monolito`, active branch `Hexagonal`, next step `/plan-feature 14` on `Hexagonal`.
  2. `decisiones.md`: `Hexagonal` is the default branch; `Monolithic` is frozen at `v1-monolito` like `master` (fixes only by explicit decision).
  3. `pendientes.md`: the translation item no longer covers `README.md`, `fase-1-capas.md` and `arquitectura-ia.md`.
  4. `historial.md`: one row for this PR.
- **Acceptance criteria:**
  - [ ] `git diff --name-only origin/Monolithic...HEAD | node scripts/check-project-status.mjs -` (the CI check) passes.

### [ ] T7 — Tag `v1-monolito` (~5 min, after the PR is merged)

- **Layer:** build
- **Files:** —
- **Depends on:** the user merges the PR
- **What to do:**
  1. With the user's go-ahead: `git fetch`, check `./mvnw verify` on `origin/Monolithic`, then `git tag -a v1-monolito origin/Monolithic -m "Fase 1 · Monolito en capas"` and `git push origin v1-monolito`.
- **Acceptance criteria:**
  - [ ] `git ls-remote --tags origin v1-monolito` points to the merge commit of this PR.
  - [ ] `./mvnw verify` ends in `BUILD SUCCESS` at that commit.

### [ ] T8 — `Hexagonal` branch as the default (~5 min, after T7)

- **Layer:** build
- **Files:** —
- **Depends on:** T7
- **What to do:**
  1. With the user's go-ahead: `git push origin origin/Monolithic:refs/heads/Hexagonal`, then `gh repo edit --default-branch Hexagonal`.
  2. Check that the CI runs on `Hexagonal` and that #13 and the milestone are closed (close the milestone if GitHub does not).
- **Acceptance criteria:**
  - [ ] `gh repo view --json defaultBranchRef` returns `Hexagonal`.
  - [ ] `gh issue list --milestone "Fase 1 · Monolito en capas" --state open` is empty.

## Global acceptance criteria

**Specific:**

- [ ] Every issue in the "Fase 1 · Monolito en capas" milestone is closed.
- [ ] Tag `v1-monolito` published and `Hexagonal` branch created from `Monolithic` and set as the default branch.
- [ ] `./mvnw verify` green on `Monolithic` at the tag.
- [ ] `README.md`, `fase-1-capas.md` and `arquitectura-ia.md` describe the final state of phase 1, in English.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (layers: each layer only uses the one below), verified by ArchUnit. Unchanged: no code changes.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a (no new tests).
- [ ] No dependencies added beyond the planned ones.
- [ ] If the API changes: n/a (the API does not change).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a (no new terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Language of the three rewritten documents.** They are in Spanish, and the project agreed to translate existing docs in a separate issue. This plan translates `README.md`, `fase-1-capas.md` and `arquitectura-ia.md` now, because they are rewritten anyway and the tag freezes them. The alternative is to keep them in Spanish and leave everything to the translation issue.
- **T7 and T8 happen after the merge.** `implement-plan` stops when it opens the PR; the tag must point to the merge commit, so T7-T8 are done once the user merges, and each one asks for the go-ahead first (they publish to the repository).
- **Default branch change.** Once `Hexagonal` is the default, new PRs and the `claude.yml` / `claude-code-review.yml` workflows run from it, and `gh pr create` without `--base` targets it. `Monolithic` keeps its CI. The status written in T6 assumes this change is done.
- **The project status points to `Hexagonal` before it exists.** Step 6.3 writes the status as it will be after the merge; between the merge and T8 it is ahead by a few minutes.
