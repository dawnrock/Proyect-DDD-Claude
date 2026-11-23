# Plan: Real persistence with PostgreSQL (Neon) on Render and H2 locally

- **Issue:** [#150](https://github.com/dawnrock/Proyect-DDD-Claude/issues/150)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-23
- **Status:** in progress
- **Total estimate:** ~170 min (19 tasks)

## Goal

Every piece of state of `Hexagonal` (accounts with their transactions, customers, account and customer ownership, the account numbering and the Spring Security users) lives in memory and is lost on every restart or deploy on Render. This plan adds JPA output adapters over a versioned schema: PostgreSQL managed by Neon (free plan, AWS `eu-central-1`, next to the Render services in Frankfurt) in production, and H2 in memory locally and in the tests, so `./mvnw verify` and `./mvnw spring-boot:run` keep working without network. Following the user's decisions of 2026-11-23: **users and customers go to the database**, and the in-memory adapters **leave `src/main` and stay only as test doubles** (option C), checked by the same port contracts as the JPA adapters.

### Out of scope

- Counters of `LoginAttempts` and of the sign-up limit (ADR 0017, 0018): they stay in memory.
- Segment review dates outside the account (#98) and the event outbox (#17): a separate issue right after this one.
- OAuth / Auth0 (it depends on this issue).
- `Monolithic` (frozen at `v1-monolito`).
- No REST or web change: the API contract does not change.

## Context

The output ports already exist and each one has an abstract contract (`*Contract`) that any implementation inherits (#15). The in-memory adapters are package-private in `adapter.out.persistence`, and the tests of `application` reach them through `PersistenceTestAdapters`, typed as ports. `spring-boot-starter-data-jpa`, H2 (runtime) and `spring-boot-starter-data-jpa-test` are already in the `pom.xml`. Today `spring.jpa.hibernate.ddl-auto=update` and no entity exists.

**Reconstitution (DDD):** an adapter that reads from the database must rebuild the aggregates without going through `open`/`register`: those check opening rules (minimum initial deposit) and record events again. Each aggregate gets a public static `reconstitute(...)` that checks the structural invariants (required fields, the balance matches the approved transactions) and records no events. Only `adapter.out.persistence` may call it: a new ArchUnit rule guards it.

**Users:** they are not part of the hexagon (ADR 0008: `application` does not know Spring Security). `SecuritySupport` swaps `InMemoryUserDetailsManager` for Spring Security's `JdbcUserDetailsManager` over the standard `users`/`authorities` tables. The configured users (`app.security.users`, `APP_ADMIN_*`) are created at startup only when missing, so a password changed in the database is not overwritten and a restart keeps the users who signed up.

**Unit of work:** the JPA adapter runs each unit inside a transaction (`TransactionTemplate`), so a failed use case is now rolled back. Concurrency: it keeps the fair global lock of `InMemoryUnitOfWork` around the transaction (one instance on Render), plus `@Version` on the account as a safety net. Locking only the rows involved stays as a later improvement (open question).

**Demo data:** Render runs the `demo` profile. With a persistent database, `DomainModelRunner` would open the demo accounts again on every start, so it skips loading when accounts already exist. Production runs `SPRING_PROFILES_ACTIVE=demo,prod`: demo data the first time and PostgreSQL through environment variables.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build / docs | `pom.xml`, `docs/adr/0019-real-persistence.md` | PostgreSQL driver (runtime), Flyway (Spring Boot 4 starter + `flyway-database-postgresql`); ADR |
| `domain.model` | `Transaction`, `AccountHolder`, `CheckingAccount`, `Customer` | `reconstitute(...)` factories with their tests |
| tests (ArchUnit) | `HexagonalArchitectureTests` | only `adapter.out.persistence` calls `reconstitute` |
| config / resources | `db/migration/V1__initial_schema.sql`, `application.properties`, `application-prod.properties`, test properties | schema, H2 in PostgreSQL mode, `ddl-auto=validate`, one database per test context, `prod` profile |
| `adapter.out.persistence` | `Jpa*` adapters, entities, Spring Data repositories, mappers, `MonABankIbans`, `DatabaseAccountNumberSequence`, `TransactionalUnitOfWork` | JPA implementations of every persistence port |
| tests (`adapter.out.persistence`) | `InMemory*` moved from `src/main`, `Jpa*Tests` | in-memory adapters as test doubles without Spring annotations; JPA adapters checked with the contracts in `@DataJpaTest` |
| `adapter.in` | `SecuritySupport` | `JdbcUserDetailsManager` and seeding of the configured users |
| `adapter.in.cli` | `DomainModelRunner` | idempotent demo data |
| docs | `fase-2-hexagonal.md`, `docs/deploy/render.md`, `docs/ia/*` | architecture, Neon guide, project status |

## Tasks

### [x] T1 — ADR and dependencies (~10 min)

- **Layer:** build · docs
- **Files:** `docs/adr/0019-real-persistence.md`, `docs/adr/README.md`, `pom.xml`
- **Depends on:** —
- **What to do:**
  1. Write ADR 0019 (status `proposed` until the user accepts it): Neon in production (rejected: Supabase pauses after a week, Render Postgres expires after 30 days), H2 in PostgreSQL mode locally and in tests (rejected: Testcontainers, needs Docker in the CI), Flyway migrations instead of `ddl-auto`, option C for the in-memory adapters (rejected: both in production behind a switch; deleting them), users with `JdbcUserDetailsManager`, unit of work as lock + transaction.
  2. Add the PostgreSQL driver (`runtime`) and Flyway (Spring Boot 4 starter plus `flyway-database-postgresql`) to the `pom.xml`, without migrations yet (`spring.flyway.enabled=false` until T6).
- **Acceptance criteria:**
  - [x] ADR listed in `docs/adr/README.md`; `./mvnw verify` green with the new dependencies.

### [x] T2 — Reconstitute transactions and account holders (~10 min)

- **Layer:** domain
- **Files:** `Transaction.java`, `AccountHolder.java`, `TransactionTests.java` (new or existing), `AccountHolderTests.java`
- **Depends on:** —
- **What to do:**
  1. `Transaction.reconstitute(id, accountId, type, amount, occurredOn, counterpart, status)`: required fields, positive amount; a counterpart only on transfers is not checked (the type does not say it).
  2. `AccountHolder.reconstitute(id, fullName, taxId, segment)` with the R21 name rule.
- **Acceptance criteria:**
  - [x] A reconstituted transaction and holder keep every value and identity (`equals` by id).
  - [x] Null fields and a non-positive amount are rejected.

### [x] T3 — Reconstitute checking accounts (~10 min)

- **Layer:** domain
- **Files:** `CheckingAccount.java`, `CheckingAccountReconstitutionTests.java` (own class instead of `CheckingAccountTests`, already 500 lines)
- **Depends on:** T2
- **What to do:**
  1. `CheckingAccount.reconstitute(id, accountNumber, primaryHolder, currentBalance, nextSegmentReview, transactions)`: required fields, transactions of this account, balance equal to approved deposits minus approved withdrawals (otherwise `IllegalStateException`), **no domain events** recorded.
  2. The reconstituted account keeps working: a deposit after reconstitution changes the balance and records its event.
- **Acceptance criteria:**
  - [x] `pullDomainEvents()` is empty right after `reconstitute`.
  - [x] A balance that does not match the transactions is rejected.

### [x] T4 — Reconstitute customers (~5 min)

- **Layer:** domain
- **Files:** `Customer.java`, `CustomerTests.java`
- **Depends on:** —
- **What to do:**
  1. `Customer.reconstitute(id, fullName, taxId, email)` with the same field rules as `register`, keeping the stored id.
- **Acceptance criteria:**
  - [x] Same id, document, name and e-mail as stored; invalid fields rejected as in `register`.

### [x] T5 — ArchUnit: only persistence reconstitutes (~5 min)

- **Layer:** tests
- **Files:** `HexagonalArchitectureTests.java`
- **Depends on:** T3, T4
- **What to do:**
  1. Rule `solo_la_persistencia_reconstituye_agregados`: no class outside `..domain.model..` and `..adapter.out.persistence..` calls a method named `reconstitute`.
  2. Validate it by breaking it (a temporary call from `application`) and reverting.
- **Acceptance criteria:**
  - [x] The rule fails with the temporary call and passes without it (validated with a temporary `application.SabotageReconstitute` calling `Customer.reconstitute`).

### [x] T6 — Initial schema and database per environment (~10 min)

- **Layer:** config
- **Files:** `src/main/resources/db/migration/V1__initial_schema.sql`, `application.properties`, `src/test/resources/config/application.properties` (adds to the main file instead of replacing it)
- **Depends on:** T1
- **What to do:**
  1. Tables: `checking_account` (holder embedded, `account_number` unique, `version`), `account_transaction` (FK to the account, position for the order), `customer` (`tax_id` unique), `customer_username` (`username` unique), `account_owner`, Spring Security's `users`/`authorities`, and the sequence `account_number_seq`.
  2. H2 in PostgreSQL mode (`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`), Flyway on, `spring.jpa.hibernate.ddl-auto=validate`, `show-sql` off by default.
  3. Tests: one H2 database per Spring context (`jdbc:h2:mem:${random.uuid};...`), so contexts do not share data now that it outlives the beans.
- **Acceptance criteria:**
  - [x] Flyway applies V1 on start; `./mvnw verify` green and `GET /actuator/health` is `UP`.

### [x] T7 — Account entities and mapper (~10 min)

- **Layer:** adapter.out
- **Files:** `adapter/out/persistence/CheckingAccountEntity.java`, `TransactionEntity.java`, `CheckingAccountMapper.java`, `CheckingAccountMapperTests.java`
- **Depends on:** T3, T6
- **What to do:**
  1. Package-private JPA entities (plain fields: `CLAUDE.md` only allows `@Getter`/`@Setter` on web forms) and a mapper domain ↔ entity that uses `reconstitute` to read.
- **Acceptance criteria:**
  - [x] An account with deposits, a rejected withdrawal and a transfer makes the round trip domain → entity → domain without changing any value.

### [x] T8 — JPA account repository (~10 min)

- **Layer:** adapter.out
- **Files:** `JpaCheckingAccountRepository.java`, `CheckingAccountJpaRepository.java` (Spring Data), `JpaCheckingAccountRepositoryTests.java`
- **Depends on:** T7
- **What to do:**
  1. Adapter of `CheckingAccountRepository`; an account number already used by another account → `IllegalStateException` (port contract).
  2. Test that extends `CheckingAccountRepositoryContract`. **Deviation:** not `@DataJpaTest`. The contracts group their tests with `@Nested`, and Spring builds the context of a nested class from its declaring class (the contract, which knows no Spring). The JPA tests share a minimal context (`JpaTestDatabase`: H2 of the configuration, Flyway, JPA, Spring Data of this package) and run each port call in its own transaction, as the unit of work does.
  3. The JPA adapters get `@Repository` in T13, when the in-memory ones lose theirs (two beans of the same port would break the context).
  4. Contract fix: `tras_cambiar_el_numero...` compared with `containsSame`, against the contract's own rule (compare by id; a real implementation returns another instance).
- **Acceptance criteria:**
  - [x] The contract passes against H2; `findAll` sorted by account number.

### [x] T9 — JPA customer repository (~10 min)

- **Layer:** adapter.out
- **Files:** `JpaCustomerRepository.java`, `CustomerEntity.java`, its Spring Data repository, `JpaCustomerRepositoryTests.java`
- **Depends on:** T4, T6
- **What to do:**
  1. Adapter of `CustomerRepository`; a document of another customer → `DuplicateCustomerException` (checked and backed by the unique constraint, R30).
- **Acceptance criteria:**
  - [x] `CustomerRepositoryContract` passes against H2 (with `JpaTestDatabase`, see T8).

### [x] T10 — JPA ownership repositories (~10 min)

- **Layer:** adapter.out
- **Files:** `JpaAccountOwnershipRepository.java`, `JpaCustomerOwnershipRepository.java`, their entities, `Jpa*OwnershipRepositoryTests.java`
- **Depends on:** T6
- **What to do:**
  1. Adapters of `AccountOwnershipRepository` and `CustomerOwnershipRepository`; a username of another customer → `IllegalArgumentException` (R30).
- **Acceptance criteria:**
  - [x] `AccountOwnershipRepositoryContract` and `CustomerOwnershipRepositoryContract` pass against H2 (with `JpaTestDatabase`, see T8).

### [x] T11 — Account numbering on a database sequence (~10 min)

- **Layer:** adapter.out
- **Files:** `MonABankIbans.java` (IBAN calculation extracted from `InMemoryAccountNumberSequence`), `MonABankIbansTests.java`, `DatabaseAccountNumberSequence.java`, `DatabaseAccountNumberSequenceTests.java`
- **Depends on:** T6
- **What to do:**
  1. Move the CCC and IBAN calculation into a package-private helper shared by both implementations.
  2. `DatabaseAccountNumberSequence` takes the next value of `account_number_seq`, so numbers are not repeated after a restart.
- **Acceptance criteria:**
  - [x] `AccountNumberSequenceContract` passes; the first IBAN is the same as today's (`...0000000001`), and a new numbering on the same database carries on.

### [x] T12 — Transactional unit of work (~10 min)

- **Layer:** adapter.out
- **Files:** `TransactionalUnitOfWork.java`, `TransactionalUnitOfWorkTests.java`
- **Depends on:** T8
- **What to do:**
  1. Adapter of `UnitOfWork`: fair global lock + `TransactionTemplate`; an exception rolls the transaction back.
- **Acceptance criteria:**
  - [x] `UnitOfWorkContract` passes; an account saved inside a unit that then fails is not in the database (validated by removing the transaction: the rollback test fails).

### [x] T13 — In-memory adapters become test doubles (~10 min)

- **Layer:** tests
- **Files:** the six `InMemory*` from `src/main/.../adapter/out/persistence` to `src/test/.../adapter/out/persistence`, `PersistenceTestAdapters.java`
- **Depends on:** T8–T12
- **What to do:**
  1. `git mv` them to `src/test`, without `@Repository`/`@Component` (otherwise the scan of `@SpringBootTest` would find them next to the JPA ones); they use `MonABankIbans`.
  2. The application context only has the JPA adapters (they get `@Repository`/`@Component` here); `PersistenceTestAdapters` keeps returning the doubles.
- **Acceptance criteria:**
  - [x] No `InMemory*` left in `src/main`; the `InMemory*Tests` still pass their contracts; `DomainConfigurationTests` and the end-to-end tests green with JPA.

### [x] T14 — Users in the database (~10 min)

- **Layer:** adapter.in
- **Files:** `SecuritySupport.java`, new `UserStore.java` (adapter.in), `SecuritySupportTests.java`, test `TestUsers.java` and the `@Import` of 13 `@WebMvcTest`s
- **Depends on:** T6
- **What to do:**
  1. `JdbcUserDetailsManager` over the `DataSource` instead of `InMemoryUserDetailsManager`.
  2. Create the configured users (`app.security.users`, `APP_ADMIN_*`) when they do not exist; keep the format and role checks. **Deviation:** an existing configured user takes the configured password and role (the configuration rules over those users, so the admin password can still be rotated on Render); other users are not touched.
  3. **Deviation:** the store is its own `@Configuration` (`UserStore`), because the `@WebMvcTest`s import `SecuritySupport` and have no database; they now also import `TestUsers` (the configured users in memory).
- **Acceptance criteria:**
  - [x] A user created by the sign-up is still found by a new `UserDetailsManager` over the same database.
  - [x] Starting twice does not fail or duplicate the configured users; the login and token tests stay green.

### [ ] T15 — Idempotent demo data (~5 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/cli/DomainModelRunner.java`, `DomainModelRunnerTests.java`
- **Depends on:** T13
- **What to do:**
  1. If the system caller already sees accounts (`CheckingAccountQueries`), log `[demo] datos ya cargados` and open nothing.
- **Acceptance criteria:**
  - [ ] Running the runner twice leaves three demo accounts and the same balances (Ada 1500, Grace 4700, Alan 200).

### [ ] T16 — `prod` profile (~5 min)

- **Layer:** config
- **Files:** `src/main/resources/application-prod.properties`, `DemoProfileTests.java` or a new profile test
- **Depends on:** T6
- **What to do:**
  1. Datasource only from `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` (no defaults, so a missing one fails at startup), small Hikari pool (Neon free plan), H2 console off.
- **Acceptance criteria:**
  - [ ] The `prod` profile without `SPRING_DATASOURCE_URL` does not start; the default and `demo` profiles still use H2.

### [ ] T17 — Data survives a restart (~10 min)

- **Layer:** tests
- **Files:** `PersistenceRestartTests.java` (end to end)
- **Depends on:** T13, T14, T15
- **What to do:**
  1. Start a context over a named H2 database, sign up a customer, close the context, start another over the same database: the customer, their account and their login are still there, and the demo data is not loaded twice.
- **Acceptance criteria:**
  - [ ] The test passes; it is the local equivalent of the restart check on Render.

### [ ] T18 — Architecture and Render guide (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/deploy/render.md`
- **Depends on:** T13, T16
- **What to do:**
  1. Package tree and adapter table with the JPA adapters and the doubles in tests; reconstitution rule.
  2. Guide: create the Neon project in AWS `eu-central-1`, use the direct (non-pooled) connection string, and set `SPRING_PROFILES_ACTIVE=demo,prod` and the three `SPRING_DATASOURCE_*` variables as secrets on the `Hexagonal` service.
- **Acceptance criteria:**
  - [ ] Both documents describe the new persistence; no secret in the repository.

### [ ] T19 — Project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1–T18
- **What to do:**
  1. Decisions: option C, users in the database, reconstitution, unit of work with rollback.
  2. Pending items: remove the in-memory notes resolved (unique constraints of #103, lost data, no rollback of #78) and add the new ones (lock vs. row locks, H2 vs. PostgreSQL differences).
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] On Render, users, customers, accounts and transactions survive a restart of the service (checked by the user after setting up Neon).
- [ ] Locally, `./mvnw verify` and `./mvnw spring-boot:run` work without network, with H2.
- [ ] No in-memory adapter left in `src/main`; the JPA adapters and the test doubles pass the same port contracts.
- [ ] Only `adapter.out.persistence` reconstitutes aggregates (ArchUnit); `DomainIsolationTests` green.
- [ ] ADR 0019 accepted by the user.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (justified in ADR 0019).
- [ ] If the API changes: n/a (the API contract does not change; `node scripts/contract-test.mjs` still passes).
- [ ] Any new business term is in `docs/dominio/glosario.md` (none expected: reconstitution is technical).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Concurrency:** the plan keeps the global lock around the transaction (same behaviour as today, valid with one instance). Locking only the rows involved (`PESSIMISTIC_WRITE`) scales better but can deadlock on crossed transfers (A→B and B→A). **Confirmed by the user (2026-11-23).**
- **H2 is not PostgreSQL:** PostgreSQL mode covers the usual SQL, but a migration could pass on H2 and fail on Neon. The first deploy is the real check; Testcontainers stays as a later option. **Accepted by the user (2026-11-23).**
- **Neon connection:** the pooled endpoint (PgBouncer in transaction mode) does not get on with prepared statements; the guide uses the direct one and a small Hikari pool. Neon suspends its compute after 5 min idle, which adds to Render's own cold start.
- **The Render service runs `demo` today:** until Neon is configured, the deploy keeps H2 in memory (same as now). The user creates the Neon project and the secrets; Claude cannot do it.
- **Users and the login limit:** `LoginAttempts` stays in memory; a restart clears the locks (unchanged).
- **Memory (512 MB):** Hibernate already starts today (the JPA starter is in the `pom.xml`); Flyway and the entities add little, but check the memory on the first deploy.
