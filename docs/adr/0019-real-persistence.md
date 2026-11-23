# 0019 · Real persistence: PostgreSQL (Neon) in production, H2 locally

- **Status:** proposed · 2026-11-23
- **Issue:** #150 · **Related:** [ADR 0006](0006-hexagonal-architecture.md) (output ports), [ADR 0008](0008-spring-security.md) (users), [ADR 0012](0012-customer-self-sign-up.md) (sign-up in memory)

## Context

Every piece of state of `Hexagonal` lived in memory: accounts with their transactions, customers, account and customer ownership, the account numbering (`adapter.out.persistence`) and the Spring Security users (`InMemoryUserDetailsManager`). The application is public on Render, so every restart or deploy wiped the customers who signed up and their accounts. A future move to an external identity provider (OAuth / Auth0) also needs customers that outlive a restart, or the link identity → customer breaks. `CLAUDE.md` allows dependencies beyond the planned ones only when an ADR justifies them.

## Decision

- **Production: PostgreSQL managed by [Neon](https://neon.com)**, free plan, region AWS `eu-central-1` (Frankfurt), next to both Render services. Credentials only as environment variables on Render (`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`), with the direct (non-pooled) connection string. A `prod` profile reads them with no defaults, so a missing one stops the start.
- **Locally and in tests: H2 in memory** in PostgreSQL mode, as before: `./mvnw verify` and `./mvnw spring-boot:run` need no network. Each Spring test context gets its own database.
- **Versioned schema with Flyway** (`src/main/resources/db/migration`) instead of `spring.jpa.hibernate.ddl-auto=update`; Hibernate only validates it. Unique constraints on account number, customer document and username.
- **JPA output adapters** in `adapter.out.persistence` implement the existing ports. JPA entities are separate from the domain model; aggregates are rebuilt with `reconstitute(...)` factories that check structural invariants and record no events. Only `adapter.out.persistence` may call them (ArchUnit).
- **In-memory adapters become test doubles (option C, chosen by the user):** they move from `src/main` to `src/test`, keep feeding the fast tests of `application` through `PersistenceTestAdapters` and pass the same port contracts as the JPA adapters. The application always runs on JPA.
- **Users in the database (chosen by the user):** Spring Security's `JdbcUserDetailsManager` over its standard `users` / `authorities` tables. The configured users are created at startup only when missing.
- **Unit of work:** a transaction (`TransactionTemplate`) inside the fair global lock of #78, so a failed use case is rolled back. The lock stays (one instance on Render); `@Version` on the account guards against lost updates.
- **Dependencies:** `org.postgresql:postgresql` (runtime), `spring-boot-starter-flyway` and `flyway-database-postgresql`. `spring-boot-starter-data-jpa` and H2 were already there.

## Alternatives

- **Supabase:** free Postgres too, but it pauses a project after a week without activity and it must be restored by hand: a practice demo would be paused most of the time.
- **Render Postgres:** same platform, but the free database expires after 30 days and is deleted 14 days later, with no backups.
- **Testcontainers with a real PostgreSQL in the tests:** more faithful than H2, but one more dependency and Docker in the CI. It stays as a later step if H2 and PostgreSQL diverge.
- **Keep both adapters in production, chosen by configuration (option B):** shows the swap with the application running, but two implementations behind a switch confuse, and a wrong switch on Render would silently lose data again.
- **Delete the in-memory adapters (option A):** less code, but the tests of `application` would need Spring and a database, or mocks that do not remember what they saved.
- **Row locks (`PESSIMISTIC_WRITE`) instead of the global lock:** scales to several instances, but crossed transfers (A→B and B→A) can deadlock. Not needed with one instance; confirmed by the user.
- **`ddl-auto=update`:** no migration files, but the schema is not versioned and Hibernate may change a production database on its own.

## Consequences

- Data survives restarts on Render once Neon is configured; until then the deploy keeps H2 in memory, as before.
- The demo data loader skips loading when accounts already exist, so `demo,prod` on Render loads them only once.
- H2 is not PostgreSQL: a migration could pass locally and fail on Neon. The first deploy is the real check.
- Neon suspends its compute after 5 minutes idle; the first request after a pause pays that start on top of Render's.
- `LoginAttempts` and the sign-up limit counters stay in memory (ADR 0017, 0018).
