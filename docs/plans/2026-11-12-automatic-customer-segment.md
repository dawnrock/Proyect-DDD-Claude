# Plan: Automatic customer segment

- **Issue:** [#94](https://github.com/dawnrock/Proyect-DDD-Claude/issues/94)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic` (frozen; `src/` exception authorised by the user on 2026-11-12)
- **Date:** 2026-11-12
- **Status:** draft
- **Total estimate:** ~70 min (9 tasks)

## Goal

The customer segment stops being an input chosen by the administrator. The domain decides it: every holder starts as `RETAIL` and the aggregate promotes the holder to `HIGH_NET_WORTH` as soon as an approved credit (opening deposit, deposit or incoming transfer) takes the account balance to the minimum net worth. This is a prerequisite of #95 (customer sign-up), where nobody chooses a segment.

### Out of scope

- Customer sign-up, holder document and e-mail, bank-assigned IBAN (#95).
- Demotion: a holder who falls below the minimum keeps the segment.
- Adding up several accounts of the same person (the holder is an entity inside each `CheckingAccount`).
- The same change on `Hexagonal`: twin issue, created when this plan is approved.

## Context

Today the segment travels from the form or the REST body through `OpenAccountCommand.segment()` to `AccountHolder.register(fullName, segment)`. `CheckingAccount.deposit` is the single place where a balance grows (opening and incoming transfers go through it), so the new rule lives there. `AccountHolder.promoteToHighNetWorth()` already exists and has no inverse.

Decisions taken in this plan (to be confirmed by the user on review):

- **Minimum net worth:** `100000.00 EUR`, constant `MINIMUM_NET_WORTH` in `CheckingAccount`, compared with "balance not less than".
- **One-way promotion**, evaluated after every approved credit.
- **Not a team plan.** The web side is one small task (remove a field), so it is implemented with `/implement-plan`. The contract change (`OpenAccountCommand` loses `segment`) was requested by the user; T8 amends the contract document (ADR 0007). The `equipo` label is removed from the issue.
- **REST:** `segment` disappears from the `POST /api/accounts` body (incompatible change). A client that still sends it is not rejected: Jackson ignores unknown properties by default.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `CheckingAccount.java` | `MINIMUM_NET_WORTH`; promote the holder after an approved credit |
| `domain.model` | `AccountHolder.java` | `register(fullName)`: always `RETAIL` |
| `application` | `OpenAccountCommandDef.java`, `CheckingAccountService.java`, `DemoDataLoader.java` | Remove `segment` and `SEGMENTS`; Grace Hopper opens with a deposit that reaches the minimum |
| `presentation.rest` | `OpenAccountRequest.java`, `AccountController.java` | Remove `segment` from the request |
| `presentation.web` | `OpenAccountForm.java`, `AccountWebController.java`, `templates/accounts/new.html` | Remove the segment field and its options |
| tests | `TestAccounts.java` and the tests listed in each task | `segment(HIGH_NET_WORTH)` promotes the holder instead of registering it with a segment |
| docs | `openapi.yaml`, `contracts/open-query-list-accounts.md`, `dominio/glosario.md`, `analysis/functional-analysis.md`, `ia/*` | New rules R17-R19, term "minimum net worth", project status |

## Tasks

### [ ] T1 — The aggregate promotes the holder at the minimum net worth (~10 min)

- **Layer:** domain
- **Files:** `src/main/java/com/pronoide/ddd/domain/model/CheckingAccount.java`, `src/test/java/com/pronoide/ddd/domain/model/CheckingAccountTests.java`
- **Depends on:** —
- **What to do:**
  1. Write the failing tests first: a deposit that takes the balance to exactly 100.000,00 € promotes the holder (R17); one cent less does not; an account opened with 100.000,00 € is high net worth from the start (R18); after a withdrawal that leaves the balance below the minimum the holder is still high net worth (R19).
  2. Add `MINIMUM_NET_WORTH = MonetaryAmount.euros("100000.00")` and, in `deposit`, after updating the balance, call `primaryHolder.promoteToHighNetWorth()` when the balance is not less than it.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=CheckingAccountTests` passes with the four new tests.
  - [ ] `./mvnw verify` green.

### [ ] T2 — An incoming transfer can promote the target holder (~5 min)

- **Layer:** domain (test only)
- **Files:** `src/test/java/com/pronoide/ddd/domain/service/MoneyTransferServiceTests.java`
- **Depends on:** T1
- **What to do:**
  1. Add a test: a retail target account with 99.900,00 € receives an approved transfer of 100,00 € and its holder becomes high net worth; a rejected transfer does not promote anybody.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=MoneyTransferServiceTests` passes; no change needed in `MoneyTransferService`.

### [ ] T3 — A holder is always registered as retail (~10 min)

- **Layer:** domain (plus the callers needed to compile)
- **Files:** `AccountHolder.java`, `AccountHolderTests.java`, `src/test/java/com/pronoide/ddd/support/TestAccounts.java`, `TestAccountsTests.java`, one line in `CheckingAccountService.java`
- **Depends on:** T1
- **What to do:**
  1. Test first: `AccountHolder.register("Ada Lovelace")` is `RETAIL`. Add the pending test "el nombre del titular es obligatorio" if it is still missing (`pendientes.md`).
  2. Replace `register(String, CustomerSegment)` with `register(String)`.
  3. `TestAccounts`: keep `segment(...)` in the builder; for `HIGH_NET_WORTH` it calls `promoteToHighNetWorth()` on the holder before opening.
  4. `CheckingAccountService.openAccount`: call `register(command.holderName())`; `command.segment()` is ignored from now on (removed in T4). Adjust the service tests that expected a high-net-worth account from the command: open with a deposit that reaches the minimum.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest='AccountHolderTests,TestAccountsTests,CheckingAccountService*'` passes.
  - [ ] `./mvnw verify` green.

### [ ] T4 — `OpenAccountCommand` without segment (~10 min)

- **Layer:** application (plus one line in each presentation mapper to compile)
- **Files:** `OpenAccountCommandDef.java`, `DemoDataLoader.java`, `OpenAccountCommandTests.java`, `DemoDataLoaderTests.java`, `CheckingAccountServiceTests.java`, `CheckingAccountServiceOwnershipTests.java`; mapper lines in `AccountController.java` and `AccountWebController.java`
- **Depends on:** T3
- **What to do:**
  1. Remove `segment()`, `SEGMENTS` and their validation from the command, and the test that compares `SEGMENTS` with `CustomerSegment`.
  2. `DemoDataLoader`: drop the segment argument; Grace Hopper opens with `150000.00`, so she is still the high-net-worth demo customer. `DemoDataLoaderTests` keeps asserting `RETAIL, RETAIL, HIGH_NET_WORTH`.
  3. Stop passing `segment` in the two presentation mappers (the request and form fields are removed in T5 and T6).
- **Acceptance criteria:**
  - [ ] `grep -rn "segment" src/main/java/com/pronoide/ddd/application/OpenAccountCommandDef.java` finds nothing.
  - [ ] `./mvnw verify` green.

### [ ] T5 — REST: `POST /api/accounts` without segment (~5 min)

- **Layer:** presentation (rest)
- **Files:** `presentation/rest/OpenAccountRequest.java`, `AccountControllerTests.java`, `DddLabApplicationTests.java`
- **Depends on:** T4
- **What to do:**
  1. Remove `segment` from the record and from the JSON bodies of the tests; remove the tests of the segment validation.
  2. Add a test: a body with 100000.00 answers `201` and `GET /api/accounts/{id}` shows `"segment": "HIGH_NET_WORTH"`.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=AccountControllerTests` passes.
  - [ ] `curl -u admin:demo -H 'Content-Type: application/json' -d '{"accountNumber":"ES0000000000000000009401","holderName":"Test","initialDeposit":100,"ownerUsername":"ada"}' localhost:8080/api/accounts` answers `201`.

### [ ] T6 — Web: the open-account form no longer asks for the segment (~10 min)

- **Layer:** presentation (web)
- **Files:** `presentation/web/OpenAccountForm.java`, `AccountWebController.java` (`addFormOptions`), `templates/accounts/new.html`, `AccountWebControllerTests.java`, `DddLabApplicationTests.java`
- **Depends on:** T4
- **What to do:**
  1. Test first: the form page does not contain `name="segment"`; posting without segment opens the account.
  2. Remove the field, the `segments` model attribute and the `<select>`. The account detail keeps showing the segment.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=AccountWebControllerTests` passes.
  - [ ] `./mvnw verify` green.

### [ ] T7 — OpenAPI (~5 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`
- **Depends on:** T5
- **What to do:**
  1. Remove `segment` from the open-account request schema and its `required` list; keep it in the account-details response, described as assigned by the bank.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` without errors.

### [ ] T8 — Contract, glossary and functional analysis (~10 min)

- **Layer:** docs
- **Files:** `docs/contracts/open-query-list-accounts.md`, `docs/dominio/glosario.md`, `docs/analysis/functional-analysis.md`
- **Depends on:** T6, T7
- **What to do:**
  1. Contract: amend `OpenAccountCommand` and the shared validation (no segment), with a note pointing to #94.
  2. Glossary: term "Patrimonio mínimo" (`MINIMUM_NET_WORTH`), updated rows for holder, segment, command and roles, and rules R17 (promotion at the minimum after an approved credit), R18 (an account can open as high net worth) and R19 (the segment is not lost) with their tests.
  3. Functional analysis: demo data table (Grace Hopper, 150.000,00 €), holder description and rules table.
- **Acceptance criteria:**
  - [ ] Every rule R17-R19 names an existing test.
  - [ ] `grep -rn "register(.*segment" docs/dominio docs/analysis` finds nothing outdated.

### [ ] T9 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T8
- **What to do:**
  1. Status: #94 done, third authorised exception to the freeze, next step #95 and the `Hexagonal` twin issue.
  2. Decision: automatic segment (threshold, one-way, per account). Pending: remove the `AccountHolder` name test if T3 added it.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] No input (web form, REST body, command, demo data) carries a segment; `grep -rn "segment" src/main/java --include=Open*` finds nothing.
- [ ] An account that reaches 100.000,00 € by opening, deposit or incoming transfer shows "Alto patrimonio" in its detail, and keeps it after going below.
- [ ] With the `demo` profile, Grace Hopper is high net worth and Ada Lovelace and Alan Turing are retail.
- [ ] The `v1-monolito` tag is not moved.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (layers: each layer only uses the one below), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones.
- [ ] `docs/api/openapi.yaml` reflects the change and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English (additions to documents still in Spanish follow the language of the document, as agreed in `pendientes.md`).
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Threshold (to confirm):** 100.000,00 € is a proposal. Changing it only touches the constant, the tests of T1-T2 and the demo deposit.
- **One-way promotion (to confirm):** if the segment should be lost below the minimum, T1 changes (a `demote` on the holder, evaluated after withdrawals) and so does the overdraft behaviour: a high-net-worth customer in overdraft would become retail while below zero.
- **Currency:** the threshold is in EUR and `isLessThan` throws `CurrencyMismatchException` for another currency. Today no account can open in another currency (the minimum deposit is also in EUR), same latent warning as in `pendientes.md`.
- **Demo balance:** Grace Hopper goes from 5.000,00 € to 150.000,00 €; the total shown to the administrator changes.
- **Old REST clients** that still send `segment` get no error and the value is ignored.
- **`Monolithic` is frozen:** this is its third authorised exception (after #86 and #91).
