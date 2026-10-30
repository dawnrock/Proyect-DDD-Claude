# Plan: Reorganise the packages for the hexagonal architecture

- **Issue:** [#14](https://github.com/dawnrock/Proyect-DDD-Claude/issues/14)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-10-30
- **Status:** in progress
- **Total estimate:** ~50 min (6 tasks)

## Goal

Make the package structure say what the hexagon is and what the adapters are (Lab 27.4): `presentation` becomes `adapter.in`, `persistence` becomes `adapter.out.persistence`, and `domain` and `application` stay where they are. It is a pure move: **behaviour must not change** (same tests, same API, same screens), and `git mv` keeps the history of every moved class.

### Out of scope

- Ports: `CheckingAccountRepository` (#15), `CheckingAccountCommands`/`CheckingAccountQueries` (#16). Until #15, `application` still uses the in-memory repositories directly, now from `adapter.out.persistence`.
- `config` and wiring without Spring in the core (#20), events (#17), factory with ports (#18), CLI adapter (#19).
- `HexagonalArchitectureTests` (#22): until then `LayeredArchitectureTests` only has its package patterns adjusted.
- Phase 1 documents (`fase-1-capas.md`, ADR 0004, contracts, decisions): they describe the frozen `v1-monolito` snapshot and keep the old package names on purpose.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `persistence` → `adapter.out.persistence` | `InMemoryCheckingAccountRepository`, `InMemoryAccountOwnershipRepository` and their tests | `git mv` + package and imports |
| `presentation.rest` → `adapter.in.rest` | 12 classes + 5 tests (including `support/FailingTestController`) | `git mv` + package and imports |
| `presentation.web` → `adapter.in.web` | 10 classes + 3 tests | `git mv` + package and imports |
| `presentation` (root) → `adapter.in` (root) | `SecuritySupport`, `CurrentCaller`, `SecurityUsersProperties` + `SecuritySupportTests` | `git mv`: shared by REST and web without either knowing the other, as in phase 1 |
| `application` | `CheckingAccountService` and 3 tests | Imports of the repositories |
| tests | `LayeredArchitectureTests` | Package patterns only (`..adapter.in..`, `..adapter.out.persistence..`); rule names stay until #22 |
| docs | `docs/adr/0006-hexagonal-architecture.md` (new), `docs/arquitectura/fase-2-hexagonal.md` | ADR and the "class → role" table |
| docs | `docs/plans/2026-10-30-close-phase-1.md` | T7 and T8 of #13 checked (done after that PR was merged) |

`.claude/agents/backend-dev.md` and `web-dev.md` already name the phase 2 packages (`adapter.in.rest`, `adapter.in.web`, `adapter.out`), so `.claude/` does not change.

## Tasks

### [x] T1 — Close the #13 plan (~3 min)

- **Layer:** docs
- **Files:** `docs/plans/2026-10-30-close-phase-1.md`
- **Depends on:** —
- **What to do:**
  1. Check T7 and T8 and their criteria, the global criteria that were waiting for them, and set `**Status:** done`. They were done after PR #60 was merged: tag `v1-monolito` on `10eb227`, `Hexagonal` created and set as default, milestone closed.
- **Acceptance criteria:**
  - [x] The #13 plan has no unchecked task or criterion.

### [x] T2 — Pin the current behaviour (~5 min)

- **Layer:** tests
- **Files:** — (this plan, to record the baseline)
- **Depends on:** —
- **What to do:**
  1. Run `./mvnw verify` on `Hexagonal` as it is and write down in this plan the number of tests run, failed and skipped. That suite (JUnit + ArchUnit + MockMvc) is the characterisation of the behaviour.
  2. Check the app: `GET /actuator/health` → `UP` and `curl -u admin:demo …/api/accounts` → 200.
- **Acceptance criteria:**
  - [x] Baseline recorded: `Tests run: 358, Failures: 0, Errors: 0, Skipped: 0` (`LayeredArchitectureTests`: 10). Health `UP`, `GET /api/accounts` as `admin` → 200.

### [x] T3 — ADR 0006: hexagonal architecture (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0006-hexagonal-architecture.md`, `docs/adr/README.md`
- **Depends on:** —
- **What to do:**
  1. Context (the layered monolith of phase 1, Lab 27.4), decision (target packages from `CLAUDE.md`; input ports in `application`, output ports in `domain.model`; adapters depend on the hexagon; `config` wires it without Spring in the core) and consequences.
  2. Record the transitional decisions of #14: the shared security support goes to the `adapter.in` root; `application` keeps using `adapter.out.persistence` until #15; `LayeredArchitectureTests` only adjusts its patterns until #22.
  3. Add it to the ADR index.
- **Acceptance criteria:**
  - [x] The ADR exists, in English, and is listed in `docs/adr/README.md`.

### [x] T4 — `persistence` → `adapter.out.persistence` (~10 min)

- **Layer:** adapter.out
- **Files:** `src/main/java/com/pronoide/ddd/persistence/*` → `adapter/out/persistence/`, same in `src/test`; imports in `application` and its tests; `LayeredArchitectureTests`
- **Depends on:** T2
- **What to do:**
  1. `git mv` both directories (main and test) and update `package` and `import`.
  2. In `LayeredArchitectureTests`, the `Persistence` layer and the other `persistence` patterns become `..adapter.out.persistence..`.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green with the same number of tests as the baseline.
  - [x] `git log --follow --oneline -- src/main/java/com/pronoide/ddd/adapter/out/persistence/InMemoryCheckingAccountRepository.java` shows the commits from before the move.
  - [x] The adjusted layer rule still bites: import `adapter.out.persistence` from `domain`, see the red, revert.

### [ ] T5 — `presentation` → `adapter.in` (~10 min)

- **Layer:** adapter.in
- **Files:** `src/main/java/com/pronoide/ddd/presentation/**` → `adapter/in/**`, same in `src/test`; `LayeredArchitectureTests`
- **Depends on:** T4
- **What to do:**
  1. `git mv` `presentation/rest` → `adapter/in/rest`, `presentation/web` → `adapter/in/web` and the three root classes to `adapter/in` (main and test, `rest/support` included); update `package` and `import`.
  2. In `LayeredArchitectureTests`: `Presentation` layer `..adapter.in..`, D4 and the controllers rule on `..adapter.in..`, slices `..adapter.in.(*)..`, and `toda_clase_pertenece_a_una_capa` with the new packages.
  3. Search `src/main/resources` (properties, templates) for any reference to the old packages.
- **Acceptance criteria:**
  - [ ] `./mvnw verify` green with the same number of tests as the baseline; no `com.pronoide.ddd.presentation` or `com.pronoide.ddd.persistence` left in `src/`.
  - [ ] The REST/web slice rule still fails if `adapter.in.web` imports a class from `adapter.in.rest` (break it, see the red, revert).
  - [ ] `git log --follow` keeps the history of `AccountController` and `AccountWebController`.

### [ ] T6 — `fase-2-hexagonal.md`: class → role table (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`
- **Depends on:** T5
- **What to do:**
  1. Translate the document to English (it is being rewritten; the convention asks for English) and keep the SVG diagram as it is.
  2. Add the table "class → package → role in the architecture" for every main class after #14 (input adapter, output adapter, application service, domain model, domain service, shared adapter support), with a column for the issue that will change its role (#15-#20).
  3. Point to ADR 0006.
- **Acceptance criteria:**
  - [ ] Every class under `src/main/java` appears in the table (grouped by package when there are many: DTOs, forms).
  - [ ] The relative links resolve.

## Global acceptance criteria

**Specific:**

- [ ] Same tests as the baseline of T2, all green: the behaviour has not changed.
- [ ] `git log --follow` keeps the history of the moved classes.
- [ ] No class left in `com.pronoide.ddd.presentation` or `com.pronoide.ddd.persistence`.
- [ ] `curl -u admin:demo http://localhost:8080/api/accounts` returns 200 and `/` renders the account list after login.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit: partially until #15 and #22 (`application` still uses `adapter.out.persistence`; the layered rules are only adjusted to the new packages).
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a (tests are moved, not added).
- [ ] No dependencies added beyond the planned ones.
- [ ] If the API changes: n/a (the API does not change).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a (no new terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Where the shared security support goes.** `SecuritySupport`, `CurrentCaller` and `SecurityUsersProperties` are used by both input adapters. The plan moves them to the `adapter.in` root, as in phase 1. The alternative is `config`, but `config` arrives in #20 with a different job (wiring the core). Decide in the ADR (T3).
- **Hexagonal rule broken on purpose until #15.** After the move, `application` → `adapter.out.persistence` is a dependency from the hexagon to an adapter. It is the reason for #15; the ADR records it so the review does not flag it as a mistake.
- **Moves and history.** The moved files only change their `package` and `import` lines, so Git detects the rename and `--follow` works; any other edit to a moved class waits for a later task.
- **Phase 1 documents keep the old names.** `fase-1-capas.md`, ADR 0004, the contracts and `decisiones.md` describe `v1-monolito`; they are not updated.
