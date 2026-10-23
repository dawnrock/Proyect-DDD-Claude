# Plan: <short title>

- **Issue:** [#N](<issue URL>)
- **Type:** feature | fix | refactor
- **Phase:** 1 · Layered monolith | 2 · Hexagonal architecture
- **Base branch:** `Monolithic` | `Hexagonal`
- **Date:** YYYY-MM-DD
- **Status:** draft
- **Total estimate:** ~N min (N tasks)

## Goal

<!-- What we want to achieve and why, in 2-4 sentences. Fix: current vs. expected behaviour. Refactor: what improves and what must NOT change. -->

### Out of scope

- ...

## Context

<!-- Affected files and layers, DDD building blocks involved (VO, entity, aggregate, event, factory, service, port, adapter) and technical decisions. -->

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `src/main/java/com/pronoide/ddd/domain/model/...` | ... |

## Tasks

<!-- Each task: 5-10 min, one layer, 1-2 files + test, leaves ./mvnw verify green. Inside out.
     If the issue touches back and web: start with "T0 — Contract" (Side: both, see implement-plan-team/assets/contract-template.md),
     add **Side:** to every task and make back and web tasks depend only on T0. -->

### [ ] T0 — Contract (~10 min) <!-- only if it touches back and web; otherwise delete it -->

- **Side:** both
- **Layer:** application · docs
- **Files:** `docs/contracts/<slug>.md`, contract types in `application`, fragment of `docs/api/openapi.yaml`
- **Depends on:** —
- **What to do:**
  1. Write the contract and create the types without logic; backend-dev and web-dev review and agree on it.
- **Acceptance criteria:**
  - [ ] Contract agreed and frozen in a commit; `./mvnw verify` compiles against it.

### [ ] T1 — <title> (~5 min)

- **Side:** back | web | both <!-- only if the plan has T0 · Contract -->
- **Layer:** domain | application | persistence | presentation | adapter.in | adapter.out | config | tests | docs | build
- **Files:** `src/...`
- **Depends on:** —
- **What to do:**
  1. ...
- **Acceptance criteria:**
  - [ ] ...

### [ ] T2 — <title> (~10 min)

- **Layer:** ...
- **Files:** `src/...`
- **Depends on:** T1
- **What to do:**
  1. ...
- **Acceptance criteria:**
  - [ ] ...

## Global acceptance criteria

**Specific:**

- [ ] ...

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (layers: each layer only uses the one below / hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain does not depend on Spring, `jakarta` or `org.immutables`, and does not use `@Data`, `@Setter`, `@Builder` or `@AllArgsConstructor` (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors (otherwise, "n/a").
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English.
- [ ] `docs/ia/estado-del-proyecto.md` updated: issue status, next step, new pending items and history row.

## Risks and open questions

- ...
