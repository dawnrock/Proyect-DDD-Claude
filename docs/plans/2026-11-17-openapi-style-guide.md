# Plan: The project's own OpenAPI style guide in redocly.yaml

- **Issue:** [#109](https://github.com/dawnrock/Proyect-DDD-Claude/issues/109)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-17
- **Status:** draft
- **Total estimate:** ~40 min (5 tasks)

## Goal

The CI lints `docs/api/openapi.yaml` with Redocly's recommended rules only; `redocly.yaml` has no rule of its own. Following the OpenAPI course (chapters 7.4 and 8.8), turn the project's conventions into Redocly rules that run in the same CI step, document them in a short style guide, and make the contract comply.

### Out of scope

- The English `title` of 415 and documenting 415 in the contract (noted in `pendientes.md` for #109/#110): it changes `RestExceptionHandler` and the API, which a style guide does not; it goes with #110, which completes the contract.
- Changing `plan-feature` (`.claude/`): the guide is enforced by the existing `redocly lint` step, which the skill already asks for, so no skill change is needed.
- Response examples, servers (#110).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| docs (contract) | `redocly.yaml` | Built-in rules raised to `error` and `rule/*` assertions (all tried on a copy of the contract while planning) |
| docs (contract) | `docs/api/openapi.yaml` | `info.contact`; the 22 `example` inside schemas become `examples` lists |
| docs | `docs/api/style-guide.md` (new) | The conventions and which rule enforces each |

Rules (each one validated by sabotage when it is added):

| Rule | Kind | Checks |
| ---- | ---- | ------ |
| `operation-2xx-response`, `operation-4xx-response`, `operation-summary`, `operation-tag-defined` | built-in, raised to `error` | A success and an error response, a summary and a declared tag on every operation |
| `rule/operation-id-camel-case` | assertion | `operationId` in camelCase |
| `rule/one-tag` | assertion | Exactly one tag per operation |
| `rule/request-body-required` | assertion | Every `requestBody` is `required: true` |
| `rule/errors-are-problem-details` | assertion | 4XX responses use `application/problem+json`, never `application/json` (RFC 9457) |
| `rule/contact-required` | assertion | `info.contact` is defined |
| `rule/no-schema-example` | assertion | No `example` inside schemas (deprecated in 3.1, use `examples`) |

Decision taken while planning (clear and reversible, so not asked): `info.contact` names the project and points to its GitHub issues (`https://github.com/dawnrock/Proyect-DDD-Claude/issues`), with no personal e-mail; the course's "corporate e-mail" rule does not apply to a practice project.

No Java change, no new dependency (Redocly already runs with `npx` at a fixed version).

## Tasks

### [ ] T1 — Rules the contract already meets (~10 min)

- **Layer:** docs
- **Files:** `redocly.yaml`
- **Depends on:** —
- **What to do:**
  1. Raise the four built-in rules to `error` and add `rule/operation-id-camel-case`, `rule/one-tag`, `rule/request-body-required` and `rule/errors-are-problem-details`, each with a message in English.
  2. Validate each one by sabotage on a temporary copy of the contract (a snake_case `operationId`, a second tag, `required: false`, an error response in `application/json`, an operation without a 4XX), then lint the real contract.
- **Acceptance criteria:**
  - [ ] Each rule fails on its sabotage; the real contract lints without errors.

### [ ] T2 — `info.contact` and its rule (~5 min)

- **Layer:** docs
- **Files:** `redocly.yaml`, `docs/api/openapi.yaml`
- **Depends on:** T1
- **What to do:**
  1. Add `rule/contact-required` (fails today: red) and `info.contact` (`name`, `url`) to the contract (green).
- **Acceptance criteria:**
  - [ ] Red before the contact, green after it.

### [ ] T3 — `examples` instead of `example` in schemas (~10 min)

- **Layer:** docs
- **Files:** `redocly.yaml`, `docs/api/openapi.yaml`
- **Depends on:** T2
- **What to do:**
  1. Add `rule/no-schema-example` (22 errors today: red).
  2. Turn each `example: <value>` inside a schema into `examples: [<value>]` (the `example` of media types and responses stays, it is not deprecated there).
  3. Lint (green) and run `node scripts/contract-test.mjs`: Schemathesis uses schema examples in its examples phase.
- **Acceptance criteria:**
  - [ ] Redocly without errors; contract tests pass.

### [ ] T4 — Style guide document (~10 min)

- **Layer:** docs
- **Files:** `docs/api/style-guide.md`, `docs/api/openapi.yaml` (one line in `info.description` pointing to it)
- **Depends on:** T3
- **What to do:**
  1. A short guide in English: resources in plural and nouns, `operationId` camelCase, one tag, errors as `ProblemDetail` with the Spanish `title` values, `201` + `Location` on creation, examples in `examples`, contact, and the table "convention → rule in `redocly.yaml`", plus the conventions no rule checks (reviewed by people, as the course says).
- **Acceptance criteria:**
  - [ ] Guide written and linked from the contract; Redocly without errors.

### [ ] T5 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`, `docs/ia/pendientes.md`
- **Depends on:** T4
- **What to do:**
  1. `decisiones.md`: the style guide and where it is enforced. `pendientes.md`: keep the 415 note, now for #110.
- **Acceptance criteria:**
  - [ ] Annexes updated.

## Global acceptance criteria

**Specific:**

- [ ] A contract change that breaks a convention of the guide fails the CI's existing Redocly step.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish) (n/a: no JUnit tests).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes (the API does not change, but the contract document does: both are run).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Redocly version:** the rules use assertion features of `@redocly/cli` 2.71.0 (`matchParentKeys`, `where`); the CI pins that version, so an upgrade must re-run the sabotages.
- **Scope of `rule/errors-are-problem-details`:** it covers 4XX only; a documented 5XX would need its own rule (none is documented today).
