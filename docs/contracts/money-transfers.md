# Contract: transfers between accounts

- **Issue:** [#7](https://github.com/dawnrock/Proyect-DDD-Claude/issues/7) · **Plan:** `docs/plans/2026-10-25-money-transfers.md`
- **Status:** frozen at `0bdd261`
- **Agreed by:** backend-dev, web-dev (rounds: 1; both agreed with minor changes; the teammates could not message each other, so the lead merged both reviews)

Builds on the frozen contracts of #5 (`open-query-list-accounts.md`) and #6 (`deposits-withdrawals.md`): `CheckingAccountService`, `AccountDetails`, `AccountSummary`, `AccountNotFoundException`, the web 404 advice and the shared presentation validation are reused as they are.

## Application service

One new method on the concrete `CheckingAccountService` (phase 1):

```java
public class CheckingAccountService {
    // ... openAccount, findAccount, listAccounts (#5), deposit, withdraw (#6)
    TransferSummary transfer(TransferCommand command);  // loads both accounts, delegates to MoneyTransferService, saves both
}
```

The rule spans two aggregates, so it lives in the domain service `domain.service.MoneyTransferService` (plain Java, stateless, created with `new` inside `CheckingAccountService`; no Spring bean). Presentation never sees it (D4).

A rejected transfer is **not an exception**: the source records a `REJECTED` withdrawal, nothing reaches the target, no balance changes, and `transfer` returns it with status `REJECTED`. The service always saves **both** accounts.

Order of the checks: load source, load target (`AccountNotFoundException` for either) → same account (R17) → target currency (R19) → withdrawal in the source (R5-R9) → deposit in the target only if approved (R18).

## Commands (input)

| Command (`*Def`) | Field | Type | Required | Rules |
| ---------------- | ----- | ---- | -------- | ----- |
| `TransferCommandDef` | `sourceAccountId` | `UUID` | yes | |
| | `targetAccountId` | `UUID` | yes | may be equal to the source: that is a **business** rule (R17, 422), not a format one |
| | `amount` | `BigDecimal` | yes | positive after rounding to the currency's digits (same as `DepositCommand`) |
| | `currency` | `String` | no (default `EUR`) | ISO 4217 code |

Violations throw `IllegalArgumentException` when the command is built (`@Value.Check` with `CommandValidation.requireValidMovement`), with Spanish messages. Presentation validates first. **The `@Value.Check` arrives with T4** (backend-dev): the frozen skeleton has no validation yet, so web tests mock the service and do not rely on the command throwing.

**Absent `currency`:** presentation (REST controller and web form) does **not** call `.currency(null)` on the builder (Immutables would throw `NullPointerException` → 500); it only sets the currency when present, so the `EUR` default applies.

### Shared presentation validation

The REST request (`TransferRequest`, a `record`) and the web form (`TransferForm`) apply the same constraints:

| Field | Constraint | Why |
| ----- | ---------- | --- |
| `sourceAccountId` (REST only; on the web it is the `{id}` of the path) | `@NotNull` | |
| `targetAccountId` | `@NotNull` | on the web: "Elige la cuenta de destino" |
| `amount` | `@NotNull` + `@Positive` | non-positive amount → 400 / field error (as in #6) |
| `currency` | `@Pattern(regexp = "[A-Z]{3}")`; `EUR` when absent | an unknown code (`XYZ`) is rejected by the command (REST 400; web message on the form) |

## Views (output)

| View (`*Def`) | Field | Type | Notes |
| ------------- | ----- | ---- | ----- |
| `TransferSummaryDef` | `sourceAccountId` | `UUID` | |
| | `sourceAccountNumber` | `String` | normalized IBAN |
| | `targetAccountId` | `UUID` | |
| | `targetAccountNumber` | `String` | normalized IBAN; the web flash shows it ("… a ES79… aprobada") |
| | `amount` | `BigDecimal` | amount of the **withdrawal in the source** returned by the domain service, already rounded to the currency's digits (`25.00`), not the raw command value |
| | `currency` | `String` | |
| | `occurredOn` | `Instant` | time of that same withdrawal in the source |
| | `status` | `String` | `APPROVED` or `REJECTED` (status of the withdrawal in the source) |

The transfer appears in the accounts' details as two ordinary `TransactionSummary` rows: a `WITHDRAWAL` in the source (approved or rejected) and, only if approved, a `DEPOSIT` in the target. There are no new transaction types.

The web builds the target select from the existing `listAccounts()` (`AccountSummary`), excluding the current account.

## Errors

| Exception | When | Web (what the user sees) | REST (status + `ProblemDetail.title`) |
| --------- | ---- | ------------------------ | ------------------------------------- |
| `AccountNotFoundException` | unknown source or target id | custom 404 page with status 404 | 404 `Recurso inexistente` |
| `SameAccountTransferException` (domain, new) | source and target are the same account (only via a crafted request on the web) | detail page again with the message | 422 `Regla de negocio incumplida` |
| `CurrencyMismatchException` (domain) | amount in a currency other than the accounts' | detail page again with the message and the entered values | 422 `Regla de negocio incumplida` |
| Bean Validation of request / form | target missing; amount missing, zero or negative; malformed currency | detail page again with the field error | 400 `Petición no válida` + `errors` |
| `IllegalArgumentException` from the command | unknown currency; positive amount that rounds to zero | detail page again with the message (caught only while building the command) | 400 `Petición no válida` |
| — (not an error) | withdrawal not allowed by the source's overdraft rules | "rejected" flash on the detail page | 201 with `status: REJECTED` |

The web catches `DomainException` (the base class) and `IllegalArgumentException` only while building the command, as in #6.

## REST (fragment of `docs/api/openapi.yaml`)

| Method and path | Request | Response |
| --------------- | ------- | -------- |
| `POST /api/transfers` | `TransferRequest` (`sourceAccountId`, `targetAccountId`, `amount`, `currency`) | 201 + `Transfer` (`APPROVED` or `REJECTED`); 400; 404; 422 |

New `TransferController` in `presentation.rest` (not `AccountController`: the resource is not under `/api/accounts`). No `Location` header: there is no transfer resource. Schemas `TransferRequest` and `Transfer` and tag `transfers` in `openapi.yaml`.

## Screens (web)

| Path | Uses | Shows |
| ---- | ---- | ----- |
| `GET /accounts/{id}` (#6) | `findAccount(id)`, `listAccounts()` | adds a "Transferir" form: target account select (other accounts as "number · holder", current one excluded), amount, currency (EUR by default; EUR/USD/GBP as in #6). With no other account, the hint "No hay otras cuentas a las que transferir" instead of the form |
| `POST /accounts/{id}/transfers` | `transfer(command)` | `APPROVED` → redirect to `/accounts/{id}` with flash "Transferencia de … a … aprobada"; `REJECTED` → redirect with a "rejected" flash (different style, text valid for both segments); error → detail page again (200) with the message and the entered values |

The page gets a third independent model attribute, `transferForm` (`targetAccountId`, `amount`, `currency`), so an error in one form does not show errors in the others. Re-rendering after an error calls `findAccount(id)` and `listAccounts()` again.

## Changes

<!-- Each renegotiation: date, who asked, what changed and why. -->

- 2026-10-25, review round 1 (before freezing): web-dev asked for `sourceAccountNumber` and `targetAccountNumber` in `TransferSummary` (and the `Transfer` schema), so the flash can name the target without cross-checking `listAccounts()`. backend-dev clarified that `amount` and `occurredOn` come from the source's withdrawal (rounded amount), that an absent `currency` must not be passed as `null` to the builder, and that the command's `@Value.Check` arrives with T4. All accepted by the lead.
