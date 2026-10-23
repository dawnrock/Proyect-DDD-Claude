# Contract: <feature>

- **Issue:** [#N](<URL>) · **Plan:** `docs/plans/YYYY-MM-DD-<slug>.md`
- **Status:** draft | agreed | frozen at `<hash>`
- **Agreed by:** backend-dev, web-dev (rounds: N)

## Application service

Phase 1: concrete class in `application`. Phase 2: input ports `*Commands` / `*Queries`.

```java
public class <Name>Service {
    <View> <operation>(<Command> command);   // what it does, in one sentence
    List<<View>> <query>();
}
```

## Commands (input)

| Command (`*Def`) | Field | Type | Required | Rules |
| ---------------- | ----- | ---- | -------- | ----- |
| `<Command>Def` | `holderName` | `String` | yes | not blank |

## Views (output)

| View (`*Def`) | Field | Type | Notes |
| ------------- | ----- | ---- | ----- |
| `<View>Def` | `accountNumber` | `String` | normalized IBAN |

Views never expose domain aggregates or entities.

## Errors

| Exception | When | Web (what the user sees) | REST (status + `ProblemDetail.title`) |
| --------- | ---- | ------------------------ | ------------------------------------- |
| `InsufficientInitialDepositException` | deposit < minimum | back to the form with the message | 422 |
| `AccountNotFoundException` | unknown id | custom 404 page | 404 |

## REST (fragment of `docs/api/openapi.yaml`)

| Method and path | Request | Response |
| --------------- | ------- | -------- |
| `POST /api/accounts` | `OpenAccountRequest` | 201 + `AccountSummary` |

## Screens (web)

| Path | Uses | Shows |
| ---- | ---- | ----- |
| `GET /` | `<query>()` | accounts table |

## Changes

<!-- Each renegotiation: date, who asked, what changed and why. -->

- —
