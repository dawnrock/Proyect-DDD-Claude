# 0009 · No Lombok in the domain

- **Status:** accepted · 2026-10-26
- **Supersedes:** the domain part of [ADR 0003](0003-lombok-immutables.md) (`@Getter` and `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` allowed in `domain..`). The rest of ADR 0003 still applies.

## Context

ADR 0003 allowed two Lombok annotations in the domain, `@Getter` and `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`, on the grounds that Lombok annotations have `SOURCE` retention and leave no dependency in the bytecode.

That premise is false in this project. `lombok.config` sets `lombok.addLombokGeneratedAnnotation = true`, so every generated method is annotated with `@lombok.Generated`, which **does** stay in the bytecode. A probe class in `domain.model` with a single `@Getter` breaks `LayeredArchitectureTests.el_dominio_solo_depende_del_jdk` (D1):

```
Method <...domain.model.LombokProbe.name()> is annotated with <lombok.Generated>
```

So the rule allowed something the build rejects. Nobody noticed because the domain has never used Lombok: accessors, `equals` and `hashCode` are hand-written.

## Decision

- **No Lombok in `domain..`**: no `lombok` import and no `@lombok.*` annotation, in any form.
- Entities and aggregates keep hand-written record-style accessors (`id()`, `currentBalance()`) and `equals`/`hashCode` based on the id; value objects stay `record`s.
- Outside the domain nothing changes: `@RequiredArgsConstructor`, `@Slf4j`, `@Getter`/`@Setter` on web forms and `@Builder` in test builders, as in ADR 0003.
- `check-domain.mjs` rejects any `lombok` import or `@lombok.` annotation in the domain sources. ArchUnit also catches most cases through `@lombok.Generated` (D1), but the script gives a clear message and runs before the build in CI.

## Alternatives

- **A `lombok.config` in the domain package with `lombok.addLombokGeneratedAnnotation = false`:** `@Getter` would then leave no trace and ADR 0003 would hold. Rejected: it keeps a special configuration for code nobody writes, coverage tools could no longer skip that generated code, and a class-level `@Getter` on `CheckingAccount` would expose the internal transaction list (breaking R10) without any architecture rule noticing.
- **Keep ADR 0003 as is:** leaves a documented rule that the build contradicts.

## Consequences

- The domain rule is simpler to state and to check: "the domain only depends on the JDK", with no exceptions.
- The domain has slightly more hand-written code (accessors, `equals`, `hashCode`), which is also what the course teaches.
- The `ddd-building-block` skill, its entity template and reference, the `revisor-ddd` agent and `CLAUDE.md` are aligned with this ADR in the same change.
