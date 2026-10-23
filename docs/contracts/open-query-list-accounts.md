# Contract: open, query and list accounts

- **Issue:** [#5](https://github.com/dawnrock/Proyect-DDD-Claude/issues/5) · **Plan:** `docs/plans/2026-10-23-open-query-list-accounts.md`
- **Status:** agreed
- **Agreed by:** backend-dev, web-dev (rounds: 1; both agreed with clarifications, merged by the lead)

## Application service

Phase 1: concrete class `com.pronoide.ddd.application.CheckingAccountService` (`@Service`), backed by `InMemoryCheckingAccountRepository`.

```java
public class CheckingAccountService {
    AccountSummary openAccount(OpenAccountCommand command);  // opens the account with its initial deposit and saves it
    AccountDetails findAccount(UUID id);                      // account with its transactions; AccountNotFoundException if unknown
    List<AccountSummary> listAccounts();                      // all accounts, sorted by account number (empty list if none)
}
```

## Commands (input)

| Command (`*Def`) | Field | Type | Required | Rules |
| ---------------- | ----- | ---- | -------- | ----- |
| `OpenAccountCommandDef` | `accountNumber` | `String` | yes | not blank; the domain normalizes it (no spaces, uppercase) and rejects anything that is not `ES` + 22 digits |
| | `holderName` | `String` | yes | not blank |
| | `segment` | `String` | yes | `RETAIL` or `HIGH_NET_WORTH` |
| | `initialDeposit` | `BigDecimal` | yes | positive |
| | `currency` | `String` | no (default `EUR`) | ISO 4217 code |

Violations of these rules throw `IllegalArgumentException` when the command is built (`@Value.Check`), including a `segment` other than the two allowed values. Presentation validates the format first, so they should not reach the command.

### Shared presentation validation

The REST request (`OpenAccountRequest`) and the web form (`OpenAccountForm`) apply the **same** Bean Validation, so whatever they accept never makes the domain throw `IllegalArgumentException`:

| Field | Constraint | Why |
| ----- | ---------- | --- |
| `accountNumber` | `@NotBlank` + `@Pattern(regexp = "^ *[Ee][Ss]( *\\d){22} *$")` | accepts spaces and lowercase, which `AccountNumber` normalizes; no tabs or other whitespace, because the domain only removes spaces. A malformed IBAN is an `IllegalArgumentException` in the domain, not a `DomainException` |
| `holderName` | `@NotBlank` | |
| `segment` | `@NotBlank` + `@Pattern(regexp = "RETAIL\|HIGH_NET_WORTH")`, typed `String` (not an enum) | with an enum, an invalid value would fail JSON parsing without the per-field `errors` map. The web offers a select with these two values (Spanish labels), without importing `CustomerSegment` |
| `initialDeposit` | `@NotNull` + `@Positive` | |
| `currency` | `@Pattern(regexp = "[A-Z]{3}")`; `EUR` when absent | an unknown but well-formed code (`XYZ`) still reaches the command and gives 400 (safety net). The web offers a select (`EUR` by default) |

**Decimals:** `MonetaryAmount` rounds `initialDeposit` to the currency's digits with `HALF_UP` (2 for EUR), so `49.995` is accepted as `50.00`. No decimals are rejected; the web may use `step="0.01"` on the input.

## Views (output)

| View (`*Def`) | Field | Type | Notes |
| ------------- | ----- | ---- | ----- |
| `AccountSummaryDef` | `id` | `UUID` | |
| | `accountNumber` | `String` | normalized IBAN |
| | `holderName` | `String` | |
| | `balance` | `BigDecimal` | scaled to the currency (2 decimals for EUR) |
| | `currency` | `String` | ISO 4217 |
| `AccountDetailsDef` | `id`, `accountNumber`, `holderName`, `balance`, `currency` | | same as `AccountSummary` |
| | `segment` | `String` | `RETAIL` or `HIGH_NET_WORTH` |
| | `transactions` | `List<TransactionSummary>` | in registration order; a new account has one approved `DEPOSIT` |
| `TransactionSummaryDef` | `id` | `UUID` | |
| | `type` | `String` | `DEPOSIT` or `WITHDRAWAL` |
| | `amount` | `BigDecimal` | |
| | `currency` | `String` | |
| | `occurredOn` | `Instant` | |
| | `status` | `String` | `PENDING`, `APPROVED` or `REJECTED` |

Views never expose domain aggregates, entities or value objects.

## Errors

| Exception | When | Web (what the user sees) | REST (status + `ProblemDetail.title`) |
| --------- | ---- | ------------------------ | ------------------------------------- |
| `InsufficientInitialDepositException` (domain) | deposit < 50.00 EUR | back to the form with the message | 422 `Regla de negocio incumplida` |
| `CurrencyMismatchException` (domain) | deposit not in EUR | back to the form with the message | 422 `Regla de negocio incumplida` |
| `DuplicateAccountNumberException` (domain, new) | account number already used by another account; thrown **by the service**, which calls `findByAccountNumber` before `save` | back to the form with the message | 422 `Regla de negocio incumplida` |
| `AccountNotFoundException` (application, extends `NotFoundException`) | `findAccount` with an unknown id | — (no web detail page in #5) | 404 `Recurso inexistente` |
| Bean Validation of the request / form | missing or malformed field | form with one message per field | 400 `Petición no válida` + `errors` |
| `IllegalArgumentException` | invalid value that slipped past validation (safety net) | — (should not happen: the form validates first) | 400 `Petición no válida` |

**Known limitation:** two simultaneous sign-ups with the same number can both pass the service check; then the repository's `save` throws `IllegalStateException` (500). Accepted for an in-memory practice project.

The web catches `DomainException` (the base class) for the form, so new business rules need no changes there. Domain messages are in Spanish and are shown as they come.

## REST (fragment of `docs/api/openapi.yaml`)

| Method and path | Request | Response |
| --------------- | ------- | -------- |
| `POST /api/accounts` | `OpenAccountRequest` (record with Bean Validation) | 201 + `Location: /api/accounts/{id}` + `AccountSummary`; 400; 422 |
| `GET /api/accounts` | — | 200 + `AccountSummary[]` |
| `GET /api/accounts/{id}` | — | 200 + `AccountDetails`; 400 if `id` is not a UUID; 404 |

REST DTOs live in `presentation.rest` and map from the views: the request is a `record` (Hibernate Validator does not see constraints on Immutables accessors), the responses are Immutables `*Def` types with `@JsonSerialize`. Amounts are JSON numbers.

## Screens (web)

| Path | Uses | Shows |
| ---- | ---- | ----- |
| `GET /` | `listAccounts()` | table with account number, holder and balance with currency; empty-state message; link "Abrir cuenta" to `/accounts/new`; success flash message after a sign-up |
| `GET /accounts/new` | — | form: account number, holder, segment (select), initial deposit, currency (EUR by default) |
| `POST /accounts/new` | `openAccount(command)` | valid → redirect to `/` with a flash message (Post/Redirect/Get); validation error → form with messages per field; `DomainException` → form with the domain message and the entered data |

## Changes

<!-- Each renegotiation: date, who asked, what changed and why. -->

- 2026-10-23, review round 1 (before freezing): backend-dev asked for an IBAN pattern without `\s` (the domain only removes spaces), `segment` as a validated `String` in the request, and the duplicate check/race documented; web-dev asked for the IBAN, currency, decimals and segment validation to be explicit. Added "Shared presentation validation" and the known limitation.
