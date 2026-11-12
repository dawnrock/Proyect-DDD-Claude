# Plan: The hexagonal web footer names phase 2

- **Issue:** [#89](https://github.com/dawnrock/Proyect-DDD-Claude/issues/89)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-12
- **Status:** draft
- **Total estimate:** ~15 min (2 tasks)

## Goal

**Current behaviour.** Every page of the hexagonal web (now public on Render, #85) ends with `Proyect-DDD-Claude · fase 1 · monolito en capas`: the footer text in `templates/fragments/layout.html` was written in phase 1 and never updated on `Hexagonal`. No test covers it.

**Expected behaviour.** The footer says `Proyect-DDD-Claude · fase 2 · arquitectura hexagonal` (Spanish, like the rest of the web texts), and a web test checks it. `Monolithic` keeps its footer (correct there, branch frozen).

The same PR also brings the `Hexagonal` project status up to date with #86 (merged in PR #88 against `Monolithic`), as #61 did for #13.

### Out of scope

- Any other web text or style.
- Changing `Monolithic`.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in.web` | `templates/fragments/layout.html` | Footer text |
| tests | `AccountWebControllerTests.java` | The list page shows the phase-2 footer |
| docs | `docs/ia/*` | #86 done; #89 done |

## Tasks

### [ ] T1 — Footer names phase 2 (~5 min)

- **Layer:** adapter.in (web)
- **Files:** `templates/fragments/layout.html`, `AccountWebControllerTests.java`
- **Depends on:** —
- **What to do:**
  1. Test first (red): the list page contains `fase 2 · arquitectura hexagonal` and not `fase 1`.
  2. Change the footer text (green).
- **Acceptance criteria:**
  - [ ] The test fails before the change and passes after it; the other web tests stay green.

### [ ] T2 — Project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `historial.md`
- **Depends on:** T1
- **What to do:**
  1. Status: #86 done (PR #88, the only change allowed on `Monolithic`), #89 done; next step: decide with the user. A row in `historial.md` for PR #88 (on `Monolithic`) and one for this PR.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] The hexagonal web footer says phase 2, checked by a test.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a.
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Redeploy:** once merged, Render's auto-deploy publishes the fix on the hexagonal service (a few minutes; the demo data is rebuilt).
