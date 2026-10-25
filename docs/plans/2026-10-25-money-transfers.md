# Plan: Transfers between accounts (back + web)

- **Issue:** [#7](https://github.com/dawnrock/Proyect-DDD-Claude/issues/7)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-25
- **Approved:** by the user on 2026-10-25
- **Status:** in progress
- **Total estimate:** ~115 min (12 tasks: T0 + 6 back + 3 web + 2 both)

## Goal

Third vertical feature: **transfers between two accounts**, from the REST API (`POST /api/transfers`) and from the web account detail page. The rule spans two aggregates, so it lives in the project's first **domain service**, `domain.service.MoneyTransferService` (Lab 26): it tries the withdrawal on the source account and, **only if it is approved**, deposits the same amount in the target account. Transferring to the same account is forbidden. It is a team issue (ADR 0007), implemented with `/implement-plan-team 7`.

As with withdrawals, a transfer rejected by the source account's balance rules is **not an error**: the source records a `REJECTED` withdrawal, nothing reaches the target, and the API/web report the transfer as rejected.

### Out of scope

- New transaction types (`TRANSFER_OUT` / `TRANSFER_IN`) or a link between the two movements: a transfer is recorded as a normal `WITHDRAWAL` in the source and a `DEPOSIT` in the target (see Risks).
- A transfer resource or history (`GET /api/transfers`): there is no `Transfer` aggregate or repository.
- Transfers between currencies or exchange rates. Demo data (#11). Layered ArchUnit rules (#12), which will enforce D4 for `domain.service`.
- Fixing the latent `tryWithdraw` ordering warning from #6 (only relevant with non-EUR accounts).

## Context

- **Domain already has the per-account rules:** `CheckingAccount.tryWithdraw` (R5-R9, approves or rejects, `CurrencyMismatchException` before registering anything) and `deposit` (R3, R4). The domain service only coordinates them; it adds no balance rules.
- **New domain rules** (glossary R17-R19):
  - **R17** — a transfer to the same account is rejected with `SameAccountTransferException` (new `DomainException`, 422) before any movement is recorded.
  - **R18** — if the withdrawal in the source is rejected, no deposit is made in the target; the source keeps the `REJECTED` withdrawal and both balances are unchanged.
  - **R19** — both accounts must be in the transfer's currency: the service checks the **target** currency *before* withdrawing (`CurrencyMismatchException`), so an approved withdrawal can never be followed by a failing deposit. Today every account is in EUR, so this is a guard, but it keeps the transfer all-or-nothing.
- **`MoneyTransferService` is stateless and plain Java** (no Spring, `domain` must not depend on it, D1): `Transaction transfer(CheckingAccount source, CheckingAccount target, MonetaryAmount amount)`, returning the source's withdrawal (its status is the transfer's outcome). `CheckingAccountService` holds it as `private final MoneyTransferService transferService = new MoneyTransferService();` — no bean, no `config` package in phase 1 (ADR 0004: concrete classes).
- **Use case on the existing service** (as the issue says, "`transfer` en el servicio"): `CheckingAccountService.transfer(TransferCommand)` loads both accounts (`AccountNotFoundException` → 404 for either), calls the domain service and **saves both** accounts.
- **Validation split** (as in #6): the command validates format (positive amount after rounding, ISO currency, `EUR` by default, via `CommandValidation.requireValidMovement`); the same-account rule is **business** (R17, 422), so the command does not duplicate it.
- **REST:** new `TransferController` in `presentation.rest` (`POST /api/transfers`, `TransferRequest` record with Bean Validation) → `201` + `Transfer`, also when `REJECTED` (consistent with withdrawals). No `Location` header (no transfer resource).
- **Web:** a third form on `GET /accounts/{id}`: target account select (other accounts from `listAccounts()`, current one excluded), amount and currency; `POST /accounts/{id}/transfers` with Post/Redirect/Get and an approved/rejected flash. When there is no other account, the page shows a hint instead of the form. The web uses only `application` (D4).
- **Documentation language:** plan, contract and OpenAPI in English; additions to documents still in Spanish (glossary, `fase-1-capas.md`, project status) stay in Spanish until they are translated.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `SameAccountTransferException.java` | New domain exception (R17) |
| `domain.service` | `MoneyTransferService.java`, `MoneyTransferServiceTests.java` | New package; domain service (R17-R19) |
| `application` | `TransferCommandDef.java`, `TransferSummaryDef.java`, `CheckingAccountService.java` | Command, view and `transfer` use case |
| `presentation.rest` | `TransferController.java`, `TransferRequest.java`, `TransferResponseDef.java` | `POST /api/transfers` |
| `presentation.web` | `AccountWebController.java`, `TransferForm.java`, `templates/accounts/detail.html` | Transfer form on the detail page |
| `docs` | `docs/contracts/money-transfers.md`, `docs/api/openapi.yaml`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md` | Contract, API and documentation |

## Tasks

### [x] T0 — Contract (~15 min)

- **Side:** both
- **Layer:** application · docs
- **Files:** `docs/contracts/money-transfers.md`, contract types in `application`, fragment of `docs/api/openapi.yaml`
- **Depends on:** —
- **What to do:**
  1. Write the contract (proposal, reviewed by `backend-dev` and `web-dev`):
     - **Service:** `TransferSummary transfer(TransferCommand)` on `CheckingAccountService`; throws `AccountNotFoundException` for an unknown source or target, `SameAccountTransferException` and `CurrencyMismatchException`; a rejected transfer is returned with `REJECTED`, not thrown.
     - **Command:** `TransferCommand` with `sourceAccountId` (`UUID`), `targetAccountId` (`UUID`), `amount` (`BigDecimal`, positive after rounding) and `currency` (`String`, ISO 4217, default `EUR`).
     - **View:** `TransferSummary` with `sourceAccountId`, `targetAccountId`, `amount`, `currency`, `occurredOn` and `status` (`APPROVED` | `REJECTED`).
     - **Errors:** amount ≤ 0 or malformed → 400 / field error; unknown account (source or target) → 404 / custom 404 page; same account or another currency → 422 / message on the detail page.
     - **REST:** `POST /api/transfers` with `TransferRequest` (`sourceAccountId`, `targetAccountId`, `amount`, `currency`) → `201` + `Transfer`; 400; 404; 422.
     - **Screens:** transfer form on `GET /accounts/{id}` (target select without the current account; hint when there is none); `POST /accounts/{id}/transfers` → PRG with flash "Transferencia de … a … aprobada" or rejected (different style); errors render the detail page again with the message and the entered values (independent `transferForm` attribute).
  2. Create the types without logic: `TransferCommandDef`, `TransferSummaryDef`, `SameAccountTransferException` (so presentation can translate it) and `transfer` with an `UnsupportedOperationException("pendiente: backend-dev")` body.
  3. Write the OpenAPI fragment (`/api/transfers`, `TransferRequest` and `Transfer` schemas), reusing `BadRequest`, `NotFound` and `UnprocessableContent`.
- **Acceptance criteria:**
  - [x] Contract agreed by both teammates and frozen in a `docs(contract): ...` commit, with its hash noted.
  - [x] `./mvnw verify` compiles and passes against the contract types.
  - [x] `npx @redocly/cli lint docs/api/openapi.yaml` without errors.

### [x] T1 — `MoneyTransferService`: approved transfer (~10 min)

- **Side:** back
- **Layer:** domain
- **Files:** `domain/service/MoneyTransferService.java`, `domain/service/MoneyTransferServiceTests.java`
- **Depends on:** T0
- **What to do:**
  1. With the `ddd-building-block` skill: stateless class, `Transaction transfer(source, target, amount)`; `tryWithdraw` on the source and, if approved, `deposit` on the target.
  2. Plain JUnit tests (no Spring): approved transfer moves the amount (source −, target +), records a `WITHDRAWAL` and a `DEPOSIT`, and returns the approved withdrawal.
- **Acceptance criteria:**
  - [x] Tests green. `check-domain.mjs` without errors; `DomainIsolationTests` covers the new package.

### [x] T2 — `MoneyTransferService`: rejected transfer and same account (~10 min)

- **Side:** back
- **Layer:** domain
- **Files:** `domain/service/MoneyTransferService.java`, `domain/model/SameAccountTransferException.java`, `domain/service/MoneyTransferServiceTests.java`
- **Depends on:** T1
- **What to do:**
  1. R18: rejected withdrawal → no deposit; target has no new movement and both balances are unchanged (retail beyond its balance, and high net worth beyond -1,000.00, parameterized).
  2. R17: same account (same id) → `SameAccountTransferException` and no movement recorded.
- **Acceptance criteria:**
  - [x] Tests green (R18 test validated by temporarily depositing regardless of the outcome).

### [x] T3 — `MoneyTransferService`: currency guard (~5 min)

- **Side:** back
- **Layer:** domain
- **Files:** `domain/service/MoneyTransferService.java`, `domain/service/MoneyTransferServiceTests.java`
- **Depends on:** T2
- **What to do:**
  1. R19: amount in a currency other than the target's → `CurrencyMismatchException` **before** withdrawing. Since accounts can only be opened in EUR, the test uses an amount in USD (source and target in EUR) and checks that neither account records a movement.
- **Acceptance criteria:**
  - [x] Test green; no movement recorded in either account.

### [x] T4 — `TransferCommand` validation (~5 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/TransferCommandDef.java`, `application/MovementCommandsTests.java`
- **Depends on:** T0
- **What to do:**
  1. `@Value.Check` with `CommandValidation.requireValidMovement`; `currency` defaults to `EUR`.
  2. Parameterized tests of the invalid cases (0, negative, `0.001` EUR, `XYZ`).
- **Acceptance criteria:**
  - [x] Tests green.

### [ ] T5 — "Transfer" use case (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/CheckingAccountService.java`, `application/CheckingAccountServiceTests.java`
- **Depends on:** T3, T4
- **What to do:**
  1. `transfer`: load source and target (or `AccountNotFoundException`), delegate to `MoneyTransferService`, **save both**, return the `TransferSummary`.
  2. Tests (`@Nested` "Transferir", repository spied or in memory): approved → both accounts saved with their new balances; rejected → `REJECTED` and balances unchanged; unknown source / unknown target → `AccountNotFoundException`; same account → `SameAccountTransferException`.
- **Acceptance criteria:**
  - [ ] Tests green; `save` is called for both accounts. No business rules in the application service.

### [ ] T6 — `POST /api/transfers` (~10 min)

- **Side:** back
- **Layer:** presentation
- **Files:** `presentation/rest/TransferController.java`, `presentation/rest/TransferRequest.java`, `presentation/rest/TransferResponseDef.java`, `presentation/rest/TransferControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. Map the request to `TransferCommand`, respond `201` with the transfer.
  2. `@WebMvcTest` tests with the mocked service: 201 `APPROVED`; 201 `REJECTED`; 400 with `errors` (missing ids, amount 0); 404; 422 for `SameAccountTransferException` and `CurrencyMismatchException`.
- **Acceptance criteria:**
  - [ ] Tests green. Responses match the OpenAPI fragment.

### [ ] T7 — Transfer form on the detail page (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `presentation/web/TransferForm.java`, `templates/accounts/detail.html`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. `transferForm` attribute and the list of target accounts (`listAccounts()` without the current one, shown as number + holder); hint "No hay otras cuentas a las que transferir" when it is empty.
  2. `@WebMvcTest` tests: the select excludes the current account; the hint appears with a single account.
- **Acceptance criteria:**
  - [ ] Tests green. The view uses no domain types.

### [ ] T8 — Transfer submission (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T7
- **What to do:**
  1. `POST /accounts/{id}/transfers` → redirect to the detail with an approved flash or a **rejected** flash (different style, text valid for both segments, as in #6).
  2. Tests: approved flash; rejected flash.
- **Acceptance criteria:**
  - [ ] Tests green.

### [ ] T9 — Transfer form errors (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T8
- **What to do:**
  1. Field errors (no target, amount ≤ 0) and command rejection (`0.001`, `XYZ`) → detail page again with the entered values; `DomainException` (same account from a crafted request, currency) → message on the detail page; unknown target → custom 404 page.
  2. Tests for each case; the other two forms show no errors.
- **Acceptance criteria:**
  - [ ] Tests green. No form error ends in a 500 page.

### [ ] T10 — End-to-end integration (~10 min)

- **Side:** both
- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java`
- **Depends on:** T1-T9 (after merging `-back` and `-web`)
- **What to do:**
  1. With the real service: open two accounts; transfer via API → 201 `APPROVED` and both balances updated in `GET /api/accounts/{id}`; transfer more than the balance (retail) → 201 `REJECTED` and nothing in the target; same account → 422; the web transfer redirects to the detail with its flash and the target's detail shows the deposit.
  2. Manual check with `curl` and in the browser.
- **Acceptance criteria:**
  - [ ] Test green. `curl` and browser checks done.

### [ ] T11 — Documentation (~10 min)

- **Side:** both
- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T10
- **What to do:**
  1. Check `openapi.yaml` against the implementation and lint it.
  2. Glossary: "Transferencia" from "previstos" to the main table (`MoneyTransferService`, `TransferCommand`, `TransferSummary`), `SameAccountTransferException`, and rules R17-R19 with their tests.
  3. `fase-1-capas.md`: `domain.service` "Implementado en la #7" and, optionally, the transfer sequence diagram (shown to the user before committing, greyscale).
  4. Project status: #7 done, next step `/plan-feature 11`, history row.
- **Acceptance criteria:**
  - [ ] Lint without errors. Glossary and status updated.

## Global acceptance criteria

**Specific:**

- [ ] Contract agreed and frozen in `docs/contracts/money-transfers.md`.
- [ ] Domain service tests without Spring: approved transfer, rejected by balance and to the same account (plus the currency guard).
- [ ] Use case test: both accounts are saved with their new balances.
- [ ] A rejected transfer returns 201 with `REJECTED` in REST, is shown as rejected on the web, and leaves the target untouched.
- [ ] `check-domain.mjs` without errors.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The layered architecture is respected (D1-D5; presentation does not use `domain.service`), verified by ArchUnit where rules exist and by `revisor-ddd` (the full rules arrive with #12).
- [ ] The domain does not depend on Spring, `jakarta` or `org.immutables`, and does not use `@Data`, `@Setter`, `@Builder` or `@AllArgsConstructor` (`check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones.
- [ ] `docs/api/openapi.yaml` reflects the changes and the Redocly lint has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English (additions to documents still in Spanish excepted, see Context).
- [ ] `docs/ia/estado-del-proyecto.md` updated: issue status, next step, new pending items and history row.

## Risks and open questions

- **Movement types:** reusing `WITHDRAWAL` / `DEPOSIT` keeps the domain unchanged but the detail page cannot tell a transfer from a cash movement. Alternative: new `TransferType` values or a reference to the counterpart account — postponed unless the user wants it now.
- **Unknown target = 404:** the target comes in the body, so 422 would also be defensible; 404 reuses `AccountNotFoundException` as it is.
- **Atomicity:** with the in-memory repository both accounts are mutated before `save`; R19's pre-check prevents the only known half-done case. A real repository would need a transaction around both saves (phase 2).
- **Who instantiates the domain service:** `new MoneyTransferService()` inside `CheckingAccountService` keeps Spring out of the domain; in phase 2 it will move to `config`.
