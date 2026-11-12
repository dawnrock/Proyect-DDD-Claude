# Plan: The web footer shows only the phase

- **Issue:** [#91](https://github.com/dawnrock/Proyect-DDD-Claude/issues/91)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone); also `Monolithic`
- **Base branch:** `Hexagonal`, then `Monolithic` (second PR)
- **Date:** 2026-11-12
- **Status:** in progress
- **Total estimate:** ~30 min (4 tasks)

## Goal

Asked by the user on 2026-11-12, after seeing both Render services: the web footer does not show the project name, only the phase and its architecture.

| Branch | Current footer | New footer |
| ------ | -------------- | ---------- |
| `Hexagonal` | `Proyect-DDD-Claude · fase 2 · arquitectura hexagonal` (#89) | `Fase 2 · arquitectura hexagonal` |
| `Monolithic` | `Proyect-DDD-Claude · fase 1 · monolito en capas` | `Fase 1 · arquitectura monolítica` |

**`Monolithic` is frozen.** This needs a second exception (the first was the deployment, #86), and this one changes `src/` (one template and one web test). Approving this plan is asked as that authorisation. The `v1-monolito` tag does not move: it keeps the original snapshot.

### Out of scope

- Any other web text or style.

## Context

| Branch | File | Change |
| ------ | ---- | ------ |
| `Hexagonal` | `templates/fragments/layout.html`, `adapter/in/web/AccountWebControllerTests.java` | Footer text; the #89 test checks the new text and the absence of the project name |
| `Monolithic` | `templates/fragments/layout.html`, `presentation/web/AccountWebControllerTests.java` | Footer text; new web test |
| both | `docs/ia/*` | Status (on `Monolithic`, in Spanish like its annexes) |

Two PRs, one per branch, because each branch has its own template. The `Monolithic` PR does not close the issue on its own (decision of #89): it is closed by hand after both merges.

## Tasks

### [x] T1 — `Hexagonal`: footer without the project name (~5 min)

- **Layer:** adapter.in (web)
- **Files:** `templates/fragments/layout.html`, `AccountWebControllerTests.java`
- **Depends on:** —
- **What to do:**
  1. Test first (red): the list page contains `Fase 2 · arquitectura hexagonal` and not `Proyect-DDD-Claude` nor `fase 1`.
  2. Change the footer (green).
- **Acceptance criteria:**
  - [x] Red before the change, green after; `mvn verify` green (the #89 test now checks the new text and the absence of the project name).

### [ ] T2 — `Hexagonal`: status and PR (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `historial.md`
- **Depends on:** T1
- **What to do:**
  1. Status: #91 in progress (the `Monolithic` PR follows); decision (second exception on `Monolithic`, authorised); a row in `historial.md`. PR against `Hexagonal` with `Refs #91` (not `Closes`: the issue stays open until the `Monolithic` PR).
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

### [ ] T3 — `Monolithic`: footer `Fase 1 · arquitectura monolítica` (~10 min)

- **Layer:** presentation (web)
- **Files:** `templates/fragments/layout.html`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T2 merged
- **What to do:**
  1. From a branch based on `origin/Monolithic`. Test first (red): the list page contains `Fase 1 · arquitectura monolítica` and not `Proyect-DDD-Claude`.
  2. Change the footer (green); `mvn verify` green (layers, ArchUnit).
  3. `git diff --name-only origin/Monolithic...HEAD` lists only the template, the test, the plan and `docs/ia/*`.
- **Acceptance criteria:**
  - [ ] Red before, green after; only the listed files change.

### [ ] T4 — `Monolithic`: status, PR and closing the issue (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `historial.md` (on `Monolithic`, in Spanish)
- **Depends on:** T3
- **What to do:**
  1. Status: second exception recorded; history row. PR against `Monolithic`. After its merge: close #91 by hand; the `Hexagonal` status catches up in its next PR.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes on the PR against `Monolithic`.

## Global acceptance criteria

**Specific:**

- [ ] `Hexagonal` shows `Fase 2 · arquitectura hexagonal`, `Monolithic` shows `Fase 1 · arquitectura monolítica`; neither shows the project name. Each checked by a test.
- [ ] `v1-monolito` does not move.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit) on both branches.
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected, verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a.
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English (the `Monolithic` annexes keep their Spanish).
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Second exception on `Monolithic`, this time in `src/`:** it needs the user's explicit approval. The phase-1 code stays as tagged in `v1-monolito`; only the branch moves.
- **Accent:** the plan writes `monolítica` (with accent, like the rest of the Spanish web texts).
- **Redeploy:** each merge redeploys its Render service.
