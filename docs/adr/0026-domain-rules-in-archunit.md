# 0026 · Domain rules only in ArchUnit, and default Lombok accessors

- **Status:** accepted · 2026-11-28
- **Amends:** [ADR 0003](0003-lombok-immutables.md) (fluent accessors, `check-domain.mjs`) and [ADR 0009](0009-domain-without-lombok.md) (`check-domain.mjs`)

## Context

A review of the project's technologies found two leftovers of decisions that later ADRs changed:

- **Fluent accessors.** `lombok.config` sets `lombok.accessors.fluent = true` so that Lombok generates `name()` instead of `getName()`, like the domain's hand-written accessors. Since ADR 0009 the domain does not use Lombok at all. The only classes with Lombok getters are the web forms, which Spring MVC binds as JavaBeans, so each of them had to undo the setting with `@Accessors(fluent = false)`. The global setting no longer had any class that wanted it.
- **Three checks of the same domain rules.** The domain rules were checked by ArchUnit (`HexagonalArchitectureTests`: D1 "the domain only depends on the JDK" and "no public setters"; `DomainIsolationTests`), by `check-domain.mjs` (a Node script inside the `ddd-building-block` skill that CI also runs) and by the `revisor-ddd` agent. The script repeated D1 (Spring, `jakarta`, `org.immutables`) and the setter rule, and added one thing: rejecting Lombok in the domain.

ArchUnit already rejects Lombok in the domain. `lombok.addLombokGeneratedAnnotation = true` puts `@lombok.Generated` (class retention) on every generated member, and D1 fails on it; that is how ADR 0009 found the problem. `@lombok.NonNull` also has class retention. Only annotations that generate no member and leave no trace in the bytecode escape it (`@SneakyThrows`, `@Cleanup`, `@Synchronized`, `val`/`var`); none of them changes the domain's state or its dependencies.

## Decision

1. **No fluent accessors.** `lombok.config` keeps Lombok's default (`getX()`/`setX()`), and the web forms drop `@Accessors(fluent = false)`. The domain keeps its hand-written record-style accessors (`id()`), as before.
2. **ArchUnit is the only automatic check of the domain rules.** `check-domain.mjs` and its CI step are removed. The rules stay in `HexagonalArchitectureTests` (D1 and no public setters) and `DomainIsolationTests`, which run in `./mvnw verify`.
3. **`revisor-ddd` only reviews what needs judgement:** invariants, anaemic model, architecture beyond the ArchUnit rules, ubiquitous language, tests, suppressions in the static analysis without a reason, and the project status. It no longer looks for Lombok or Immutables in the domain, because the build already fails on them.

## Alternatives

- **Keep `check-domain.mjs` for its clearer messages:** a second tool for the same rules, in another language, inside a skill folder; ArchUnit's message already names the class, the member and `lombok.Generated`.
- **A Checkstyle `IllegalImport` rule for `lombok` in the domain:** it would also catch the source-only annotations, but it needs a scoped suppression file for one package to cover a gap that does not affect the domain's design.

## Consequences

- One place for the domain rules, in the Maven build; CI has one Node step fewer.
- The source-only Lombok annotations listed above are not detected in the domain; `revisor-ddd` can still point them out when it reads the diff.
- `LombokSetupTests` now checks that Lombok generates JavaBean getters.
