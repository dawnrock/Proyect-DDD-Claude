# Plan: validate() of the opening and sign-up commands without excess cyclomatic complexity

- **Issue:** [#181](https://github.com/dawnrock/Proyect-DDD-Claude/issues/181)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-25
- **Status:** in progress
- **Total estimate:** ~40 min (6 tasks)

## Goal

The PMD baseline (#168, ADR 0020) flags `CyclomaticComplexity` 11 in `OpenAccountCommandDef.validate()` and `SignUpCommandDef.validate()`: five or six `if (…) throw` in a row, with the same check («text matches a pattern or throw this message») written seven times and the invalid-currency message in three places. Named checks in `CommandValidation` turn each `validate()` into a list of rules (Clean Code ch. 3, 17 and 22), and both classes leave the baseline.

**What must not change:** the rules, the messages, their order (the first failing rule is the one reported), which fields are optional, and the answers of the REST API, the web and the CLI.

### Out of scope

- `CheckingAccount` (#182) and the other commands.
- `Monolithic` (frozen).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `application` | `CommandValidation.java` | Named checks: `requireMatching`, `requireMatchingIfPresent`, `requireNotBlank`, `requireIsoCurrency` (the currency message, once) |
| `application` | `OpenAccountCommandDef.java`, `SignUpCommandDef.java` | `validate()` as a list of those checks |
| tests | `SignUpCommandTests.java` (new), `OpenAccountCommandTests.java` | Characterization: each rule of the sign-up command, and the order of the rules in both |
| build | `pmd-baseline.properties` | Both lines out |
| docs | `docs/ia/*` | Status, pending item updated, history |

`SignUpCommand` has no unit tests of its own today (its rules are only reached through `SignUpControllerTests`), and no test fixes the order of the rules in either command. Both gaps are closed first, so the refactor runs against a safety net.

`CommandValidation` is package-private and stays so. PMD's default threshold for a method is 10; the new `validate()` methods have no branches of their own.

## Tasks

### [x] T1 — Characterization: rules of the sign-up command (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/dawnrock/ddd/application/SignUpCommandTests.java`
- **Depends on:** —
- **What to do:**
  1. One test per rule with its exact message: blank full name, malformed document, malformed e-mail, malformed username, non-ISO currency, initial deposit that rounds to zero or is above the maximum; and a valid command builds with `EUR` by default.
  2. They pass at once (characterization): break each rule temporarily in `SignUpCommandDef` to see the red, then revert.
- **Acceptance criteria:**
  - [x] Every rule of `SignUpCommandDef.validate()` has a test with its message; sabotage seen red and reverted (each rule broken alone fails all its tests).

### [x] T2 — Characterization: order of the rules (~5 min)

- **Layer:** tests
- **Files:** `SignUpCommandTests.java`, `OpenAccountCommandTests.java`
- **Depends on:** T1
- **What to do:**
  1. A command with every field wrong reports the first rule; fixing that field reports the next one, and so on (sign-up: name, document, e-mail, username, currency, deposit; opening: document, e-mail, owner username, currency, deposit).
  2. Sabotage: swap two `if`s temporarily, see the red, revert.
- **Acceptance criteria:**
  - [x] The order of both commands is fixed by tests; sabotage seen red and reverted.

### [x] T3 — Named checks in CommandValidation (~10 min)

- **Layer:** application
- **Files:** `CommandValidation.java`, `src/test/java/com/dawnrock/ddd/application/CommandValidationTests.java` (new)
- **Depends on:** T2
- **What to do:**
  1. Tests first: `requireMatching(value, pattern, message)` throws `IllegalArgumentException(message)` when the value does not match; `requireMatchingIfPresent` accepts an empty or blank value; `requireNotBlank(value, message)`; `requireIsoCurrency(code)` with the current message.
  2. Write them; `requireValidMovement` uses `requireIsoCurrency`, so its message is defined once.
- **Acceptance criteria:**
  - [x] The new tests pass; `MovementCommandsTests` unchanged and green; the currency message appears once in `src/main` (after T4 and T5). Deviation: `OpenAnotherAccountCommandDef` also wrote the message, so it uses `requireIsoCurrency` too, with a new `OpenAnotherAccountCommandTests` that passes before and after the change.

### [x] T4 — validate() of the opening command (~5 min)

- **Layer:** application
- **Files:** `OpenAccountCommandDef.java`
- **Depends on:** T3
- **What to do:**
  1. `validate()` becomes five calls in the same order: document, e-mail if present, owner username if present, currency, initial deposit.
- **Acceptance criteria:**
  - [x] `OpenAccountCommandTests` (with T2's order test) and the web/REST tests green without changes. While doing it, a characterization test caught that the old code treats a blank e-mail as not given but validates a blank owner username: `requireMatchingIfPresent` skips only the empty value and `requireMatchingUnlessBlank` also the blank one. The test passes on the old code and failed on the first version of the new one.

### [x] T5 — validate() of the sign-up command (~5 min)

- **Layer:** application
- **Files:** `SignUpCommandDef.java`
- **Depends on:** T3
- **What to do:**
  1. `validate()` becomes six calls in the same order: name, document, e-mail, username, currency, initial deposit.
- **Acceptance criteria:**
  - [x] `SignUpCommandTests`, `SignUpControllerTests` and `CustomerControllerTests` green without changes. Committed together with T4: the opening command uses the new `SignUpCommandDef.EMAIL_MESSAGE`, so the e-mail message is also defined once.

### [x] T6 — Out of the PMD baseline and project status (~5 min)

- **Layer:** build · docs
- **Files:** `pmd-baseline.properties`, `docs/ia/estado-del-proyecto.md`, `pendientes.md`, `historial.md`
- **Depends on:** T4, T5
- **What to do:**
  1. Remove both lines (and their comment); `./mvnw verify` runs PMD without them and stays green.
  2. Status files: issue done, the PMD baseline pending item keeps only `CheckingAccount` (#182), history row.
- **Acceptance criteria:**
  - [x] `pmd-baseline.properties` has no line for the two commands and `./mvnw verify` is green (sabotage: with the old `validate()` PMD fails with complexity 11 in both).

## Global acceptance criteria

**Specific:**

- [ ] Neither `validate()` is above PMD's `CyclomaticComplexity` threshold; both lines are out of the baseline.
- [ ] The invalid-currency message is defined once.
- [ ] Same rules, messages and order: the characterization tests of T1-T2 and the existing web, REST and CLI tests pass unchanged.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: … (n/a: no API change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: no new term).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Immutables and `@Value.Check`:** the generated builder calls `validate()`; the checks stay `default` methods calling static helpers, so nothing changes in the generated code.
- No open questions: the issue fixes the behaviour, and the helpers' names are internal (package-private).
