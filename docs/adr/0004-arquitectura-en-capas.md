# 0004 · Layered architecture for phase 1

- **Status:** accepted · 2026-10-23

## Context

Phase 1 must turn the domain model of Lab 22 (only `domain.model`) into a minimal web application, and stand as the first of two comparable "snapshots": layered monolith (`Monolithic`) versus hexagonal (`Hexagonal`, ADR 0002).

The DDD course presents the layered architecture (**chapter 14**) as the most common one: presentation, application, domain and persistence, with dependencies in one direction only, top-down, each layer ideally reaching only the one right below it. It also warns about two anti-patterns:

- **Layer cake (chapter 17):** layers that only forward the call to the one below (for example, `findById` repeated in every layer) without adding anything.
- **Anemic translation (chapter 18):** each layer defines its own almost identical type for the same concept and forces a translation at every boundary.

And it proposes, with care, **bypassing layers (chapter 18.1)**: deliberately letting one layer skip another, guarded by ArchUnit.

## Decision

- Four layers: `presentation` (`web` and `rest`) → `application` → `domain` (`model`, `service`) and `persistence`. Details in [fase-1-capas.md](../arquitectura/fase-1-capas.md).
- **The domain is at the bottom and depends on nobody.** `persistence` knows the domain (it stores aggregates), and `application` uses both. It is a deliberate *bypass* (ch. 18.1): the application loads and saves directly through persistence, instead of the domain calling it.
- **Concrete classes, no port interfaces.** The application uses `InMemoryCheckingAccountRepository` directly. This is the trait that sets this snapshot apart from the hexagonal one, where the domain will declare the port (`CheckingAccountRepository`) and the dependency will be inverted.
- **Against anemic translation:** the views and commands of `application` (Immutables) are the only intermediate type. Presentation defines no other model of the same concept except the REST DTOs when the JSON format requires it, and all of them use the names of the [glossary](../dominio/glosario.md).
- **Against layer cake:** no layer or method is created only to forward; simple queries are not duplicated in every layer for symmetry.
- From the domain, `presentation` only knows the exceptions (`DomainException` and subclasses), to translate them to HTTP or screen messages; never aggregates, entities or value objects (rule D4).
- The dependency rules D1-D5 of `fase-1-capas.md` are checked with ArchUnit (`LayeredArchitectureTests`, issue #12).

## Alternatives

- **Strict layers** (`application → domain → persistence`, no skipping): would force the domain to know persistence, breaking its isolation (rule D1 of `LayeredArchitectureTests`), or to add domain methods that only forward to the repository (layer cake). Discarded.
- **Hexagonal from the start:** it is the goal of phase 2; starting with it would remove the comparison between the two snapshots. Discarded for this phase.
- **Repository interfaces already in phase 1:** would bring phase 1 closer to hexagonal and blur the difference we want to show. They arrive in phase 2 (#15).

## Consequences

- The application depends on the concrete persistence implementation: replacing it (with JPA, for example) means touching `application`. That is the cost phase 2 removes with ports.
- Application tests use the real in-memory repository, not mocks.
- The `application → persistence` bypass is a conscious exception: ArchUnit allows it explicitly and forbids the others (for example, `presentation → persistence`).
- The risk of anemic translation is watched in review (`revisor-ddd`) and with the glossary as the source of names.
