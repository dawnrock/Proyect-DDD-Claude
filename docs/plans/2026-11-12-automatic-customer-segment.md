# Plan: Automatic customer segment

- **Issue:** [#94](https://github.com/dawnrock/Proyect-DDD-Claude/issues/94)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic` (frozen; `src/` exception authorised by the user on 2026-11-12)
- **Date:** 2026-11-12 (revision 2: two-way segment with a one-month grace period)
- **Status:** draft
- **Total estimate:** ~100 min (12 tasks)

## Goal

The customer segment stops being an input chosen by the administrator. The domain decides it in both directions. Every holder starts as `RETAIL`; the aggregate promotes the holder to `HIGH_NET_WORTH` as soon as an approved credit (opening deposit, deposit or incoming transfer) takes the balance to the minimum net worth. A high-net-worth holder whose balance falls below the minimum goes back to `RETAIL` only if, one month later, the balance is still below it. This is a prerequisite of #95 (customer sign-up), where nobody chooses a segment.

### Out of scope

- Customer sign-up, holder document and e-mail, bank-assigned IBAN (#95).
- Adding up several accounts of the same person (the holder is an entity inside each `CheckingAccount`).
- Showing the customer the date on which the segment would be lost.
- The same change on `Hexagonal`: twin issue, created when this plan is approved. Both branches must end with the same behaviour.

## Context

Today the segment travels from the form or the REST body through `OpenAccountCommand.segment()` to `AccountHolder.register(fullName, segment)`. `CheckingAccount.deposit` is the single place where a balance grows (opening and incoming transfers go through it) and `tryWithdraw` the single place where it shrinks, so the rules live there.

Decisions confirmed by the user (2026-11-12):

- **Minimum net worth:** `100000.00 EUR`, constant `MINIMUM_NET_WORTH` in `CheckingAccount`, compared with "balance not less than".
- **Two-way segment with delay.** Promotion is immediate. Demotion waits one calendar month (UTC) counted from the approved withdrawal that left the balance below the minimum; if the balance reaches the minimum again before that, the countdown is cancelled.
- **Demo data:** Grace Hopper opens with 150.000,00 €.

Decisions taken in this plan:

- **Time in the domain.** The aggregate stores `belowMinimumNetWorthSince` (`Instant`, taken from the withdrawal's `occurredOn`) and exposes `reviewSegment(Instant now)`. The domain never asks for the current time to decide the demotion, so the rule is testable.
- **Who triggers the review.** `CheckingAccountService` reviews the account before every withdrawal and outgoing transfer (so the overdraft rule always sees the right segment) and a daily `@Scheduled` job in `application` reviews every account. `@Scheduled` is Spring core: no new dependency. The service takes the time from a `java.time.Clock` bean.
- **Not a team plan.** The web side is one small task, so it is implemented with `/implement-plan`. T11 amends the contract document (ADR 0007).
- **REST:** `segment` disappears from the `POST /api/accounts` body (incompatible change). A client that still sends it is not rejected: Jackson ignores unknown properties by default.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `CheckingAccount.java` | `MINIMUM_NET_WORTH`, promotion after a credit, `belowMinimumNetWorthSince`, `reviewSegment(Instant)` |
| `domain.model` | `AccountHolder.java` | `register(fullName)`: always `RETAIL`; `demoteToRetail()` |
| `application` | `CheckingAccountService.java`, new `SegmentReviewJob.java`, `OpenAccountCommandDef.java`, `DemoDataLoader.java` | Review before debits, daily job, `Clock`; remove `segment` and `SEGMENTS` |
| `presentation.rest` | `OpenAccountRequest.java`, `AccountController.java` | Remove `segment` from the request |
| `presentation.web` | `OpenAccountForm.java`, `AccountWebController.java`, `templates/accounts/new.html` | Remove the segment field and its options |
| tests | `TestAccounts.java` and the tests listed in each task | `segment(HIGH_NET_WORTH)` promotes the holder instead of registering it with a segment |
| docs | `openapi.yaml`, `contracts/open-query-list-accounts.md`, `dominio/glosario.md`, `analysis/functional-analysis.md`, `ia/*` | Rules R17-R20, terms, project status |

## Tasks

### [ ] T1 — The aggregate promotes the holder at the minimum net worth (~10 min)

- **Layer:** domain
- **Files:** `domain/model/CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** —
- **What to do:**
  1. Failing tests first: a deposit that takes the balance to exactly 100.000,00 € promotes the holder (R17); one cent less does not; an account opened with 100.000,00 € is high net worth from the start (R18).
  2. Add `MINIMUM_NET_WORTH = MonetaryAmount.euros("100000.00")` and, in `deposit`, after updating the balance, call `primaryHolder.promoteToHighNetWorth()` when the balance is not less than it.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=CheckingAccountTests` passes with the three new tests; `./mvnw verify` green.

### [ ] T2 — An incoming transfer can promote the target holder (~5 min)

- **Layer:** domain (test only)
- **Files:** `MoneyTransferServiceTests.java`
- **Depends on:** T1
- **What to do:**
  1. A retail target account with 99.900,00 € receives an approved transfer of 100,00 € and its holder becomes high net worth; a rejected transfer promotes nobody.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=MoneyTransferServiceTests` passes; `MoneyTransferService` is not changed.

### [ ] T3 — The account remembers since when it is below the minimum (~10 min)

- **Layer:** domain
- **Files:** `CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** T1
- **What to do:**
  1. Failing tests first: an approved withdrawal that leaves a high-net-worth account below the minimum records `belowMinimumNetWorthSince` with the withdrawal's `occurredOn`; a second withdrawal does not move the date; a deposit that reaches the minimum clears it; a rejected withdrawal and a retail account record nothing.
  2. Add the private field and the accessor `belowMinimumNetWorthSince()` (`Optional<Instant>`); set it in `tryWithdraw`, clear it in `deposit`.
- **Acceptance criteria:**
  - [ ] The five tests pass; `./mvnw verify` green.

### [ ] T4 — The holder goes back to retail after one month below the minimum (~10 min)

- **Layer:** domain
- **Files:** `AccountHolder.java`, `CheckingAccount.java`, `AccountHolderTests.java`, `CheckingAccountTests.java`
- **Depends on:** T3
- **What to do:**
  1. Failing tests first: `reviewSegment(since + 1 month - 1 s)` keeps high net worth; `reviewSegment(since + 1 month)` demotes and clears the date (R19); after recovering the minimum in between, the review does nothing (R20); reviewing a retail account does nothing; a demoted holder in overdraft keeps the balance but its next withdrawal is rejected.
  2. Add `AccountHolder.demoteToRetail()` and `CheckingAccount.reviewSegment(Instant now)`; the month is one calendar month in UTC (constant `SEGMENT_GRACE_PERIOD`).
- **Acceptance criteria:**
  - [ ] The five tests pass; `./mvnw verify` green.

### [ ] T5 — A holder is always registered as retail (~10 min)

- **Layer:** domain (plus the callers needed to compile)
- **Files:** `AccountHolder.java`, `AccountHolderTests.java`, `src/test/java/com/pronoide/ddd/support/TestAccounts.java`, `TestAccountsTests.java`, one line in `CheckingAccountService.java`
- **Depends on:** T1
- **What to do:**
  1. Test first: `AccountHolder.register("Ada Lovelace")` is `RETAIL`. Add the pending test "el nombre del titular es obligatorio" if it is still missing (`pendientes.md`).
  2. Replace `register(String, CustomerSegment)` with `register(String)`.
  3. `TestAccounts`: keep `segment(...)` in the builder; for `HIGH_NET_WORTH` it calls `promoteToHighNetWorth()` on the holder before opening (test shortcut: such an account has no countdown).
  4. `CheckingAccountService.openAccount`: call `register(command.holderName())`; `command.segment()` is ignored from now on (removed in T8). Adjust the service tests that expected a high-net-worth account from the command: open with a deposit that reaches the minimum.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest='AccountHolderTests,TestAccountsTests,CheckingAccountService*'` passes; `./mvnw verify` green.

### [ ] T6 — The service reviews the segment before every debit (~10 min)

- **Layer:** application
- **Files:** `CheckingAccountService.java`, `CheckingAccountServiceTests.java`, a `Clock` bean (`application`)
- **Depends on:** T4
- **What to do:**
  1. Test first, with a fixed `Clock` moved forward: a high-net-worth account that fell below the minimum more than a month ago gets its overdraft withdrawal rejected and its detail shows `RETAIL`; the same for the source account of a transfer.
  2. Inject `Clock`; call `account.reviewSegment(clock.instant())` before `tryWithdraw` and before the transfer, and save the account.
- **Acceptance criteria:**
  - [ ] The two tests pass; `la_aplicacion_no_depende_de_spring_security` and the layer rules stay green.

### [ ] T7 — Daily segment review job (~10 min)

- **Layer:** application
- **Files:** `CheckingAccountService.java` (`reviewSegments()`), new `SegmentReviewJob.java`, their tests, `@EnableScheduling`
- **Depends on:** T6
- **What to do:**
  1. Test first: `reviewSegments()` demotes only the accounts whose month has passed and returns how many.
  2. `SegmentReviewJob` (`@Component`, `@Scheduled(cron = "0 0 3 * * *")`) calls it and logs the count; enable scheduling in a configuration class of `application`.
- **Acceptance criteria:**
  - [ ] `./mvnw verify` green; the application starts and logs no scheduling error.

### [ ] T8 — `OpenAccountCommand` without segment (~10 min)

- **Layer:** application (plus one line in each presentation mapper to compile)
- **Files:** `OpenAccountCommandDef.java`, `DemoDataLoader.java`, `OpenAccountCommandTests.java`, `DemoDataLoaderTests.java`, `CheckingAccountServiceTests.java`, `CheckingAccountServiceOwnershipTests.java`; mapper lines in `AccountController.java` and `AccountWebController.java`
- **Depends on:** T5
- **What to do:**
  1. Remove `segment()`, `SEGMENTS` and their validation from the command, and the test that compares `SEGMENTS` with `CustomerSegment`.
  2. `DemoDataLoader`: drop the segment argument; Grace Hopper opens with `150000.00`. `DemoDataLoaderTests` keeps asserting `RETAIL, RETAIL, HIGH_NET_WORTH`.
  3. Stop passing `segment` in the two presentation mappers (the request and form fields are removed in T9 and T10).
- **Acceptance criteria:**
  - [ ] `grep -n "segment" src/main/java/com/pronoide/ddd/application/OpenAccountCommandDef.java` finds nothing; `./mvnw verify` green.

### [ ] T9 — REST: `POST /api/accounts` without segment (~5 min)

- **Layer:** presentation (rest)
- **Files:** `presentation/rest/OpenAccountRequest.java`, `AccountControllerTests.java`, `DddLabApplicationTests.java`
- **Depends on:** T8
- **What to do:**
  1. Remove `segment` from the record and from the JSON bodies of the tests; remove the tests of the segment validation.
  2. Add a test: a body with 100000.00 answers `201` and `GET /api/accounts/{id}` shows `"segment": "HIGH_NET_WORTH"`.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=AccountControllerTests` passes.
  - [ ] `curl -u admin:demo -H 'Content-Type: application/json' -d '{"accountNumber":"ES0000000000000000009401","holderName":"Test","initialDeposit":100,"ownerUsername":"ada"}' localhost:8080/api/accounts` answers `201`.

### [ ] T10 — Web: the open-account form no longer asks for the segment (~10 min)

- **Layer:** presentation (web)
- **Files:** `presentation/web/OpenAccountForm.java`, `AccountWebController.java` (`addFormOptions`), `templates/accounts/new.html`, `AccountWebControllerTests.java`, `DddLabApplicationTests.java`
- **Depends on:** T8
- **What to do:**
  1. Test first: the form page does not contain `name="segment"`; posting without segment opens the account.
  2. Remove the field, the `segments` model attribute and the `<select>`. The account detail keeps showing the segment.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=AccountWebControllerTests` passes; `./mvnw verify` green.

### [ ] T11 — OpenAPI, contract, glossary and functional analysis (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/contracts/open-query-list-accounts.md`, `docs/dominio/glosario.md`, `docs/analysis/functional-analysis.md`
- **Depends on:** T7, T9, T10
- **What to do:**
  1. OpenAPI: remove `segment` from the open-account request and its `required` list; keep it in the details response, described as assigned by the bank.
  2. Contract: amend `OpenAccountCommand` and the shared validation, with a note pointing to #94.
  3. Glossary: terms "Patrimonio mínimo" (`MINIMUM_NET_WORTH`) and "Periodo de gracia del segmento" (`SEGMENT_GRACE_PERIOD`), updated rows for holder, segment, command and roles, and rules R17 (promotion at the minimum after an approved credit), R18 (an account can open as high net worth), R19 (back to retail after one month below the minimum) and R20 (recovering the minimum cancels the countdown), each with its test.
  4. Functional analysis: demo data table, holder description and rules table.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` without errors.
  - [ ] Every rule R17-R20 names an existing test.

### [ ] T12 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T11
- **What to do:**
  1. Status: #94 done, third authorised exception to the freeze, next step #95 and the `Hexagonal` twin issue.
  2. Decision: automatic two-way segment (threshold, grace period, who triggers the review). Pending: remove the `AccountHolder` name test if T5 added it.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] No input (web form, REST body, command, demo data) carries a segment.
- [ ] An account that reaches 100.000,00 € by opening, deposit or incoming transfer shows "Alto patrimonio" in its detail.
- [ ] A high-net-worth account that stays below the minimum for one month shows "Minorista" and can no longer go into overdraft; one that recovers the minimum in time keeps the segment.
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

- **Demotion while in overdraft:** a holder demoted with a negative balance keeps the debt and cannot withdraw until the balance allows it. Accepted; covered by a test in T4.
- **Data in memory:** a restart (frequent on Render) rebuilds the demo data and loses every countdown, so in practice a demotion is only seen in tests or on a server that stays up for a month.
- **Currency:** the threshold is in EUR and `isLessThan` throws `CurrencyMismatchException` for another currency. Today no account can open in another currency; same latent warning as in `pendientes.md`.
- **Demo balance:** Grace Hopper goes from 5.000,00 € to 150.000,00 €; the total shown to the administrator changes.
- **Old REST clients** that still send `segment` get no error and the value is ignored.
- **`Monolithic` is frozen:** this is its third authorised exception (after #86 and #91).
