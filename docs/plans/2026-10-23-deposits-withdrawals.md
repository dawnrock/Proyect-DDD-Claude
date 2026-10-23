# Plan: Deposits and withdrawals with account details (back + web)

- **Issue:** [#6](https://github.com/dawnrock/Proyect-DDD-Claude/issues/6)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-23
- **Approved:** by the user on 2026-10-23
- **Status:** in progress
- **Total estimate:** ~125 min (12 tasks: T0 + 6 back + 3 web + 2 both)

## Goal

Second vertical feature: **deposits and withdrawals** on an existing account, from the REST API and from the web, plus the **web account detail page** with its transactions (left out of #5). It is a team issue (ADR 0007), implemented with `/implement-plan-team 6`.

A rejected withdrawal is **not an error**: the domain records it with status `REJECTED` and the balance does not change. The API returns it like any other transaction, and the web shows it as rejected.

### Out of scope

- Transfers between accounts (#7). Demo data (#11). Layered ArchUnit rules (#12).
- The rest of #10 that this issue does not replace.
- New business rules: deposits and withdrawals use the existing ones (R3-R9).

## Context

- **Domain already has the rules:** `CheckingAccount.deposit` (R3, R4) and `tryWithdraw` (R5-R9: retail cannot go negative; high net worth down to -1,000.00 EUR; a rejected withdrawal leaves the balance unchanged). Both return the `Transaction`.
- **From #5:** `CheckingAccountService` (`openAccount`, `findAccount`, `listAccounts`), `AccountNotFoundException` (404), the `TransactionSummary` view (type, amount, currency, `occurredOn`, status), `AccountDetails` with its transactions, `RestExceptionHandler`, the web list and sign-up form, and the shared presentation validation.
- **Non-positive amount stays a 400** (decided by the user on 2026-10-23, consistent with #3/#8 and R3): presentation validates `@Positive` (REST 400 with `errors`; web field error), and the command rejects it with `IllegalArgumentException` as a safety net. **The issue's acceptance criterion "non-positive → domain error (422)" is corrected here**: only another currency is a domain error (`CurrencyMismatchException` → 422 / message on the web).
- **Repository stores references:** the service must still call `save` after changing an account (repository Javadoc).
- **Rejected withdrawal over HTTP:** `201 Created` with the transaction, whatever its status; the client reads `status`. There is no transaction resource, so no `Location` header.
- **Web 404:** an unknown id on `GET /accounts/{id}` must show the custom 404 page with status 404 (the web has no exception handling yet).
- **Pending item folded in:** this issue touches withdrawals and their tests, so the legacy synonyms "reintegro" and "transacción" are renamed to "retirada" and "movimiento" (test names in `CheckingAccountTests` and the `Transaction.requirePending` message), as the project status asks.
- **Documentation language:** this plan, the contract and the OpenAPI fragment are in English; additions to documents still in Spanish (glossary, `fase-1-capas.md`, project status) stay in Spanish until they are translated.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `CheckingAccountTests.java`, `Transaction.java` | Parameterized overdraft-limit tests; rename legacy synonyms |
| `application` | `CheckingAccountService.java` | `deposit`, `withdraw` |
| `application` | `DepositCommandDef.java`, `WithdrawCommandDef.java` | Commands with validation |
| `presentation.rest` | `AccountController.java`, `MovementRequest.java` | `POST /api/accounts/{id}/deposits` and `/withdrawals` |
| `presentation.web` | `AccountWebController.java`, `MovementForm.java`, `templates/accounts/detail.html`, `templates/accounts/list.html` | Detail page with transactions and both forms; link from the list |
| `docs` | `docs/contracts/deposits-withdrawals.md`, `docs/api/openapi.yaml`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md` | Contract, API and documentation |

## Tasks

### [x] T0 — Contract (~15 min)

- **Side:** both
- **Layer:** application · docs
- **Files:** `docs/contracts/deposits-withdrawals.md`, contract types in `application`, fragment of `docs/api/openapi.yaml`
- **Depends on:** —
- **What to do:**
  1. Write the contract (proposal, reviewed by `backend-dev` and `web-dev`):
     - **Service:** `TransactionSummary deposit(DepositCommand)` and `TransactionSummary withdraw(WithdrawCommand)`; both throw `AccountNotFoundException` for an unknown account. `withdraw` returns the transaction with `APPROVED` or `REJECTED`.
     - **Commands:** `DepositCommand` and `WithdrawCommand` with `accountId` (`UUID`), `amount` (`BigDecimal`, positive) and `currency` (`String`, ISO 4217, default `EUR`).
     - **View:** the existing `TransactionSummary` (type, amount, currency, date, status).
     - **Errors:** amount ≤ 0 or malformed → 400 / field error; another currency → `CurrencyMismatchException` 422 / message; unknown account → 404 / custom 404 page.
     - **REST:** `POST /api/accounts/{id}/deposits` and `POST /api/accounts/{id}/withdrawals` with `MovementRequest` (`amount`, `currency`) → `201` + `Transaction`.
     - **Screens:** `GET /accounts/{id}` (account data, transactions table, deposit and withdrawal forms); `POST /accounts/{id}/deposits` and `POST /accounts/{id}/withdrawals` with Post/Redirect/Get and a flash message (approved or rejected); errors render the detail page again with the message and the entered amount. The list links each account to its detail.
  2. Create the types without logic: the two service methods with `UnsupportedOperationException("pendiente: backend-dev")` bodies and the two `*Def` commands.
  3. Write the OpenAPI fragment for both operations, reusing the `Transaction` schema and the `BadRequest`, `NotFound` and `UnprocessableContent` responses.
- **Acceptance criteria:**
  - [x] Contract agreed by both teammates and frozen in a `docs(contract): ...` commit, with its hash noted.
  - [x] `./mvnw verify` compiles and passes against the contract types.
  - [x] `npx @redocly/cli lint docs/api/openapi.yaml` without errors.

### [x] T1 — Overdraft-limit withdrawal tests and legacy synonyms (~10 min)

- **Side:** back
- **Layer:** domain
- **Files:** `domain/model/CheckingAccountTests.java`, `domain/model/Transaction.java`
- **Depends on:** T0
- **What to do:**
  1. `@ParameterizedTest` + `@CsvSource` for withdrawals at the overdraft limit: retail (balance exactly 0 approved, 0.01 below rejected) and high net worth (exactly -1,000.00 approved, -1,000.01 rejected), checking status and balance.
  2. Rename `un_reintegro_con_saldo_suficiente_se_aprueba` → `una_retirada_...` and `una_transaccion_ya_resuelta_...` → `un_movimiento_ya_resuelto_...` (with their `@DisplayName`), and the `Transaction.requirePending` message "La transacción…" → "El movimiento…".
- **Acceptance criteria:**
  - [x] Parameterized tests green (characterization: validated by temporarily breaking the limit in `isAllowed`).
  - [x] No "reintegro" or "transacción" left in `src/`. `check-domain.mjs` without errors.

### [x] T2 — `DepositCommand` and `WithdrawCommand` validation (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/DepositCommandDef.java`, `application/WithdrawCommandDef.java`, `application/MovementCommandsTests.java`
- **Depends on:** T0
- **What to do:**
  1. `@Value.Check`: positive amount; valid ISO currency (same check as `OpenAccountCommand`); `currency` defaults to `EUR`.
  2. Parameterized tests of the invalid cases for both commands.
- **Acceptance criteria:**
  - [x] Tests green.

### [x] T3 — "Deposit" use case (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/CheckingAccountService.java`, `application/CheckingAccountServiceTests.java`
- **Depends on:** T2
- **What to do:**
  1. `deposit`: load the account (or `AccountNotFoundException`), `account.deposit(...)`, `save`, return the `TransactionSummary`.
  2. Tests (`@Nested` "Ingresar"): approved deposit updates the balance and appears among the transactions; unknown account → `AccountNotFoundException`; another currency → `CurrencyMismatchException` and the balance does not change.
- **Acceptance criteria:**
  - [x] Tests green. No business rules in the service.

### [x] T4 — "Withdraw" use case (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/CheckingAccountService.java`, `application/CheckingAccountServiceTests.java`
- **Depends on:** T3
- **What to do:**
  1. `withdraw`: load, `account.tryWithdraw(...)`, `save`, return the `TransactionSummary` (approved or rejected).
  2. Tests (`@Nested` "Retirar"): parameterized retail / high net worth cases around the overdraft limit returning `APPROVED` or `REJECTED`; a rejected withdrawal leaves the balance unchanged and is still recorded; unknown account → `AccountNotFoundException`.
- **Acceptance criteria:**
  - [x] Tests green.

### [ ] T5 — `POST /api/accounts/{id}/deposits` (~10 min)

- **Side:** back
- **Layer:** presentation
- **Files:** `presentation/rest/AccountController.java`, `presentation/rest/MovementRequest.java` (record with Bean Validation), `presentation/rest/AccountControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. Map the request to `DepositCommand`, respond `201` with the transaction.
  2. `@WebMvcTest` tests with the mocked service: 201; 400 with `errors` for amount 0 or negative; 404 unknown account; 422 `CurrencyMismatchException`.
- **Acceptance criteria:**
  - [ ] Tests green.

### [ ] T6 — `POST /api/accounts/{id}/withdrawals` (~10 min)

- **Side:** back
- **Layer:** presentation
- **Files:** `presentation/rest/AccountController.java`, `presentation/rest/AccountControllerTests.java`
- **Depends on:** T5
- **What to do:**
  1. Same as T5 with `WithdrawCommand`.
  2. Tests: 201 with `APPROVED`; 201 with `REJECTED` (not an error); 400; 404; 422.
- **Acceptance criteria:**
  - [ ] Tests green. Responses match the OpenAPI fragment.

### [ ] T7 — Account detail page (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `templates/accounts/detail.html`, `templates/accounts/list.html`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. `GET /accounts/{id}`: account number, holder, segment (Spanish label), balance and a transactions table (date, type, amount, status in Spanish; rejected ones clearly marked).
  2. An unknown id shows the custom 404 page with status 404.
  3. Each row of the list links to its detail.
  4. `@WebMvcTest` tests with `@MockitoBean`: detail with transactions; unknown account → 404; the list links to the detail.
- **Acceptance criteria:**
  - [ ] Tests green. The view uses no domain types.

### [ ] T8 — Deposit form (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `presentation/web/MovementForm.java`, `templates/accounts/detail.html`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T7
- **What to do:**
  1. Deposit form on the detail page (amount, currency with EUR by default); `POST /accounts/{id}/deposits` → redirect to the detail with a flash "Ingreso … aprobado".
  2. Errors: field error for amount ≤ 0; `DomainException` (another currency) → detail page again with the message and the entered amount.
  3. Tests for the happy path and both errors.
- **Acceptance criteria:**
  - [ ] Tests green. No form error ends in a 500 page.

### [ ] T9 — Withdrawal form (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `templates/accounts/detail.html`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T8
- **What to do:**
  1. Withdrawal form on the detail page; `POST /accounts/{id}/withdrawals` → redirect to the detail with a flash that says whether it was approved or **rejected** (different style).
  2. Same error handling as T8.
  3. Tests: approved flash; rejected flash; field error; domain error.
- **Acceptance criteria:**
  - [ ] Tests green. A rejected withdrawal is shown as such.

### [ ] T10 — End-to-end integration (~10 min)

- **Side:** both
- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java`
- **Depends on:** T1-T9 (after merging `-back` and `-web`)
- **What to do:**
  1. With the real service: open an account; deposit via API → 201 `APPROVED`; withdraw more than the balance (retail) → 201 `REJECTED`; `GET /accounts/{id}` shows both transactions and the rejected one as rejected; web deposit and withdrawal redirect to the detail with their flash.
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
  2. Glossary: `DepositCommand` and `WithdrawCommand` from "previstos" to the main table; R5 and R11 test names after the rename; remove the "sinónimos heredados" pending note.
  3. `fase-1-capas.md`: "Implementado en la #6".
  4. Project status: #6 done, #31 closed, next step `/plan-feature 7`, history row.
- **Acceptance criteria:**
  - [ ] Lint without errors. Glossary and status updated.

## Global acceptance criteria

**Specific:**

- [ ] Contract agreed and frozen in `docs/contracts/deposits-withdrawals.md`.
- [ ] Parameterized withdrawal tests for retail and high-net-worth customers at the overdraft limit (domain and service).
- [ ] Non-positive amount → 400 in REST and a field error on the web; another currency → domain error (422 in REST, message on the web). *(Corrected from the issue: see Context.)*
- [ ] A rejected withdrawal returns 201 with `REJECTED` in REST and is shown as rejected on the web.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The layered architecture is respected (D4, D5), verified by ArchUnit where rules exist and by `revisor-ddd`.
- [ ] The domain does not depend on Spring, `jakarta` or `org.immutables`, and does not use `@Data`, `@Setter`, `@Builder` or `@AllArgsConstructor` (`check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones.
- [ ] `docs/api/openapi.yaml` reflects the changes and the Redocly lint has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English (additions to documents still in Spanish excepted, see Context).
- [ ] `docs/ia/estado-del-proyecto.md` updated: issue status, next step, new pending items and history row.

## Risks and open questions

- **201 for a rejected withdrawal:** the movement is created either way, so the client reads `status`. The alternative (200, or 422 for a rejection) would contradict "a rejected withdrawal is not an error" from the issue.
- **Web form errors render the detail page (no redirect):** this keeps field errors and the entered amount, at the cost of a POST URL in the address bar after an error. Successful operations always use Post/Redirect/Get.
- **Concurrent operations on the same account** in the in-memory repository are not synchronized beyond `save`; accepted for a practice project.
