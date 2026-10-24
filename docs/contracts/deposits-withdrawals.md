# Contract: deposits and withdrawals with account details

- **Issue:** [#6](https://github.com/dawnrock/Proyect-DDD-Claude/issues/6) · **Plan:** `docs/plans/2026-10-23-deposits-withdrawals.md`
- **Status:** frozen at `dbc59e5`
- **Agreed by:** backend-dev, web-dev (rounds: 1; both agreed, clarifications merged by the lead)

Builds on the frozen contract of #5 (`open-query-list-accounts.md`): `CheckingAccountService`, `AccountDetails`, `TransactionSummary`, `AccountNotFoundException` and the shared presentation validation are reused as they are.

## Application service

Two new methods on the concrete `CheckingAccountService` (phase 1):

```java
public class CheckingAccountService {
    // ... openAccount, findAccount, listAccounts (#5)
    TransactionSummary deposit(DepositCommand command);    // loads the account, deposits, saves; always APPROVED
    TransactionSummary withdraw(WithdrawCommand command);  // loads, tries to withdraw, saves; APPROVED or REJECTED
}
```

A rejected withdrawal is **not an exception**: the domain records it with status `REJECTED`, the balance does not change, and `withdraw` returns it like any other transaction. The service always calls `save` after changing the account (the repository stores references, but a real one would not).

## Commands (input)

| Command (`*Def`) | Field | Type | Required | Rules |
| ---------------- | ----- | ---- | -------- | ----- |
| `DepositCommandDef` | `accountId` | `UUID` | yes | |
| | `amount` | `BigDecimal` | yes | positive **after rounding to the currency's digits** (`0.001` EUR rounds to `0.00` and is rejected here, instead of reaching the domain's `requirePositive`) |
| | `currency` | `String` | no (default `EUR`) | ISO 4217 code |
| `WithdrawCommandDef` | `accountId`, `amount`, `currency` | | | same as `DepositCommandDef` |

Violations throw `IllegalArgumentException` when the command is built (`@Value.Check`), with Spanish messages. Presentation validates first, so they should not reach the command.

### Shared presentation validation

The REST request (`MovementRequest`, a `record`) and the web form (`MovementForm`) apply the same constraints:

| Field | Constraint | Why |
| ----- | ---------- | --- |
| `amount` | `@NotNull` + `@Positive` | **A non-positive amount is a 400 / field error, not a domain error** (R3; decided by the user on 2026-10-23, correcting the issue's criterion) |
| `currency` | `@Pattern(regexp = "[A-Z]{3}")`; `EUR` when absent | a well-formed but unknown code (`XYZ`) is rejected by the command: REST 400 (safety net); the web shows the message on the form (same pattern as #5's T12) |

Decimals: `MonetaryAmount` rounds to the currency's digits with `HALF_UP`, as in #5.

## Views (output)

The existing `TransactionSummary` (#5): `id`, `type` (`DEPOSIT` | `WITHDRAWAL`), `amount`, `currency`, `occurredOn`, `status` (`APPROVED` | `REJECTED`; `PENDING` never leaves the aggregate). The detail page uses the existing `AccountDetails` (`findAccount`), whose `transactions` are in registration order.

No new views.

## Errors

| Exception | When | Web (what the user sees) | REST (status + `ProblemDetail.title`) |
| --------- | ---- | ------------------------ | ------------------------------------- |
| `AccountNotFoundException` | unknown account id (detail page or operation) | custom 404 page with status 404 | 404 `Recurso inexistente` |
| `CurrencyMismatchException` (domain) | amount in a currency other than the account's | detail page again with the message and the entered amount | 422 `Regla de negocio incumplida` |
| Bean Validation of request / form | amount missing, zero or negative; malformed currency | detail page again with the field error | 400 `Petición no válida` + `errors` |
| `IllegalArgumentException` from the command | well-formed but unknown currency; positive amount that rounds to zero | detail page again with the message (caught only while building the command) | 400 `Petición no válida` |
| — (not an error) | withdrawal not allowed by the overdraft rules | flash "rejected" message on the detail page; the row is marked as rejected | 201 with `status: REJECTED` |

The web catches `DomainException` (the base class) for the forms, as in #5, and `IllegalArgumentException` only while building the command (an `IllegalArgumentException` from the service would be a programming error and must not be hidden).

**Web 404:** a `@ControllerAdvice` in `presentation.web` handles `NotFoundException`, sets status 404 and renders the existing `error/404` view. It is scoped with `basePackageClasses` to `presentation.web`, **not** with `annotations = Controller.class`: `@RestController` is meta-annotated with `@Controller`, so that selector would also catch REST errors and compete with `RestExceptionHandler`.

## REST (fragment of `docs/api/openapi.yaml`)

| Method and path | Request | Response |
| --------------- | ------- | -------- |
| `POST /api/accounts/{id}/deposits` | `MovementRequest` (`amount`, `currency`) | 201 + `Transaction`; 400; 404; 422 |
| `POST /api/accounts/{id}/withdrawals` | `MovementRequest` | 201 + `Transaction` (`APPROVED` or `REJECTED`); 400; 404; 422 |

No `Location` header: there is no transaction resource. A non-UUID `{id}` is a 400 (Spring).

## Screens (web)

| Path | Uses | Shows |
| ---- | ---- | ----- |
| `GET /` (#5) | `listAccounts()` | each row links to `/accounts/{id}` |
| `GET /accounts/{id}` | `findAccount(id)` | account number, holder, segment (Spanish label), balance with currency; transactions table (date, type, amount, status in Spanish; rejected rows clearly marked); a deposit form and a withdrawal form (amount, currency with EUR by default); flash messages |
| `POST /accounts/{id}/deposits` | `deposit(command)` | success → redirect to `/accounts/{id}` with flash "Ingreso de … aprobado"; error → detail page again (200) with the message and the entered amount |
| `POST /accounts/{id}/withdrawals` | `withdraw(command)` | `APPROVED` → redirect with a success flash; `REJECTED` → redirect with a "rejected" flash (different style); error → same as deposits |

The page has two independent model attributes (`depositForm`, `withdrawalForm`), so an error in one form does not show errors in the other; re-rendering after an error calls `findAccount(id)` again. The currency select offers `EUR` (default), `USD` and `GBP`, as in #5. `occurredOn` (`Instant`) is formatted in the template with the system time zone.

Successful operations always use Post/Redirect/Get; error responses render the page directly so field errors and the entered amount are kept. Session tracking is cookie-only (#5), so the redirects stay clean.

## Changes

<!-- Each renegotiation: date, who asked, what changed and why. -->

- 2026-10-23, review round 1 (before freezing): backend-dev pointed out that a positive amount can round to zero (`0.001` EUR) and reach the domain's `IllegalArgumentException`; the command now rejects it. web-dev proposed a web advice for `NotFoundException`; the lead scoped it to `presentation.web` with `basePackageClasses` because `@RestController` is meta-annotated with `@Controller`. Two independent form attributes and the EUR/USD/GBP select documented.
