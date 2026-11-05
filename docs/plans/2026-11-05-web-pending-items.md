# Plan: Web pending items — segment in the list, transfer label, unbroken IBAN and newest transactions first

- **Issue:** [#72](https://github.com/dawnrock/Proyect-DDD-Claude/issues/72)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-05
- **Status:** draft
- **Total estimate:** ~100 min (11 tasks)

## Goal

Close the web pending items left by #48 (`docs/ia/pendientes.md`):

1. The account list shows each account's **segment** (the view already carries it since #18).
2. A transfer shows up in the account details as a **transfer to / from** another account (shortened IBAN), not as a plain withdrawal and deposit.
3. The shortened IBAN never **breaks across lines** (non-breaking spaces in `WebFormat.maskedIban`).
4. Transactions are listed **newest first** in the details screen (decided by the user on 2026-11-05; the REST API keeps chronological order).

Item 2 needs data that does not exist today: a transaction does not know it belongs to a transfer. Decided by the user on 2026-11-05: each transaction of a transfer records the **counterpart account number** (the IBAN of the other account). Transfers keep being recorded as `WITHDRAWAL` + `DEPOSIT` (decision of #7: no transaction types of their own); the counterpart is an optional attribute. This touches the domain, `application`, REST and the web, so the plan runs as a **team** (`/implement-plan-team 72`): contract first, back and web in parallel.

### Out of scope

- New transaction types (`TRANSFER_IN`/`TRANSFER_OUT`): discarded by the user; the #7 decision stands.
- Domain events: `MoneyDeposited`/`MoneyWithdrawn` do not gain the counterpart (no consumer needs it yet).
- The CLI demo trace and the REST transfer response (`TransferSummary` already carries both account numbers).
- Changing the order of transactions in the REST API.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `Transaction.java`, `CheckingAccount.java` | Optional counterpart (`AccountNumber`) on a transaction; business methods to withdraw for a transfer to an account and to receive a transfer from one |
| `domain.service` | `MoneyTransferService.java` | Uses those methods, so both transactions of a transfer carry the other account's number |
| `application` | `TransactionSummaryDef.java`, `CheckingAccountService.java` | View gains `counterpartAccountNumber` (optional); mapping fills it |
| `adapter.in.rest` | `TransactionResponseDef.java`, `AccountController.java` | Response gains `counterpartAccountNumber` (absent when not a transfer) |
| `adapter.in.web` | `WebFormat.java`, `AccountWebController.java`, `templates/accounts/list.html`, `detail.html` | Segment label helper, segment in the list, transfer label, non-breaking spaces, newest first |
| docs | `docs/contracts/transfer-counterpart.md` (new), `docs/api/openapi.yaml`, `docs/dominio/glosario.md`, `docs/ia/*` | Contract, API, new term, status |

DDD building blocks: `Transaction` is an entity inside the `CheckingAccount` aggregate, so the counterpart is only set through the aggregate's business methods (never from outside, R10). `AccountNumber` is the existing value object. No new port or adapter.

**New term:** counterpart account (*cuenta de contrapartida*): the other account of a transfer, as seen from one of its transactions.

## Tasks

### [ ] T0 — Contract (~10 min)

- **Side:** both
- **Layer:** application · docs
- **Files:** `docs/contracts/transfer-counterpart.md`, `application/TransactionSummaryDef.java`, fragment of `docs/api/openapi.yaml`
- **Depends on:** —
- **What to do:**
  1. Contract from the template: `TransactionSummary.counterpartAccountNumber()` (`Optional<String>`, normalised IBAN of the other account, present only for transactions of a transfer, also a rejected one); REST `Transaction` schema gains `counterpartAccountNumber` (string, omitted when absent); screens: list with segment, details with "Transferencia a/de <shortened IBAN>" and newest first. No new errors.
  2. Add the attribute to `TransactionSummaryDef` without filling it (always empty); backend-dev and web-dev review and agree; frozen in a commit.
- **Acceptance criteria:**
  - [ ] Contract agreed and frozen; `./mvnw verify` green with the new (empty) attribute.

### [ ] T1 — Domain: transactions of a transfer know the other account (~10 min)

- **Side:** back
- **Layer:** domain
- **Files:** `domain/model/Transaction.java`, `CheckingAccount.java`, `CheckingAccountTests.java` (or `TransactionTests.java`)
- **Depends on:** T0
- **What to do:**
  1. Tests first: a withdrawal for a transfer records the target account number (approved and rejected); a deposit from a transfer records the source account number; a plain deposit or withdrawal has no counterpart.
  2. `Transaction.counterpart()` (`Optional<AccountNumber>`), set only by the factory methods inside the package; `CheckingAccount` gains the business methods (for example `tryWithdrawForTransfer(amount, target)` and `depositFromTransfer(amount, source)`), reusing the same rules (R3-R9). Names checked against the glossary.
- **Acceptance criteria:**
  - [ ] Domain tests green; `check-domain.mjs` without errors (no Lombok, no public setters).

### [ ] T2 — Domain service: transfers record the counterpart (~5 min)

- **Side:** back
- **Layer:** domain.service
- **Files:** `domain/service/MoneyTransferService.java`, `MoneyTransferServiceTests.java`
- **Depends on:** T1
- **What to do:**
  1. Test first: after a transfer, the source's withdrawal points to the target and the target's deposit to the source; a rejected transfer leaves the rejected withdrawal pointing to the target and nothing on the target (R18).
  2. `MoneyTransferService` uses the new methods; R17-R19 unchanged.
- **Acceptance criteria:**
  - [ ] `MoneyTransferServiceTests` green, including the existing R17-R19 tests.

### [ ] T3 — Application: the view carries the counterpart (~5 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/CheckingAccountService.java`, `CheckingAccountService*Tests.java`
- **Depends on:** T2
- **What to do:**
  1. Test first: the details of both accounts after a transfer show the counterpart on the transfer's transactions and none on the others.
  2. The mapping fills `counterpartAccountNumber` from `Transaction.counterpart()`.
- **Acceptance criteria:**
  - [ ] Service tests green.

### [ ] T4 — REST: the transaction response carries the counterpart (~10 min)

- **Side:** back
- **Layer:** adapter.in.rest
- **Files:** `adapter/in/rest/TransactionResponseDef.java`, `AccountController.java`, `AccountControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. Test first (`@WebMvcTest`, ports mocked): `GET /api/accounts/{id}` returns `counterpartAccountNumber` on a transfer transaction and omits it on a deposit.
  2. Add the field to the response and its mapping.
- **Acceptance criteria:**
  - [ ] REST tests green; JSON as in the contract.

### [ ] T5 — OpenAPI (~5 min)

- **Side:** back
- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`
- **Depends on:** T4
- **What to do:**
  1. `Transaction` schema: optional `counterpartAccountNumber` with its description; check it matches the contract fragment.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` without errors.

### [ ] T6 — Web format: unbroken IBAN and segment label (~5 min)

- **Side:** web
- **Layer:** adapter.in.web
- **Files:** `adapter/in/web/WebFormat.java`, `WebFormatTests.java`
- **Depends on:** T0
- **What to do:**
  1. Tests first: `maskedIban` uses non-breaking spaces (U+00A0) between the groups; `segment("RETAIL")` → "Minorista", `segment("HIGH_NET_WORTH")` → "Alto patrimonio", an unknown code is shown as it is.
  2. Implement both; the controller's private `segmentLabel` and the ternary in `detail.html` use `fmt.segment` (one place for the label).
- **Acceptance criteria:**
  - [ ] `WebFormatTests` green; no other place translates the segment on the web.

### [ ] T7 — Web: segment in the account list (~5 min)

- **Side:** web
- **Layer:** adapter.in.web
- **Files:** `templates/accounts/list.html`, `AccountWebControllerTests.java`
- **Depends on:** T6
- **What to do:**
  1. Test first (`@WebMvcTest`, ports mocked): the list shows "Alto patrimonio" for a high net worth account and "Minorista" for a retail one.
  2. Add the segment to each row (`row-sub`, after the shortened IBAN).
- **Acceptance criteria:**
  - [ ] Web tests green.

### [ ] T8 — Web: transfer label and newest transactions first (~10 min)

- **Side:** web
- **Layer:** adapter.in.web
- **Files:** `AccountWebController.java`, `templates/accounts/detail.html`, `AccountWebControllerTests.java`
- **Depends on:** T6
- **What to do:**
  1. Tests first, with stubbed views: a withdrawal with a counterpart reads "Transferencia a ES79 •••• 6789", a deposit with one "Transferencia de …", a rejected one keeps its rejected style; transactions appear newest first.
  2. Implement the label in the template and the order in the controller (the view list stays as `application` returns it).
- **Acceptance criteria:**
  - [ ] Web tests green; plain deposits and withdrawals keep their current labels.

### [ ] T9 — Integration: the demo shows the transfer (~10 min)

- **Side:** both
- **Layer:** tests
- **Files:** `DemoProfileTests.java` (or a new full-context test in the root package)
- **Depends on:** T3, T5, T7, T8
- **What to do:**
  1. Merge back and web. Full context with the `demo` profile: the REST details of Ada's account show the €300 deposit with Grace's account number as counterpart, and the web details of Grace's account show "Transferencia a …" first.
  2. Prove the test can fail: break the mapping of T3 temporarily, see red, revert.
- **Acceptance criteria:**
  - [ ] The integration test passes and fails when the counterpart is not mapped; `./mvnw verify` green.

### [ ] T10 — Glossary, documentation and project status (~10 min)

- **Side:** both
- **Layer:** docs
- **Files:** `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md` (if a role changes), `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T9
- **What to do:**
  1. Glossary: "Counterpart account / Cuenta de contrapartida" and the transfer row says the transactions record it; the views row mentions the new field.
  2. Status: #72 done; next step decided with the user (no open issues left); decision (counterpart instead of transfer types; newest first only on the web); `pendientes.md` loses the #48 web items.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] The list shows the segment of each account.
- [ ] Transfers are told apart from withdrawals and deposits in the details (web) and carry the counterpart (REST).
- [ ] `maskedIban` uses non-breaking spaces (test).
- [ ] Transactions are listed newest first on the web.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md` (counterpart account).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

Answered by the user on 2026-11-05: transfers are labelled through a counterpart account on the transactions (not new types); transactions newest first on the web only.

- **The issue grows from web-only to back + web:** labels `lado:back` and `equipo` should be added to #72 so the issue matches the plan (done when the plan is approved).
- **REST change is additive:** a new optional field; existing clients are not affected, no versioning needed.
- **Accounts already in memory:** the repository is in memory and restarts empty, so there are no old transfer transactions without a counterpart to migrate.
- **Next step after #72:** no open issues remain; the status will say "decide with the user". Candidates already noted: the Graphify refinement for method names (#71, touches `.claude/`), the transaction for the two `save` calls of a transfer (#7 review), the missing holder-name test.
