# Contract: Mon-A Bank customer sign-up (Hexagonal)

- **Issue:** [#103](https://github.com/dawnrock/Proyect-DDD-Claude/issues/103) · **Plan:** `docs/plans/2026-11-15-customer-sign-up-hexagonal.md`
- **Twin of:** the contract of #95 on `Monolithic` (`docs/contracts/customer-sign-up.md` there, frozen at `8ce0baa`, plus its change of 2026-11-15). Same behaviour, adapted to ports and adapters.
- **Status:** draft
- **Agreed by:** pending

Builds on the frozen contracts `open-query-list-accounts.md`, `deposits-withdrawals.md`, `money-transfers.md`, `login-roles.md` and `account-factory.md`. Business rules R28-R32 are new (glossary). Mapping with `Monolithic`: R28 ↔ R27, R29 ↔ R28, R30 ↔ R29 (+ the username of the 2026-11-15 change), R31 ↔ R30, R32 ↔ R31.

Decisions of the user (2026-11-15): the customer ↔ username relation is an **application output port** (`CustomerOwnershipRepository`); **no new domain events**.

## Application (input ports)

The adapters only know the input ports; `CheckingAccountService` implements them (stubs in this commit).

```java
public interface CheckingAccountCommands {
    // New
    AccountSummary signUp(SignUpCommand command);                                  // registers the customer and opens the first account (generated IBAN) for command.username()
    AccountSummary openAnotherAccount(Caller caller, OpenAnotherAccountCommand c);  // another account for the caller's customer; only the deposit
    AccountSummary regenerateAccountNumber(Caller caller, UUID id);                 // admin only: replaces the IBAN with a newly generated one (R32)

    // Changed in the integration task (T21), shape frozen here
    AccountSummary openAccount(Caller caller, OpenAccountCommand command);          // admin: existing customer by holderTaxId or new customer
}

public interface CheckingAccountQueries {
    boolean isUsernameTaken(String username);                                       // new: the username already belongs to a customer (R30)
    AccountDetails findAccount(Caller caller, UUID id);                             // changed in T21: adds holderTaxId and holderEmail
}
```

Every command runs in the `UnitOfWork`: `signUp` and `openAccount` (new customer) are all or nothing. The account is built first (the domain checks R1 and the currency) and only then are the customer, both ownerships and the account saved. A failed opening leaves no customer, no customer ownership and no account ownership behind. R30 on the document is atomic in the customer adapter (`putIfAbsent`), and R30 on the username is atomic in the ownership adapter.

### Output ports (back only; the web never sees them)

```java
// domain.model
public interface CustomerRepository {
    Customer save(Customer customer);              // DuplicateCustomerException if the document belongs to another customer (R30), atomic
    Optional<Customer> findByTaxId(TaxId taxId);
}

// application (decision of the user): like AccountOwnershipRepository, identity does not reach the domain
public interface CustomerOwnershipRepository {
    void assign(TaxId customer, String username);  // IllegalArgumentException if the username belongs to another customer (R30)
    Optional<String> usernameOf(TaxId customer);
    Optional<TaxId> customerOf(String username);
}
```

`CustomerOwnershipRepository` is in this commit (interface only); `CustomerRepository`, `Customer`, `EmailAddress` are backend-dev's (T2-T4). Both adapters live in `adapter.out.persistence` and are wired in `config.DomainConfiguration`.

### Credentials: `adapter.in.CustomerRegistration`

The application must not know Spring Security, so the user creation stays in the input adapters, in the shared root package `adapter.in` (like `SecuritySupport`), used by `adapter.in.web` and `adapter.in.rest`. It depends on the input ports only.

```java
@Component
public class CustomerRegistration {
    public static final int MINIMUM_PASSWORD_LENGTH = 8;
    public AccountSummary register(SignUpCommand command, String rawPassword);    // checks the username, calls signUp, then creates the USER with a BCrypt hash
}
public class UsernameTakenException extends RuntimeException { UsernameTakenException(String username); }
```

`register` order: (1) username taken (exists as a Spring Security user **or** `isUsernameTaken`) → `UsernameTakenException`, nothing opened; (2) password shorter than 8 → `IllegalArgumentException`; (3) `signUp`; (4) create the user. If (3) fails, no user exists. Steps 1-4 run under one lock (`synchronized`); if step 4 still finds the username taken it is reported as `UsernameTakenException`. Residual risk (step 4 failing for another reason leaves a customer without a login) is recorded in ADR 0012. `SecuritySupport` exposes the in-memory user store as a `UserDetailsManager` so `CustomerRegistration` can create users.

**Who is a customer (web):** a non-admin caller whose `listAccounts(caller)` is not empty. After T21 every account belongs to a registered customer, so this matches the back; `NotACustomerException` stays as the service's safety net.

## Commands (input)

| Command (`*Def`) | Field | Type | Required | Rules |
| ---------------- | ----- | ---- | -------- | ----- |
| `SignUpCommandDef` (new) | `fullName` | `String` | yes | not blank |
| | `taxId` | `String` | yes | matches `OpenAccountCommandDef.TAX_ID_PATTERN` (DNI or NIE after T1); `TaxId` normalises it and checks the control letter (R28) |
| | `email` | `String` | yes | matches `SignUpCommandDef.EMAIL_PATTERN`; `EmailAddress` trims it and normalises it to lower case (R29) |
| | `username` | `String` | yes | `OpenAccountCommandDef.USERNAME_PATTERN` |
| | `initialDeposit` | `BigDecimal` | yes | positive (the minimum, R1, is checked by the domain) |
| | `currency` | `String` | no (`EUR`) | ISO 4217 |
| `OpenAnotherAccountCommandDef` (new) | `initialDeposit` | `BigDecimal` | yes | positive |
| | `currency` | `String` | no (`EUR`) | ISO 4217 |
| `OpenAccountCommandDef` (changed in T21) | `holderTaxId` | `String` | yes | `TAX_ID_PATTERN` |
| | `initialDeposit`, `currency` | | as today | |
| | `holderName`, `holderEmail` (new), `ownerUsername` | `String` | default `""` | required (and validated) only when `holderTaxId` is not a customer yet; for an existing customer they must be blank or equal to the customer's, otherwise `IllegalArgumentException` (400) |

There is no `accountNumber` in `OpenAccountCommandDef` on this branch already (the factory generates it).

`TAX_ID_PATTERN` and `TAX_ID_MESSAGE` live in `OpenAccountCommandDef` and are reused by every command, `OpenAccountRequest`, `SignUpRequest`, `OpenAccountForm` and `SignUpForm`. T1 widens them to NIE (`[XYZxyz0-9]` + 7 digits + letter, spaces allowed) with the message "El documento debe ser un DNI o un NIE"; T1 also gives `TaxId` `Locale.ROOT`.

### Shared input-adapter validation

| Field | Constraint (REST request and web form) |
| ----- | -------------------------------------- |
| `fullName` | `@NotBlank` |
| `taxId` | `@NotBlank` + `@Pattern(regexp = OpenAccountCommandDef.TAX_ID_PATTERN, message = OpenAccountCommandDef.TAX_ID_MESSAGE)` |
| `email` | `@NotBlank` + `@Pattern(regexp = SignUpCommandDef.EMAIL_PATTERN)` |
| `username` | `@NotBlank` + `@Pattern(regexp = OpenAccountCommandDef.USERNAME_PATTERN)` |
| `password` | `@NotBlank` + `@Size(min = CustomerRegistration.MINIMUM_PASSWORD_LENGTH)`; the web also asks for the repetition, which must be equal |
| `initialDeposit` | `@NotNull` + `@Positive` |
| `currency` | `@Pattern(regexp = "[A-Z]{3}")`, `EUR` when absent |

The control letter of the document is checked by the domain, not by the pattern: a well-formed document with a wrong letter reaches the command and fails with `IllegalArgumentException` (400 in REST; the web shows it on step 1).

## Views (output)

| View (`*Def`) | Field | Type | Notes |
| ------------- | ----- | ---- | ----- |
| `AccountSummaryDef` | unchanged | | `accountNumber` is always a generated Mon-A Bank IBAN |
| `AccountDetailsDef` (changed in T21) | `holderTaxId` | `String` | normalised document; `@Value.Default ""` |
| | `holderEmail` | `String` | the customer's e-mail; `@Value.Default ""` |

The defaults let web stubs keep building `AccountDetails` without the new fields until T21. Views never expose domain aggregates or entities.

## Errors

| Exception | When | Web (what the user sees) | REST (status + `ProblemDetail.title`) |
| --------- | ---- | ------------------------ | ------------------------------------- |
| `DuplicateCustomerException` (domain) | document already belongs to a customer (R30) | sign-up: back to step 1 with "Ya eres cliente de Mon-A Bank…" and a link to `/login?form`; admin form: not used (an existing customer is a valid case there) | 422 "Regla de negocio incumplida" |
| `UsernameTakenException` (`adapter.in`) | username already exists (user or customer) | back to step 2 with the message | 422 "Regla de negocio incumplida" (own `@ExceptionHandler` in `RestExceptionHandler`: it is not a `DomainException`) |
| `InsufficientInitialDepositException` | deposit below 50,00 € | stays on step 3 (or the another-account / admin form) with the message | 422 |
| `IllegalArgumentException` | wrong control letter; admin data that contradict an existing customer; username of another customer in `signUp`/`openAccount`; short password | sign-up: raised by the final POST of step 3, the web goes back to step 1 and shows the message as is; admin form: global error | 400 "Petición no válida" |
| `CurrencyMismatchException` (domain) | a currency other than EUR in `signUp`, `openAnotherAccount` or `openAccount` | stays on step 3 (or the another-account / admin form) with the message | 422 |
| `NotACustomerException` (application, `OperationNotAllowedException`) | `openAnotherAccount` by an admin or a user without customer | 403 page | — (no REST endpoint) |
| `ForbiddenOperationException` | `regenerateAccountNumber` by a customer | 403 page | 403 |
| `AccountNotFoundException` | unknown id in `regenerateAccountNumber` | 404 page | 404 |

## REST (fragment of `docs/api/openapi.yaml`)

| Method and path | Security | Request | Response |
| --------------- | -------- | ------- | -------- |
| `POST /api/customers` | public (no credentials, no CSRF: stateless API) | `SignUpRequest {fullName, taxId, email, username, password, initialDeposit, currency?}` | 201 + `Location: /api/accounts/{id}` + `AccountSummary`; 400; 422 |
| `POST /api/accounts/{id}/account-number` | `ADMIN` | — | 200 + `AccountSummary` with the new IBAN; 403; 404 |
| `POST /api/accounts` (changed in T21) | `ADMIN` | `OpenAccountRequest`: `holderTaxId` required; `holderName`, `holderEmail`, `ownerUsername` optional | as today |
| `GET /api/accounts/{id}` (changed in T21) | as today | — | `AccountDetails` with `holderTaxId`, `holderEmail` |

The OpenAPI document itself is updated by the lead in T24, copying the `Monolithic` fragment.

## Screens (web, `adapter.in.web`)

Same routes, templates, texts and behaviour as `Monolithic` after PR #102 (`presentation.web` there).

| Path | Who | Uses | Shows |
| ---- | --- | ---- | ----- |
| `GET /login` | public | — | two doors: "Ya soy cliente" (`/login?form`) and "Quiero ser cliente" (`/signup`); the logout notice |
| `GET /login?form`, `?error` | public | Spring Security | the current login form, with a link back to the doors |
| `GET\|POST /signup` | public, CSRF | session `SignUpForm`, group `PersonalData` | step 1: full name, DNI/NIE, e-mail |
| `GET\|POST /signup/access` | public, CSRF | group `Access` | step 2: username, password, repetition |
| `GET\|POST /signup/account` | public, CSRF | `CustomerRegistration.register` | step 3: deposit, summary, acceptance of conditions, "Abrir mi cuenta"; then signed in and redirected to `/accounts/{id}` with "Te damos la bienvenida a Mon-A Bank. Tu IBAN es …" |
| `GET\|POST /accounts/another` | `USER` | `openAnotherAccount` | deposit only; redirect to the new account with its IBAN |
| `GET /` (list) | `USER` | as today | adds the "Abrir otra cuenta" button for customers (model attribute `customer`, rule above) |
| `GET /accounts/{id}` (detail) | `USER` | as today | the same "Abrir otra cuenta" button for customers |
| `POST /accounts/{id}/account-number` | `ADMIN`, CSRF | `regenerateAccountNumber` | button "Generar nuevo IBAN" with an in-page confirmation on the detail; flash with the new IBAN |
| `GET\|POST /accounts/new` (changed in T21) | `ADMIN` | `openAccount` | document and deposit; name, e-mail and username for a new customer |
| `GET /accounts/{id}` (changed in T21) | as today | `findAccount` | adds the holder's document and e-mail |

Entering a later step without the previous ones redirects to `/signup`. The password is kept only in the server session between steps 2 and 3 and never rendered. Web tests mock `CheckingAccountCommands`, `CheckingAccountQueries` and `CustomerRegistration` with `@MockitoBean`.

## Changes

<!-- Each renegotiation: date, who asked, what changed and why. -->

- —
