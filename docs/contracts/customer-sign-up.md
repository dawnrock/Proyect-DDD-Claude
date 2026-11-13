# Contract: Mon-A Bank customer sign-up

- **Issue:** [#95](https://github.com/dawnrock/Proyect-DDD-Claude/issues/95) · **Plan:** `docs/plans/2026-11-12-customer-sign-up.md`
- **Status:** draft
- **Agreed by:** backend-dev, web-dev (rounds: —)

Builds on the frozen contracts of #5, #6, #7 and #50 (`open-query-list-accounts.md`, `deposits-withdrawals.md`, `money-transfers.md`, `login-roles.md`) and on #99 (`holderTaxId`, simulated tax agency). Business rules R27-R31 are new (glossary).

## Application service

Phase 1: concrete class `CheckingAccountService` (stubs in this commit) plus the presentation component `CustomerRegistration`, shared by web and REST because `application` must not know Spring Security.

```java
public class CheckingAccountService {
    // New
    AccountSummary signUp(SignUpCommand command);                                  // registers the customer and opens the first account (generated IBAN) for command.username()
    AccountSummary openAnotherAccount(Caller caller, OpenAnotherAccountCommand c);  // another account for the caller's customer; only the deposit
    AccountSummary regenerateAccountNumber(Caller caller, UUID id);                 // admin only: replaces the IBAN with a newly generated one (R31)

    // Changed in the integration task (T20), shape frozen here
    AccountSummary openAccount(Caller caller, OpenAccountCommand command);          // admin: generated IBAN; existing customer by holderTaxId or new customer
    AccountDetails findAccount(Caller caller, UUID id);                             // adds holderTaxId and holderEmail
}

public class CustomerRegistration {                                                 // presentation (root package), @Component
    static final int MINIMUM_PASSWORD_LENGTH = 8;
    AccountSummary register(SignUpCommand command, String rawPassword);             // checks the username, calls signUp, then creates the USER with a BCrypt hash
}
```

`register` order: (1) username taken → `UsernameTakenException`, nothing opened; (2) password shorter than 8 → `IllegalArgumentException`; (3) `signUp`; (4) create the user. If (3) fails, no user exists.

## Commands (input)

| Command (`*Def`) | Field | Type | Required | Rules |
| ---------------- | ----- | ---- | -------- | ----- |
| `SignUpCommandDef` (new) | `fullName` | `String` | yes | not blank |
| | `taxId` | `String` | yes | matches `OpenAccountCommandDef.TAX_ID_PATTERN` (DNI or NIE after T1); `TaxId` normalises it and checks the control letter (R27) |
| | `email` | `String` | yes | matches `SignUpCommandDef.EMAIL_PATTERN`; `EmailAddress` normalises it to lower case (R28) |
| | `username` | `String` | yes | `OpenAccountCommandDef.USERNAME_PATTERN` |
| | `initialDeposit` | `BigDecimal` | yes | positive (the minimum, R1, is checked by the domain) |
| | `currency` | `String` | no (`EUR`) | ISO 4217 |
| `OpenAnotherAccountCommandDef` (new) | `initialDeposit` | `BigDecimal` | yes | positive |
| | `currency` | `String` | no (`EUR`) | ISO 4217 |
| `OpenAccountCommandDef` (changed in T20) | `accountNumber` | — | **removed** | the bank generates it |
| | `holderTaxId` | `String` | yes | `TAX_ID_PATTERN` |
| | `initialDeposit`, `currency` | | as today | |
| | `holderName`, `holderEmail`, `ownerUsername` | `String` | default `""` | required (and validated) only when `holderTaxId` is not a customer yet; for an existing customer they must be blank or equal to the customer's, otherwise `IllegalArgumentException` (400) |

`TAX_ID_PATTERN` and `TAX_ID_MESSAGE` live in `OpenAccountCommandDef` and are reused by every command, `OpenAccountRequest`, `SignUpRequest`, `OpenAccountForm` and `SignUpForm`. T1 widens them to NIE (`[XYZxyz0-9]` + 7 digits + letter, spaces allowed) with the message "El documento debe ser un DNI o un NIE".

### Shared presentation validation

| Field | Constraint (REST request and web form) |
| ----- | -------------------------------------- |
| `fullName` | `@NotBlank` |
| `taxId` | `@NotBlank` + `@Pattern(regexp = OpenAccountCommandDef.TAX_ID_PATTERN)` |
| `email` | `@NotBlank` + `@Pattern(regexp = SignUpCommandDef.EMAIL_PATTERN)` |
| `username` | `@NotBlank` + `@Pattern(regexp = OpenAccountCommandDef.USERNAME_PATTERN)` |
| `password` | `@NotBlank` + `@Size(min = CustomerRegistration.MINIMUM_PASSWORD_LENGTH)`; the web also asks for the repetition, which must be equal |
| `initialDeposit` | `@NotNull` + `@Positive` |
| `currency` | `@Pattern(regexp = "[A-Z]{3}")`, `EUR` when absent |

The control letter of the document is checked by the domain, not by the pattern: a well-formed document with a wrong letter reaches the command and fails with `IllegalArgumentException` (400 in REST; the web shows it on step 1).

## Views (output)

| View (`*Def`) | Field | Type | Notes |
| ------------- | ----- | ---- | ----- |
| `AccountSummaryDef` | unchanged | | `accountNumber` is now always a generated Mon-A Bank IBAN |
| `AccountDetailsDef` (changed in T20) | `holderTaxId` | `String` | normalised document |
| | `holderEmail` | `String` | the customer's e-mail |

Views never expose domain aggregates or entities.

## Errors

| Exception | When | Web (what the user sees) | REST (status + `ProblemDetail.title`) |
| --------- | ---- | ------------------------ | ------------------------------------- |
| `DuplicateCustomerException` (domain) | document already belongs to a customer (R29) | sign-up: back to step 1 with "Ya eres cliente de Mon-A Bank…" and a link to `/login?form`; admin form: field error on the document is **not** used (an existing customer is a valid case there) | 422 "Regla de negocio incumplida" |
| `UsernameTakenException` (presentation) | username already exists | back to step 2 with the message | 422 "Regla de negocio incumplida" |
| `InsufficientInitialDepositException` | deposit below 50,00 € | stays on step 3 (or the another-account / admin form) with the message | 422 |
| `IllegalArgumentException` | wrong control letter; admin data that contradict an existing customer; short password | sign-up: step 1 (document) or the field concerned; admin form: global error | 400 "Petición no válida" |
| `NotACustomerException` | `openAnotherAccount` by an admin or a user without customer | 403 page | — (no REST endpoint) |
| `ForbiddenOperationException` | `regenerateAccountNumber` by a customer | 403 page | 403 |
| `AccountNotFoundException` | unknown id in `regenerateAccountNumber` | 404 page | 404 |

## REST (fragment of `docs/api/openapi.yaml`)

| Method and path | Security | Request | Response |
| --------------- | -------- | ------- | -------- |
| `POST /api/customers` | public (no credentials, no CSRF: stateless API) | `SignUpRequest {fullName, taxId, email, username, password, initialDeposit, currency?}` | 201 + `Location: /api/accounts/{id}` + `AccountSummary`; 400; 422 |
| `POST /api/accounts/{id}/account-number` | `ADMIN` | — | 200 + `AccountSummary` with the new IBAN; 403; 404 |
| `POST /api/accounts` (changed in T20) | `ADMIN` | `OpenAccountRequest` without `accountNumber`; `holderTaxId` required; `holderName`, `holderEmail`, `ownerUsername` optional | as today |
| `GET /api/accounts/{id}` (changed in T20) | as today | — | `AccountDetails` with `holderTaxId`, `holderEmail` |

## Screens (web)

| Path | Who | Uses | Shows |
| ---- | --- | ---- | ----- |
| `GET /login` | public | — | two doors: "Ya soy cliente" (`/login?form`) and "Quiero ser cliente" (`/signup`); the logout notice |
| `GET /login?form`, `?error` | public | Spring Security | the current login form, with a link back to the doors |
| `GET\|POST /signup` | public, CSRF | session `SignUpForm`, group `PersonalData` | step 1: full name, DNI/NIE, e-mail |
| `GET\|POST /signup/access` | public, CSRF | group `Access` | step 2: username, password, repetition |
| `GET\|POST /signup/account` | public, CSRF | `CustomerRegistration.register` | step 3: deposit, summary, acceptance of conditions, "Abrir mi cuenta"; then signed in and redirected to `/accounts/{id}` with "Te damos la bienvenida a Mon-A Bank. Tu IBAN es …" |
| `GET\|POST /accounts/another` | `USER` | `openAnotherAccount` | deposit only; redirect to the new account with its IBAN |
| `GET /` (list) | `USER` | as today | adds the "Abrir otra cuenta" button for customers |
| `POST /accounts/{id}/account-number` | `ADMIN`, CSRF | `regenerateAccountNumber` | button "Generar nuevo IBAN" with an in-page confirmation on the detail; flash with the new IBAN |
| `GET\|POST /accounts/new` (changed in T20) | `ADMIN` | `openAccount` | document and deposit; name, e-mail and username for a new customer; no IBAN field |
| `GET /accounts/{id}` (changed in T20) | as today | `findAccount` | adds the holder's document and e-mail |

Entering a later step without the previous ones redirects to `/signup`. The password is kept only in the server session between steps 2 and 3 and never rendered.

## Changes

- —
