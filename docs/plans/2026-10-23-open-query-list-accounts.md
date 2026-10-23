# Plan: Open, query and list accounts (back + web)

- **Issue:** [#5](https://github.com/dawnrock/Proyect-DDD-Claude/issues/5)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-23
- **Status:** in progress
- **Total estimate:** ~120 min (12 tasks: T0 + 6 back + 3 web + 2 both)

> Approved by the user on 2026-10-23 (written in Spanish and published on the issue; translated to English when committed, following the documentation language convention from PR #38).

## Goal

First vertical feature: open a checking account, query it and list accounts, from the REST API and from the web. It is the first **team** issue (ADR 0007), so it is implemented with `/implement-plan-team 5`:

1. The contract is agreed and frozen (T0).
2. `backend-dev` and `web-dev` implement in parallel.
3. The lead integrates and tests end to end.

Business rules (minimum deposit, currency) stay in the aggregate. The use case only adds the unique account number check.

### Out of scope

- Web account detail page: querying by id is REST only in this issue. The detail screen comes with #6 (deposits and withdrawals), which is the one that needs it.
- Deposits, withdrawals and transfers (#6, #7). Demo data (#11). Layered ArchUnit rules (#12).
- Generating the account number automatically: whoever opens the account enters it.
- Renaming the legacy synonyms "transacción" and "reintegro": this issue does not touch `Transaction` or its tests.

## Context

- **Already exists:** `CheckingAccount.open(...)` with R1 (minimum €50.00) and R4 (currency); `InMemoryCheckingAccountRepository` (`save`, `findById`, `findByAccountNumber`, sorted `findAll`); `NotFoundException` and `@ApplicationValue` in `application`; `RestExceptionHandler` (422/404/400); Thymeleaf base layout and 404 page; `openapi.yaml` with `paths: {}` and the error responses.
- **Unique account number:** the repository throws `IllegalStateException`. The service must check first with `findByAccountNumber` and throw a new domain exception, `DuplicateAccountNumberException` (`domain.model`, extends `DomainException`). So the API returns 422 and the web shows the message instead of a 500.
- **Commands validate before calling the domain** (Immutables `@Value.Check`, `IllegalArgumentException`). Presentation validates the format first with Bean Validation. The 400 for `IllegalArgumentException` is only a safety net.
- **Why the REST request is a `record` and not an Immutables DTO:** Hibernate Validator only sees constraints on fields and JavaBean getters (`getX()`). Immutables accessors (`holderName()`) are not, so `@NotBlank` would not apply. Responses are Immutables DTOs with `@JsonSerialize` (ADR 0003).
- **D4:** `presentation` uses `application` (service, commands, views) and, from the domain, only `DomainException` and its subclasses. Neither REST nor web touch `CheckingAccount`, `MonetaryAmount` or the repository.
- **D5:** the web does not consume the REST API. Both call `CheckingAccountService`.
- **Tests:**
  - Service: with the real `InMemoryCheckingAccountRepository` (concrete and in memory, no need to mock it).
  - REST and web: `@WebMvcTest` with `@MockitoBean CheckingAccountService`.
  - End to end: in `DddLabApplicationTests`, the only full-context test.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `DuplicateAccountNumberException.java` | New domain exception (account number already in use) |
| `application` | `CheckingAccountService.java` | Service: `openAccount`, `findAccount`, `listAccounts` |
| `application` | `OpenAccountCommandDef.java` | Command with validation (`@Value.Check`) |
| `application` | `AccountSummaryDef.java`, `AccountDetailsDef.java`, `TransactionSummaryDef.java` | Views |
| `application` | `AccountNotFoundException.java` | Extends `NotFoundException` |
| `presentation.rest` | `AccountController.java`, `OpenAccountRequest.java`, `AccountSummaryResponseDef.java`, `AccountDetailsResponseDef.java`, `TransactionResponseDef.java` | `/api/accounts` API |
| `presentation.web` | `AccountWebController.java`, `OpenAccountForm.java`, `templates/accounts/list.html`, `templates/accounts/new.html` | List at `/` and sign-up at `/accounts/new` |
| `docs` | `docs/contracts/open-query-list-accounts.md`, `docs/api/openapi.yaml`, `redocly.yaml`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md` | Contract, API and documentation |

## Tasks

### [x] T0 — Contract (~15 min)

- **Side:** both
- **Layer:** application · docs
- **Files:** `docs/contracts/open-query-list-accounts.md`, contract types in `application` and `domain.model`, fragment of `docs/api/openapi.yaml`
- **Depends on:** —
- **What to do:**
  1. Write the contract from `contract-template.md` (initial proposal: service `openAccount` / `findAccount` / `listAccounts`; `OpenAccountCommand` with `accountNumber`, `holderName`, `segment`, `initialDeposit`, `currency` defaulting to `EUR`; views `AccountSummary`, `AccountDetails`, `TransactionSummary`; errors and REST routes). `backend-dev` and `web-dev` review it.
  2. Create the types without logic: `CheckingAccountService` with `UnsupportedOperationException("pendiente: backend-dev")` bodies, the `*Def` types, `AccountNotFoundException`, `DuplicateAccountNumberException` (constructor and message only).
  3. Write the OpenAPI fragment: the three operations and the schemas, referencing `BadRequest`, `NotFound` and `UnprocessableContent`.
- **Acceptance criteria:**
  - [x] Contract agreed by both teammates and frozen in a `docs(contract): ...` commit, with its hash noted.
  - [x] `./mvnw verify` compiles and passes against the contract types.
  - [x] `npx @redocly/cli lint docs/api/openapi.yaml` without errors.

### [x] T1 — Duplicate account number as a domain rule (~5 min)

- **Side:** back
- **Layer:** domain
- **Files:** `domain/model/DuplicateAccountNumberException.java`, `domain/model/DuplicateAccountNumberExceptionTests.java`
- **Depends on:** T0
- **What to do:**
  1. Test: the exception is a `DomainException` and its Spanish message includes the normalized account number.
- **Acceptance criteria:**
  - [x] Test green. `check-domain.mjs` without errors.

### [x] T2 — `OpenAccountCommand` validation (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/OpenAccountCommandDef.java`, `application/OpenAccountCommandTests.java`
- **Depends on:** T0
- **What to do:**
  1. `@Value.Check`: holder and number not blank; positive deposit; valid ISO currency; valid segment.
  2. `@Value.Default` `currency` = `EUR`.
  3. Parameterized tests of the invalid cases (`IllegalArgumentException`) and a valid one.
- **Acceptance criteria:**
  - [x] `./mvnw test -Dtest=OpenAccountCommandTests` green.

### [x] T3 — "Open account" use case (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/CheckingAccountService.java`, `application/CheckingAccountServiceTests.java`
- **Depends on:** T1, T2
- **What to do:**
  1. `openAccount`: builds `AccountNumber`, `AccountHolder.register(...)` and `MonetaryAmount`; checks `findByAccountNumber`; calls `CheckingAccount.open`; calls `save`; returns `AccountSummary`.
  2. Tests (`@Nested` "Abrir cuenta"): a valid deposit is saved and returns the summary; a deposit below the minimum is rejected and not saved; another currency is rejected; an account number already in use (even with spaces or lowercase) is rejected with `DuplicateAccountNumberException`.
- **Acceptance criteria:**
  - [x] Tests green. The service has no business rules besides the uniqueness check.

### [x] T4 — "Query" and "list" use cases (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `application/CheckingAccountService.java`, `application/CheckingAccountServiceTests.java`
- **Depends on:** T3
- **What to do:**
  1. `findAccount`: returns `AccountDetails` with the initial deposit among its transactions, or throws `AccountNotFoundException`.
  2. `listAccounts`: returns the summaries sorted by account number.
  3. Tests: existing account → details with the initial deposit transaction; unknown account → `AccountNotFoundException`; empty list; several accounts sorted.
- **Acceptance criteria:**
  - [x] Tests green.

### [x] T5 — `POST /api/accounts` (~10 min)

- **Side:** back
- **Layer:** presentation
- **Files:** `presentation/rest/AccountController.java`, `OpenAccountRequest.java` (record with Bean Validation), `AccountSummaryResponseDef.java`, `presentation/rest/AccountControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. The controller maps the request to `OpenAccountCommand` and responds 201 with `Location: /api/accounts/{id}`.
  2. `@WebMvcTest` tests with the mocked service: 201 with body and `Location`; 400 with `errors` if the holder is missing; 422 if the service throws `InsufficientInitialDepositException`.
- **Acceptance criteria:**
  - [x] Tests green.

### [x] T6 — `GET /api/accounts` and `GET /api/accounts/{id}` (~10 min)

- **Side:** back
- **Layer:** presentation
- **Files:** `presentation/rest/AccountController.java`, `AccountDetailsResponseDef.java`, `TransactionResponseDef.java`, `presentation/rest/AccountControllerTests.java`
- **Depends on:** T5
- **What to do:**
  1. Tests: list → 200 with an array; detail → 200 with its transactions; unknown id → 404 `Recurso inexistente`; non-UUID id → 400.
- **Acceptance criteria:**
  - [x] Tests green. Responses match the OpenAPI fragment schemas.

### [x] T7 — Account list at `/` (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `templates/accounts/list.html`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. `GET /` renders a table (number, holder, balance with currency) on the base layout, with a link to "Abrir cuenta".
  2. With no accounts, shows an empty-state message.
  3. `@WebMvcTest` tests with `@MockitoBean`: with and without accounts.
- **Acceptance criteria:**
  - [x] Tests green. The view uses no domain types.

### [x] T8 — Sign-up form: happy path (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `OpenAccountForm.java`, `templates/accounts/new.html`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T7
- **What to do:**
  1. `GET /accounts/new` shows the form: account number, holder, segment (select), initial deposit, currency (EUR by default).
  2. `POST /accounts/new` with valid data calls `openAccount` and redirects to `/` with a success flash message.
  3. Tests: the form is shown; a valid sign-up redirects and calls the service with the expected command.
- **Acceptance criteria:**
  - [x] Tests green.

### [x] T9 — Sign-up form: errors (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/AccountWebController.java`, `templates/accounts/new.html`, `presentation/web/AccountWebControllerTests.java`
- **Depends on:** T8
- **What to do:**
  1. Bean Validation errors (`OpenAccountForm`): the form comes back with one message per field.
  2. A `DomainException` from the service: the form comes back with its message and keeps the entered data.
  3. Tests: blank holder; malformed IBAN (`@Pattern`); insufficient deposit; duplicate account number.
- **Acceptance criteria:**
  - [x] Tests green. No form error ends in a 500 page.

### [x] T10 — End-to-end integration (~10 min)

- **Side:** both
- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java`
- **Depends on:** T1-T9 (after merging `-back` and `-web`)
- **What to do:**
  1. With the real service: `POST /api/accounts` → 201; `GET /api/accounts/{id}` → 200; `GET /` → the HTML contains the account number.
  2. Manual check: `curl -i -X POST localhost:8080/api/accounts ...` → 201; in the browser an account is opened and appears in the list.
- **Acceptance criteria:**
  - [x] Test green. `curl` → 201. Browser check done.

### [x] T11 — Documentation (~10 min)

- **Side:** both
- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `redocly.yaml`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T10
- **What to do:**
  1. Check that `openapi.yaml` reflects what was implemented.
  2. Remove `no-unused-components: off` from `redocly.yaml` (the error responses are now used) and lint.
  3. Glossary: move `AccountNotFoundException` and `OpenAccountCommand` to the main table; add `DuplicateAccountNumberException` ("Número de cuenta duplicado") with its rule and test; add `CheckingAccountService` and the views.
  4. `fase-1-capas.md`: add "Implemented in #5".
  5. Project status: #5 done; next step `/plan-feature 6`; history row.
- **Acceptance criteria:**
  - [x] `npx @redocly/cli lint docs/api/openapi.yaml` without errors or unused-component warnings.
  - [x] Glossary and status updated.

### [x] T12 — Review fixes (~10 min)

- **Side:** both
- **Layer:** presentation · tests
- **Files:** `presentation/web/AccountWebController.java`, `presentation/web/AccountWebControllerTests.java`, `application/OpenAccountCommandTests.java`
- **Depends on:** T10 (added after the `revisor-ddd` review; no blocking findings)
- **What to do:**
  1. A well-formed but non-existent currency (`XYZ`) passes the form's `@Pattern` and the command rejects it with `IllegalArgumentException`: the web ended in a 500. The controller now catches it only while building the command and shows the message on the form.
  2. Test that the segments accepted by `OpenAccountCommand` are exactly `CustomerSegment`'s, so the duplicated list cannot drift.
- **Acceptance criteria:**
  - [x] Web test with `currency=XYZ`: red without the fix (exception), green with it; the service is not called.
  - [x] Segment sync test green. `./mvnw verify` green (109 tests).

## Global acceptance criteria

**Specific:**

- [ ] Contract agreed and frozen in `docs/contracts/open-query-list-accounts.md`.
- [ ] Service tests: open with a valid deposit; reject a deposit below the minimum; reject a duplicate account number; query an unknown account.
- [ ] `@WebMvcTest` tests for REST and web (web with the mocked service).
- [ ] `curl` open account → 201; in the browser an account is opened and appears in the list.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The layered architecture is respected (D4, D5), verified by ArchUnit where rules exist and by `revisor-ddd`.
- [ ] The domain does not depend on Spring, `jakarta` or `org.immutables`, and does not use `@Data`, `@Setter`, `@Builder` or `@AllArgsConstructor` (`check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones.
- [ ] `docs/api/openapi.yaml` reflects the changes and the Redocly lint has no errors.
- [ ] The new business term ("Número de cuenta duplicado") is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English.
- [ ] `docs/ia/estado-del-proyecto.md` updated: issue status, next step, new pending items and history row.

## Decisions taken at approval

- The user enters the account number (form and REST request); it is not generated.
- Duplicate account number → 422 (business rule, existing error codes).
- Race between the check and `save` (two simultaneous sign-ups with the same number): the repository stops it with `IllegalStateException` (500). Accepted for an in-memory practice project.
- Not addressed here: the "holder name is required" test for `AccountHolder` (the service uses `AccountHolder.register` without changing the class).
