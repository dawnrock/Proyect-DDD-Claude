# Contract: transfers show their counterpart account

- **Issue:** [#72](https://github.com/dawnrock/Proyect-DDD-Claude/issues/72) · **Plan:** `docs/plans/2026-11-05-web-pending-items.md`
- **Status:** agreed (frozen in the next commit)
- **Agreed by:** backend-dev, web-dev (rounds: 1; both agreed with clarifications, merged by the lead)

Extends the transaction view of `deposits-withdrawals.md` (#6) and `money-transfers.md` (#7). Transfers are still recorded as a `WITHDRAWAL` on the source and a `DEPOSIT` on the target (#7 decision); each of those two transactions now also records the **counterpart account** (the other account of the transfer). The signatures of the input ports do not change: the back tasks (T1-T3) only fill the new field. No domain event changes.

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
| `AccountDetailsDef` | `transactions` | `List<TransactionSummary>` | Unchanged order, and `application` guarantees it: oldest first, in the order they were recorded (two transactions may share an instant, for example the initial deposit and a transfer in the demo). Reversing it is a web concern |
| `AccountSummaryDef` | `segment` | `String` | Unchanged (`RETAIL` / `HIGH_NET_WORTH`); listed here only as a note: the web starts showing it in the list, nothing changes on the back |

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

The field is optional and **omitted** (not `null`) when absent. Additive change: no versioning. The schema is named `Transaction` in `openapi.yaml` (the Javadoc of `TransactionResponseDef` that says `TransactionSummary` is fixed in T4).

**Implementation note (REST):** the `application` view keeps `Optional<String>`; the REST response declares `@Nullable String counterpartAccountNumber()` (JSpecify, which Immutables treats as optional) with `@JsonInclude(JsonInclude.Include.NON_NULL)`, because Jackson 3 writes an empty `Optional` as `null`. The mapping uses `orElse(null)`. T4 tests that the key **does not exist** on a deposit, not only that it is null. The value is `AccountNumber.value()`, the same normalised IBAN that `TransferSummary` shows.

## Screens (web)

| Path | Uses | Shows |
| ---- | ---- | ----- |
| `GET /` | `listAccounts` | each row adds the segment label ("Minorista" / "Alto patrimonio") next to the shortened IBAN |
| `GET /accounts/{id}` | `findAccount` | transactions **newest first**; a withdrawal with a counterpart reads "Transferencia a ES79 •••• 6789", a deposit with one "Transferencia de ES79 •••• 6789" (rejected ones keep their rejected style); others unchanged |

Web details:

- **Texts:** a withdrawal with a counterpart reads "Transferencia a <shortened IBAN>", a deposit with one "Transferencia de <shortened IBAN>"; without a counterpart they stay "Retirada" / "Ingreso".
- **Optional in Thymeleaf:** read with `orElse(null)` (`th:with="cp=${t.counterpartAccountNumber().orElse(null)}"`).
- **Order:** the controller reverses a copy of the list (`List.copyOf` + `Collections.reverse`); it never sorts by `occurredOn`, because instants can be equal.
- **Shortened IBAN:** `WebFormat.maskedIban` uses non-breaking spaces (U+00A0), so it never breaks across lines. The notices (for example after a transfer: "Transferencia de X a ES79 •••• 6789 aprobada") do not change otherwise; tests that compare them with normal spaces are adjusted on purpose (T6/T8).
- **Segment label:** one place, `WebFormat.segment`, used by the list, the `hero-sub` of the details and the notice after opening an account (today duplicated in the controller's `segmentLabel` and a ternary in `detail.html`).

## Changes

- —
