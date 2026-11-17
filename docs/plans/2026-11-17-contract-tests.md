# Plan: Contract tests — the REST API must keep to docs/api/openapi.yaml

- **Issue:** [#107](https://github.com/dawnrock/Proyect-DDD-Claude/issues/107)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-17
- **Status:** in progress
- **Total estimate:** ~45 min (6 tasks)

## Goal

Nothing checks today that the implementation keeps to the contract; #108 and #113 showed it drifts. Add automatic contract tests at the level recommended in the issue for the hexagonal architecture: the **whole hexagon as a black box** (the real application, demo profile, over HTTP) with Schemathesis 4.33.4, run in the CI on every PR and runnable locally with one command. The adapter-level option (`swagger-request-validator` in the `@WebMvcTest`s) stays out, as an optional later step.

### Out of scope

- Adapter-level validation in the `@WebMvcTest`s (new test dependency, its own ADR).
- Test data for `POST /api/transfers` beyond the demo profile: Schemathesis warns that it mostly gets 404 (it invents account ids). It is a warning, not a failure; noted in `pendientes.md`.
- The English `title` of 415 (#109/#110).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build (test config) | `schemathesis.toml` (root, new) | Seed, deterministic generation, examples per operation, HTTP Basic `admin`/`demo`, and the statuses that are legitimate for schema-valid data |
| scripts | `scripts/contract-test.mjs` (new), `scripts/README.md` | Starts the built jar with the `demo` profile, waits for `/actuator/health`, runs Schemathesis, stops the app, returns Schemathesis' exit code |
| CI | `.github/workflows/ci.yml` | After `./mvnw verify`: Python, `pip install schemathesis==4.33.4`, `node scripts/contract-test.mjs` |
| docs | `docs/adr/0013-contract-tests-schemathesis.md`, `docs/adr/README.md` | Why black-box contract tests at hexagon level, with Schemathesis in the CI and not as a Maven dependency |
| Claude Code (approved by the user, 2026-11-17) | `CLAUDE.md`, `.claude/skills/implement-plan/SKILL.md`, `.claude/skills/plan-feature/assets/template.md` | The command in "Comandos"; `implement-plan` runs it in its global criteria when the plan touches the API; the plan template's fixed API criterion mentions it |

Decisions taken while planning (clear and reversible, so not asked):

- **Legitimate rejections are declared, not disabled.** `positive_data_acceptance` keeps running with its default statuses plus `422` (business rules the schema cannot express); only `openAccount` also accepts `400` (the #103 cross-field rule: an existing customer's document with different data). Every other check stays on with its defaults.
- **Deterministic run** (`seed` + `generation.deterministic`): the CI must not turn red because a new random case appeared; new cases are explored by changing the seed on purpose. `max-examples = 40`, about a minute.
- **Schemathesis is a CI tool, like Redocly**: installed with `pip` at a fixed version, not added to `pom.xml`.

## Tasks

### [x] T1 — Schemathesis configuration (~10 min)

- **Layer:** build
- **Files:** `schemathesis.toml`
- **Depends on:** —
- **Deviation:** the first run still reported a `ST202` on `signUp`: a DNI/NIE with a wrong control letter (mod 23) answers 400, a rule a regex cannot check and the contract documents in text. Declared like `openAccount`'s 400. Result: 0 failures in two runs (872 and 870 cases); two warnings left (transfers test data, `signUp` mostly rejected generated data, because random documents rarely have a valid letter).
- **What to do:**
  1. Write `schemathesis.toml`: `seed`, `[generation] deterministic = true, max-examples = 40`, `[auth.basic]` with `admin`/`demo` (practice password, already public in the README), `[checks.positive_data_acceptance] expected-statuses` = defaults + `422`, and an `[[operations]]` entry for `include-operation-id = "openAccount"` adding `400`. A comment in Spanish per decision.
  2. Run it by hand against the app with the `demo` profile.
- **Acceptance criteria:**
  - [x] Schemathesis ends with no failures (`ST202` for 422 and the #103 400 no longer reported) and the same result in two runs.

### [x] T2 — `contract-test.mjs` script (~10 min)

- **Layer:** build
- **Files:** `scripts/contract-test.mjs`, `scripts/README.md`
- **Depends on:** T1
- **What to do:**
  1. Node script without npm dependencies (like the others): finds `target/*.jar` (fails with a clear message if `./mvnw verify` was not run), starts it with `--spring.profiles.active=demo --server.port=<CONTRACT_TEST_PORT, 8080>`, waits up to 90 s for `/actuator/health` `UP`, runs `${SCHEMATHESIS:-schemathesis} run docs/api/openapi.yaml --url http://localhost:<port>`, stops the app always, and exits with Schemathesis' code.
  2. Document it in `scripts/README.md` (requirements: Java 21, built jar, `pip install schemathesis==4.33.4`).
  3. Check it can fail: temporarily put `exclusiveMinimum: 0` back on `MovementRequest.amount` in the contract, see it fail (`ST202`, `0.001` → 400), revert.
- **Acceptance criteria:**
  - [x] `node scripts/contract-test.mjs` exits 0 on the current code and non-zero with the sabotage; the app is stopped in both cases.

### [x] T3 — Run it in the CI (~5 min)

- **Layer:** build
- **Files:** `.github/workflows/ci.yml`
- **Depends on:** T2
- **What to do:**
  1. After "Build y tests": `actions/setup-python@v5` (3.12), `pip install schemathesis==4.33.4`, step "Contrato frente a la implementación (Schemathesis)" running `node scripts/contract-test.mjs`. The Telegram failure notice already covers it.
- **Acceptance criteria:**
  - [ ] The PR's `verify` job runs the step and passes (checked on the PR).

### [x] T4 — ADR 0013 (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0013-contract-tests-schemathesis.md`, `docs/adr/README.md`
- **Depends on:** T3
- **What to do:**
  1. Context (#108/#113 drifts, course chapter 9), decision (black box at hexagon level, Schemathesis in the CI, config in `schemathesis.toml`, legitimate statuses declared per check and operation, deterministic), alternatives (adapter level with `swagger-request-validator`; Prism proxy; Schemathesis via Docker), consequences (one more minute of CI, Python in the runner, the contract cannot express business rules).
- **Acceptance criteria:**
  - [x] ADR in English, listed in the ADR index.

### [x] T5 — `CLAUDE.md` and skills (~5 min)

- **Layer:** docs
- **Files:** `CLAUDE.md`, `.claude/skills/implement-plan/SKILL.md`, `.claude/skills/plan-feature/assets/template.md`
- **Depends on:** T2
- **What to do:**
  1. `CLAUDE.md`, "Comandos": `node scripts/contract-test.mjs   # pruebas de contrato (Schemathesis) contra el jar; requiere ./mvnw verify y schemathesis instalado`.
  2. `implement-plan`, step 6.1: if the plan touches the API, also `node scripts/contract-test.mjs`.
  3. Plan template, fixed API criterion: add the contract test.
- **Acceptance criteria:**
  - [x] The three files mention the command consistently.

### [ ] T6 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/arquitectura-ia.md`
- **Depends on:** T5
- **What to do:**
  1. `decisiones.md`: contract tests at hexagon level (ADR 0013) and the declared statuses.
  2. `pendientes.md`: replace the #107 note with what is left (transfers test data; adapter-level validation as an optional step).
  3. `arquitectura-ia.md`: the CI row mentions the contract tests.
- **Acceptance criteria:**
  - [ ] Annexes updated.

## Global acceptance criteria

**Specific:**

- [ ] A PR whose implementation drifts from `openapi.yaml` fails in the CI (shown by the T2 sabotage locally and by the green run on the PR).

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish) (n/a: no JUnit tests; the contract test is the new check).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR): `pom.xml` unchanged; Schemathesis is a CI tool justified in ADR 0013.
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors (n/a: the API does not change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **CI time:** about one more minute per PR (start the jar, ~600 cases).
- **Deterministic seed:** it will not find new random cases by itself; changing the seed is a deliberate act, and a new finding is fixed (or declared) in its own issue.
- **Demo data:** the run creates accounts and customers in the in-memory app it starts; nothing persists.
