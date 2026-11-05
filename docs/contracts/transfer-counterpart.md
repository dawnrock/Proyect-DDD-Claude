# Contract: transfers show their counterpart account

- **Issue:** [#72](https://github.com/dawnrock/Proyect-DDD-Claude/issues/72) · **Plan:** `docs/plans/2026-11-05-web-pending-items.md`
- **Status:** draft
- **Agreed by:** backend-dev, web-dev (rounds: —)

Extends the transaction view of `deposits-withdrawals.md` (#6) and `money-transfers.md` (#7). Transfers are still recorded as a `WITHDRAWAL` on the source and a `DEPOSIT` on the target (#7 decision); each of those two transactions now also records the **counterpart account** (the other account of the transfer). The input ports do not change.

## Application service

Phase 2 input ports, unchanged signatures:

```java
public interface CheckingAccountQueries {
    // Each TransactionSummary of the details now carries counterpartAccountNumber when it belongs to a transfer.
    AccountDetails findAccount(Caller caller, UUID accountId);
    List<AccountSummary> listAccounts(Caller caller);       // unchanged; AccountSummary.segment() already exists (#18)
}

public interface CheckingAccountCommands {
    // The returned TransactionSummary of a plain deposit or withdrawal has no counterpart.
    TransactionSummary deposit(Caller caller, DepositCommand command);
    TransactionSummary withdraw(Caller caller, WithdrawCommand command);
    TransferSummary transfer(Caller caller, TransferCommand command);   // unchanged
}
```

## Commands (input)

No changes.

## Views (output)

| View (`*Def`) | Field | Type | Notes |
| ------------- | ----- | ---- | ----- |
| `TransactionSummaryDef` | `counterpartAccountNumber` | `Optional<String>` | **New.** Normalised IBAN of the other account when the transaction belongs to a transfer: the target on the source's withdrawal (approved or rejected), the source on the target's deposit. Empty for plain deposits, plain withdrawals and the initial deposit |
| | other fields | — | Unchanged; `type` stays `DEPOSIT` / `WITHDRAWAL` |
| `AccountDetailsDef` | `transactions` | `List<TransactionSummary>` | Unchanged order: oldest first. Reversing it is a web concern |
| `AccountSummaryDef` | `segment` | `String` | Unchanged (`RETAIL` / `HIGH_NET_WORTH`); the web starts showing it in the list |

Views never expose domain aggregates or entities.

## Errors

No new errors.

## REST (fragment of `docs/api/openapi.yaml`)

| Method and path | Request | Response |
| --------------- | ------- | -------- |
| `GET /api/accounts/{id}` | — | 200 + `AccountDetails`; each `Transaction` may carry `counterpartAccountNumber` |
| `POST /api/accounts/{id}/deposits`, `/withdrawals` | `MovementRequest` | 201 + `Transaction` (never with a counterpart) |

```yaml
Transaction:
  properties:
    counterpartAccountNumber:
      type: string
      description: >-
        Normalised IBAN of the other account when the transaction belongs to a transfer
        (target on the source's withdrawal, source on the target's deposit). Omitted otherwise.
      example: ES7921000418450200051332
```

The field is optional and **omitted** (not `null`) when absent. Additive change: no versioning.

## Screens (web)

| Path | Uses | Shows |
| ---- | ---- | ----- |
| `GET /` | `listAccounts` | each row adds the segment label ("Minorista" / "Alto patrimonio") next to the shortened IBAN |
| `GET /accounts/{id}` | `findAccount` | transactions **newest first**; a withdrawal with a counterpart reads "Transferencia a ES79 •••• 6789", a deposit with one "Transferencia de ES79 •••• 6789" (rejected ones keep their rejected style); others unchanged |

The shortened IBAN (`WebFormat.maskedIban`) uses non-breaking spaces (U+00A0), so it never breaks across lines. The segment label lives in one place on the web (`WebFormat.segment`).

## Changes

- —
