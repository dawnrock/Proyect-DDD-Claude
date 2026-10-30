# 0006 · Hexagonal architecture for phase 2

- **Status:** accepted · 2026-10-30
- **Issue:** #14 (the rest of phase 2, #15-#23, builds on it)

## Context

Phase 1 ended as a layered monolith (ADR 0004, tag `v1-monolito`): `presentation` → `application` → `domain` and `persistence`, with concrete classes and no ports. It works, but the layers say nothing about which parts are the core and which are technology, and `application` depends directly on the in-memory repositories.

Phase 2 (Lab 27.4) rebuilds the same functionality as ports and adapters on the `Hexagonal` branch, so the two snapshots can be compared (#23).

## Decision

Target packages (also in `CLAUDE.md` and [`fase-2-hexagonal.md`](../arquitectura/fase-2-hexagonal.md)):

| Package | Role |
| ------- | ---- |
| `domain.model` | Model, business rules and **output ports** (what the core needs: repository, account number sequence, income verifier, event publisher) |
| `domain.service` | Domain services (`MoneyTransferService`) |
| `application` | Application services and **input ports** (`CheckingAccountCommands`, `CheckingAccountQueries`); `application.listener` for domain event consumers |
| `adapter.in.{rest,web,cli}` | Input adapters: they **use** the input ports |
| `adapter.out.{persistence,event,taxagency}` | Output adapters: they **implement** the output ports; all of them simulated |
| `config` | Builds the core and hands it its adapters, so the core carries no Spring annotations |

Rules: dependencies always point into the hexagon; adapters do not know each other; the domain depends on the JDK only and keeps its state private. `HexagonalArchitectureTests` (#22) will check them.

### Transitional decisions of #14

#14 only moves packages; behaviour does not change. Until the following issues land:

- **`application` still uses `adapter.out.persistence`** directly (dependency from the hexagon to an adapter). #15 removes it with the `CheckingAccountRepository` port and a port for account ownership.
- **`LayeredArchitectureTests` only has its package patterns adjusted** (`presentation` → `adapter.in`, `persistence` → `adapter.out.persistence`). #22 replaces it with `HexagonalArchitectureTests`.
- **Shared security support** (`SecuritySupport`, `CurrentCaller`, `SecurityUsersProperties`) goes to the `adapter.in` root: both input adapters use it without knowing each other, as `presentation` did in phase 1. It is technology of the input side, not wiring of the core, so it does not go to `config`.

## Alternatives

- **Keep the layer names and add interfaces.** Ports would exist, but the packages would not show what is core and what is technology, which is the point of the exercise.
- **Shared security support in `config`.** `config` arrives in #20 with a different job (building the core); mixing in authentication helpers would blur it.

## Consequences

- Every moved class keeps its history (`git mv`; only `package` and `import` lines change).
- Phase 1 documents (`fase-1-capas.md`, ADR 0004, contracts) keep the old package names: they describe `v1-monolito`.
- Until #15 and #22, a reviewer will see a known violation (`application` → `adapter.out.persistence`); this ADR is the reference for it.
