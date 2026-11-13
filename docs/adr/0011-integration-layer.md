# 0011 · An `integration` layer for simulated external systems in phase 1

- **Status:** accepted · 2026-11-13
- **Issue:** #99

## Context

#99 aligns `Monolithic` with `Hexagonal` (#98): the customer segment is also decided by the holder's verified annual income, which a simulated tax agency returns from a fixed demo table. On `Hexagonal` the agency is the output port `IncomeVerifier` (`domain.model`) implemented by the adapter `FakeTaxAgencyIncomeVerifier` (`adapter.out.taxagency`), and the domain (`CheckingAccountFactory`, `SegmentReviewService`) calls it.

Phase 1 cannot copy that design:

- `domain` depends on nobody (rule D1), so the domain cannot call the agency.
- The layers use concrete classes, without port interfaces (ADR 0004), so there is no `IncomeVerifier`.

The question is which layer holds the simulated agency, a class that talks to an external system that is not storage.

## Decision

- A fifth layer, **`integration`** (`com.pronoide.ddd.integration`), sibling of `persistence`: it holds the clients of external systems, today only `TaxAgencyClient` (concrete, `@Component`, same demo table as `Hexagonal`).
- Only `application` uses it, and it only depends on the JDK, the domain and Spring stereotypes. No interfaces, like `persistence`.
- The domain receives the **result** of the verification: `application` asks the agency and passes the segment to `AccountHolder.register(...)` and `incomeStillHigh` to `CheckingAccount.resolveSegmentReview(...)`. The decision (threshold in `HighNetWorthIncome`, periods, what each result means) stays in the domain; only the orchestration ("ask the agency only when a review is due") moves to `CheckingAccountService`.
- `LayeredArchitectureTests` adds the `Integration` layer to `las_capas_solo_dependen_hacia_abajo` and two rules: `la_integracion_no_tiene_interfaces` and `la_integracion_solo_conoce_el_dominio`. They were validated by breaking them (a controller using the agency, an interface in `integration`, an `integration` class using `application`).

## Alternatives

- **Inside `persistence`:** no ArchUnit change, but `persistence` would stop meaning "saving and loading aggregates" and the agency would sit next to repositories it has nothing to do with. Discarded.
- **Inside `application`:** mixes an external system with the use cases; it would be the only infrastructure class in that layer and `application` would no longer be easy to test with plain objects. Discarded.
- **A port interface in `domain.model`:** that is the hexagonal design of phase 2; bringing it to phase 1 would blur the difference between the two snapshots (ADR 0004). Discarded.

## Consequences

- Phase 1 now has four layers below `presentation`: `application`, `domain`, `persistence` and `integration`. The mapping to phase 2 is direct: `integration.TaxAgencyClient` ↔ `adapter.out.taxagency.FakeTaxAgencyIncomeVerifier`.
- The review logic is split differently in each branch: `SegmentReviewService` (domain service + port) on `Hexagonal`, a private method of `CheckingAccountService` on `Monolithic`. The behaviour is the same.
- A future external system of phase 1 goes to `integration` with the same rules.
