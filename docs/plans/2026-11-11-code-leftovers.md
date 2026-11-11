# Plan: Code leftovers — transaction status in the views and withdrawals evaluated before they are recorded

- **Issue:** [#82](https://github.com/dawnrock/Proyect-DDD-Claude/issues/82)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-11
- **Status:** done
- **Total estimate:** ~35 min (4 tasks)

## Goal

Close the two code items of `docs/ia/pendientes.md` (the configuration ones go to #79):

1. **Status in the views (#7 review).** The web and the CLI compare status literals: `"REJECTED".equals(...)` in `AccountWebController` (withdrawal and transfer notices), `status() == 'REJECTED'` / `'APPROVED'` in `detail.html`, and `"APPROVED".equals(...)` three times in `DomainModelRunner`. The `application` views `TransactionSummary` and `TransferSummary` will offer `approved()` and `rejected()`, and the adapters will use them.
2. **Withdrawal evaluated before it is recorded (#6 review).** `CheckingAccount.tryWithdraw` records the transaction and then evaluates `isAllowed`; on an account not in EUR, a high net worth customer would get a `CurrencyMismatchException` (the overdraft limit is in EUR) with a `PENDING` transaction already recorded. The evaluation moves before the recording.

What must not change: the web texts, the `[demo]` trace, the REST responses (they keep the `status` string), R5-R9.

### Out of scope

- Accounts in other currencies (the reason item 2 cannot happen today).
- The accepted notes: synchronous event consumers (#17) and the login limitations (#50).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `CheckingAccount.java` | `tryWithdraw` evaluates `isAllowed` before `register` |
| `application` | `TransactionSummaryDef.java`, `TransferSummaryDef.java` + tests | `default boolean approved()` / `rejected()` derived from `status()` (not Immutables attributes: no change to builders or JSON) |
| `adapter.in.web` | `AccountWebController.java`, `templates/accounts/detail.html` | Use `rejected()` / `approved()` |
| `adapter.in.cli` | `DomainModelRunner.java` | Use `approved()` |
| docs | `docs/ia/*` | Status |

**Testing item 2.** The branch cannot be reached today: no account can be opened in a currency other than EUR (the minimum deposit is in EUR, so `open` throws first). The change is a reordering covered by the existing R5-R9 tests as characterization tests; T1 proves those tests still catch a broken reordering (sabotage: evaluating with the wrong balance).

## Tasks

### [x] T1 — Withdrawal evaluated before it is recorded (~10 min)

- **Layer:** domain
- **Files:** `domain/model/CheckingAccount.java`
- **Depends on:** —
- **What to do:**
  1. Characterization: run `CheckingAccountTests` (R5-R9) and the transfer tests; they pass.
  2. Compute `allowed = isAllowed(newBalance)` before `register(movement)`; then approve or reject as today. Javadoc: why the order matters.
  3. Sabotage: evaluate with the current balance instead of the new one; R6/R8 tests fail; revert.
- **Acceptance criteria:**
  - [x] Domain tests green, red with the sabotage; `check-domain.mjs` without errors.

### [x] T2 — `approved()` and `rejected()` in the views (~5 min)

- **Layer:** application
- **Files:** `application/TransactionSummaryDef.java`, `TransferSummaryDef.java`, a test in `application`
- **Depends on:** —
- **What to do:**
  1. Tests first: a summary with status `APPROVED` is `approved()` and not `rejected()`; `REJECTED` the other way round; `PENDING` neither.
  2. `default` methods on both `*Def` interfaces (Immutables does not treat a non-annotated default method as an attribute).
- **Acceptance criteria:**
  - [x] Tests green; `JacksonImmutablesTests` and the REST tests unchanged and green.

### [x] T3 — Adapters use them (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/web/AccountWebController.java`, `templates/accounts/detail.html`, `adapter/in/cli/DomainModelRunner.java`
- **Depends on:** T2
- **What to do:**
  1. Replace every status literal comparison in `adapter.in` with `approved()` / `rejected()`. The existing web and CLI tests are the characterization (same notices, same rejected style, same `[demo]` trace).
  2. Grep: no `"REJECTED"` / `"APPROVED"` comparison left in `adapter.in` (the REST DTO keeps the string field).
- **Acceptance criteria:**
  - [x] `AccountWebControllerTests`, `DomainModelRunnerTests` and `DemoProfileTests` green; the grep finds no comparison.

### [x] T4 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1-T3
- **What to do:**
  1. Status: #82 done; next step decided with the user (no open issues left); `pendientes.md` loses the two items; decision (views expose `approved()`/`rejected()`, adapters never compare status strings); a row in `historial.md`.
- **Acceptance criteria:**
  - [x] The CI check of the project status passes.

### [x] T5 — Review fixes (~2 min)

- **Layer:** tests
- **Files:** `StatusViewsTests.java`
- **Depends on:** T4
- **What to do:**
  1. `revisor-ddd` found no errors (the reordering keeps R5-R9 and the events; the default methods are not Immutables attributes; no status literal comparison left except `PENDING`, which has no method). Applied: import order in `StatusViewsTests`. The missing direct test for the reordering is already explained in this plan and in the Javadoc of `tryWithdraw`.
- **Acceptance criteria:**
  - [x] `mvn verify` green.

## Global acceptance criteria

**Specific:**

- [x] No adapter compares status literals; tests of the views and the adapters green.
- [x] The withdrawal is evaluated before it is recorded; R5-R9 unchanged.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR).
- [x] If the API changes: n/a (REST keeps the `status` string).
- [x] Any new business term is in `docs/dominio/glosario.md` (n/a: no new terms).
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **No direct test for item 2:** the case needs an account in another currency, which the model does not allow. The reordering is protected by the R5-R9 tests and their sabotage; a direct test would come with multi-currency accounts.
- **Order with #79:** independent; #79 is planned first because the status already pointed to it.
