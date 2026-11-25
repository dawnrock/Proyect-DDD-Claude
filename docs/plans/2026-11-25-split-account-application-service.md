# Plan: Split `CheckingAccountService` and `CheckingAccountCommands` by use case

- **Issue:** [#174](https://github.com/dawnrock/Proyect-DDD-Claude/issues/174)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-25
- **Status:** in progress
- **Total estimate:** ~120 min (13 tasks)

## Goal

`CheckingAccountService` (400 lines) implements both input ports and holds every account use case: admin opening, sign-up, another account, IBAN regeneration, deposit, withdrawal, transfer, segment review and the queries. PMD flags it as `GodClass` (WMC 53) and `CouplingBetweenObjects` (35 types), and its main test class as `CouplingBetweenObjects` (PMD baseline, #168, ADR 0020). Following the Clean Code course (ch. 19 and 22: small classes, one reason to change), the input port `CheckingAccountCommands` and the service are split by use case, as the user decided on 2026-11-25 ("services and ports").

- **Improves:** each input port and each service has one responsibility; each input adapter depends only on the ports it uses; the shared mechanics (unit of work with event publishing, access by owner, views) live once, in small package-private collaborators.
- **Must NOT change:** any behaviour. Same rules and errors, one `UnitOfWork` per use case (and the segment review before a debit in its own unit, as today), same events published in the same order inside the unit (outbox, #165), same REST API and web. The existing tests prove it once they are pointed at the new ports.

### Out of scope

- `AccountWebController` (one controller per use case), `CheckingAccount` (`GodClass`, `reconstitute`) and the `validate()` of `OpenAccountCommandDef`/`SignUpCommandDef`: each keeps its baseline line and gets its own issue.
- Any change to the REST contract (`docs/api/openapi.yaml` keeps its version) or to the web.
- Historical documents (ADRs, older plans, `docs/ia/historial.md`) that name `CheckingAccountService` or `CheckingAccountCommands`: they describe what was decided then and are not rewritten.
- `Monolithic`, frozen.

## Context

**Target design** (all in `application`):

| Input port | Use cases | Implemented by | Used by |
| ---------- | --------- | -------------- | ------- |
| `AccountOpeningCommands` | `openAccount`, `signUp`, `openAnotherAccount`, `regenerateAccountNumber` | `AccountOpeningService` | REST `AccountController`, `AccountWebController`, `DomainModelRunner`, `CustomerRegistration` |
| `MoneyMovementCommands` | `deposit`, `withdraw`, `transfer` | `MoneyMovementService` | REST `AccountController`, `TransferController`, `AccountWebController`, `DomainModelRunner` |
| `SegmentReviewCommands` | `reviewSegments` | `AccountSegmentReviewService` (the domain already has `domain.service.SegmentReviewService`) | `SegmentReviewScheduler` |
| `CheckingAccountQueries` (unchanged) | `listAccounts`, `findAccount`, `isUsernameTaken` | `CheckingAccountQueryService` | REST `AccountController`, `AccountWebController`, `DomainModelRunner`, `CustomerRegistration` |

`CheckingAccountCommands` and `CheckingAccountService` disappear.

**Shared package-private collaborators**, extracted first while `CheckingAccountService` still exists, so every step is a small refactor with the current tests as a safety net:

| Collaborator | Takes from `CheckingAccountService` | Used by |
| ------------ | ----------------------------------- | ------- |
| `AccountViews` (static) | `toSummary`, `toTransaction`, `toDetails` (the holder's e-mail is passed in), the `TransferSummary` built in `transfer` | all four services |
| `AccountUnitOfWork` + `AccountChanges` | `inUnit`, `save` and `Events`: runs the work in the `UnitOfWork`, `AccountChanges.save(account)` saves and collects its events, and the batches are published at the end, still inside the unit | opening, movement, segment review |
| `AccountAccess` | `load`, `loadFor`, `canAccess` | movement, queries, opening (`regenerateAccountNumber`) |
| `UsernameAvailability` | `usernameTaken` (R30) | opening, queries |
| `DueSegmentReview` | `reviewSegment` (with the `Clock` and `domain.service.SegmentReviewService`) | movement (before a debit), segment review |

Package-private collaborators cannot be built in `config`, so each service keeps a **public constructor that takes the output ports** (and the factory, domain services, `Clock`, `UnitOfWork`) and builds its collaborators inside; `DomainConfiguration` builds one bean per service and adapters receive them by their port. The lock and the transaction stay in the single `UnitOfWork` bean, so the concurrency guarantees (#78, #150) do not change.

**Tests.** The service tests (`CheckingAccountService*Tests`, 1,936 lines) build the service with ten arguments in nine places. A test helper `ApplicationServicesFixture` (in `src/test/.../application`) builds the four services on the in-memory adapters (`PersistenceTestAdapters`) with optional overrides (publisher, factory, clock, income verifier), so the tests move to one class per service without repeating the wiring. The adapter tests that mock `CheckingAccountCommands` (`@MockitoBean`) mock the new port(s) instead. `CheckingAccountServiceConcurrencyTests` becomes `MoneyMovementServiceConcurrencyTests`.

**ArchUnit.** `HexagonalArchitectureTests` forbids input adapters to depend on `CheckingAccountService`. It becomes: input adapters (and nobody outside `application` and `config`) do not depend on classes of `application` that implement an input port, checked by the rule that ports are interfaces plus a rule on classes whose name ends in `Service`.

**Affected files:**

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `application` | `AccountViews`, `AccountUnitOfWork`, `AccountChanges`, `AccountAccess`, `UsernameAvailability`, `DueSegmentReview` (new) | Extracted collaborators |
| `application` | `AccountOpeningCommands`, `MoneyMovementCommands`, `SegmentReviewCommands` (new); `CheckingAccountCommands` (deleted) | Input ports |
| `application` | `AccountOpeningService`, `MoneyMovementService`, `AccountSegmentReviewService`, `CheckingAccountQueryService` (new); `CheckingAccountService` (deleted) | Services |
| `adapter.in` | `CustomerRegistration`, `cli.DomainModelRunner`, `rest.AccountController`, `rest.TransferController`, `scheduler.SegmentReviewScheduler`, `web.AccountWebController` | Depend on the new ports |
| `config` | `DomainConfiguration` | One bean per service |
| tests | `application/*`, adapter tests, `DddLabApplicationTests`, `Outbox*Tests`, `DomainConfigurationTests`, `HexagonalArchitectureTests` | New ports and services, tests split per service |
| build | `pmd-baseline.properties` | Lines of `CheckingAccountService` and `CheckingAccountServiceTests` removed |
| docs | `docs/arquitectura/fase-2-hexagonal.md`, `comparativa-capas-hexagonal.md`, `docs/contracts/account-factory.md`, `customer-sign-up.md`, `transfer-counterpart.md`, `open-query-list-accounts.md`, `deposits-withdrawals.md`, `money-transfers.md`, `login-roles.md` | Name the new ports and services |

## Tasks

### [x] T1 — Baseline (~5 min)

- **Layer:** tests
- **Files:** — (scratchpad only)
- **Depends on:** —
- **What to do:**
  1. `./mvnw verify` green; record the number of tests.
  2. `./mvnw pmd:pmd -Dpmd.baseline=` and record WMC and coupling of `CheckingAccountService` and of `CheckingAccountServiceTests`.
  3. `node scripts/graphify.mjs affected "CheckingAccountCommands"` and `"CheckingAccountService"`: confirm the file list above.
- **Acceptance criteria:**
  - [x] Test count and PMD metrics recorded; affected files confirmed. (1,035 test cases green; PMD: CheckingAccountService WMC 53, ATFD 19, TCC 10.8 %, coupling 35; CheckingAccountServiceTests coupling over 20; Graphify not installed in this container, affected files confirmed with grep)

### [ ] T2 — Test fixture for the application services (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/dawnrock/ddd/application/ApplicationServicesFixture.java`, the nine `new CheckingAccountService(...)` call sites
- **Depends on:** T1
- **What to do:**
  1. Create the fixture (Lombok `@Builder` is allowed in test builders) with the in-memory adapters by default and overrides for publisher, factory, clock and income verifier; for now it returns a `CheckingAccountService`.
  2. Replace the nine constructions in the tests with the fixture.
- **Acceptance criteria:**
  - [ ] No test builds `CheckingAccountService` directly; `./mvnw verify` green with the same number of tests.

### [ ] T3 — Extract `AccountViews` (~5 min)

- **Layer:** application
- **Files:** `AccountViews.java` (new), `CheckingAccountService.java`
- **Depends on:** T1
- **What to do:**
  1. Move `toSummary`, `toTransaction`, `toDetails` (the e-mail as a parameter) and the `TransferSummary` building to a package-private final class with static methods.
- **Acceptance criteria:**
  - [ ] `CheckingAccountService` has no mapping code; `StatusViewsTests` and the service tests pass unchanged.

### [ ] T4 — Extract `AccountUnitOfWork` and `AccountChanges` (~10 min)

- **Layer:** application
- **Files:** `AccountUnitOfWork.java`, `AccountChanges.java` (new), `AccountUnitOfWorkTests.java` (new), `CheckingAccountService.java`
- **Depends on:** T3
- **What to do:**
  1. TDD: tests for the collaborator with a recording `DomainEventPublisher` and the in-memory `UnitOfWork`: one `publishAll` per saved account, in save order, inside the unit; nothing published if the work throws.
  2. Move `inUnit`, `save` and `Events` there; the service uses it.
- **Acceptance criteria:**
  - [ ] New tests pass; the outbox tests (`OutboxDeliveryTests`, `OutboxRestartTests`) and the event tests of the service pass unchanged.

### [ ] T5 — Extract `AccountAccess`, `UsernameAvailability` and `DueSegmentReview` (~10 min)

- **Layer:** application
- **Files:** the three new classes, `CheckingAccountService.java`
- **Depends on:** T4
- **What to do:**
  1. `AccountAccess`: `load`, `loadFor`, `canAccess` (ADR 0008: someone else's account is `AccountNotFoundException`).
  2. `UsernameAvailability`: `isTaken(username)` (R30).
  3. `DueSegmentReview`: `reviewIfDue(account, changes)` returning whether the holder went back to retail.
- **Acceptance criteria:**
  - [ ] `CheckingAccountService` only orchestrates; `CheckingAccountServiceOwnershipTests` and the segment review tests pass unchanged.

### [ ] T6 — `SegmentReviewCommands` and `AccountSegmentReviewService` (~10 min)

- **Layer:** application · adapter.in · config
- **Files:** `SegmentReviewCommands.java`, `AccountSegmentReviewService.java` (new), `CheckingAccountCommands.java`, `CheckingAccountService.java`, `SegmentReviewScheduler.java`, `DomainConfiguration.java`, the fixture, `SegmentReviewSchedulerTests.java`, segment review tests moved to `AccountSegmentReviewServiceTests.java`
- **Depends on:** T2, T5
- **What to do:**
  1. New port with `reviewSegments()`; remove it from `CheckingAccountCommands`.
  2. New service on `AccountUnitOfWork` + `DueSegmentReview`; bean in `config`; the scheduler depends on the new port.
  3. Move the `reviewSegments` tests to the new test class.
- **Acceptance criteria:**
  - [ ] Scheduler and segment review tests pass against the new port; `./mvnw verify` green.

### [ ] T7 — `MoneyMovementCommands` and `MoneyMovementService` (~15 min)

- **Layer:** application · adapter.in · config
- **Files:** `MoneyMovementCommands.java`, `MoneyMovementService.java` (new), `CheckingAccountCommands.java`, `CheckingAccountService.java`, `rest.AccountController`, `rest.TransferController`, `web.AccountWebController`, `cli.DomainModelRunner`, `DomainConfiguration.java`, their tests; `MoneyMovementServiceTests.java` and `MoneyMovementServiceConcurrencyTests.java` (moved)
- **Depends on:** T6
- **What to do:**
  1. New port with `deposit`, `withdraw`, `transfer` (Javadoc moved as is); remove them from `CheckingAccountCommands`.
  2. New service: segment review before a debit in its own unit (unchanged), then the movement in another.
  3. Adapters and their `@MockitoBean`s use the new port; move the deposit, withdrawal and transfer tests and the concurrency test.
- **Acceptance criteria:**
  - [ ] Movement, transfer, concurrency, REST and web tests pass against the new port; `./mvnw verify` green.

### [ ] T8 — `AccountOpeningCommands` and `AccountOpeningService` (~15 min)

- **Layer:** application · adapter.in · config
- **Files:** `AccountOpeningCommands.java`, `AccountOpeningService.java` (new), `CheckingAccountCommands.java` (deleted), `CheckingAccountService.java`, `CustomerRegistration`, `rest.AccountController`, `web.AccountWebController`, `cli.DomainModelRunner`, `DomainConfiguration.java`, their tests; `AccountOpeningService*Tests.java` (renamed from `CheckingAccountServiceAdminOpeningTests`, `…SignUpTests`, `…AnotherAccountTests`, `…RegenerateAccountNumberTests`)
- **Depends on:** T7
- **What to do:**
  1. New port with the four opening use cases; delete `CheckingAccountCommands`.
  2. New service with the admin opening (`newCustomer`, `ownerOfExisting`), sign-up, another account, IBAN regeneration and `requireUnused` (R16).
  3. Adapters and mocks use the new port; rename and move the tests.
- **Acceptance criteria:**
  - [ ] `CheckingAccountCommands` no longer exists; opening, sign-up and registration tests pass; `./mvnw verify` green.

### [ ] T9 — `CheckingAccountQueryService`; delete `CheckingAccountService` (~10 min)

- **Layer:** application · config
- **Files:** `CheckingAccountQueryService.java` (new), `CheckingAccountService.java` (deleted), `DomainConfiguration.java`, the fixture, `DomainConfigurationTests.java`, query tests moved to `CheckingAccountQueryServiceTests.java`, ownership tests split between the services they exercise
- **Depends on:** T8
- **What to do:**
  1. The query service implements `CheckingAccountQueries` on `AccountAccess`, `UsernameAvailability` and `AccountViews`, each query inside the `UnitOfWork` (unchanged).
  2. Delete `CheckingAccountService`; `DomainConfigurationTests` checks one bean per port.
- **Acceptance criteria:**
  - [ ] No reference to `CheckingAccountService` left in `src/`; `./mvnw verify` green with the same number of test cases as T1 (plus the new ones of T4).

### [ ] T10 — ArchUnit: no adapter depends on an application service (~5 min)

- **Layer:** tests
- **Files:** `HexagonalArchitectureTests.java`
- **Depends on:** T9
- **What to do:**
  1. Replace the rule on `CheckingAccountService` with one on classes of `application` whose name ends in `Service`: only `application` and `config` may depend on them.
  2. Validate it by making a REST controller depend on `MoneyMovementService`: the rule fails; revert.
- **Acceptance criteria:**
  - [ ] Rule broken on purpose and restored; `./mvnw verify` green.

### [ ] T11 — PMD baseline (~5 min)

- **Layer:** build
- **Files:** `pmd-baseline.properties`
- **Depends on:** T10
- **What to do:**
  1. Remove the lines of `CheckingAccountService` and `CheckingAccountServiceTests`.
  2. `./mvnw verify`: no PMD warning on the new services and tests. If a test class is still too coupled, split it further rather than add it to the baseline.
- **Acceptance criteria:**
  - [ ] Baseline without those two lines; `./mvnw verify` green; `./mvnw pmd:pmd -Dpmd.baseline=` shows no `GodClass` or `CouplingBetweenObjects` in the new classes.

### [ ] T12 — Architecture and contract documents (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/arquitectura/comparativa-capas-hexagonal.md`, the contracts listed in *Context*
- **Depends on:** T11
- **What to do:**
  1. Component table of phase 2: the four ports, the four services and the shared collaborators; testing table (adapters mock the new ports); ArchUnit rule text.
  2. Contracts: replace `CheckingAccountCommands`/`CheckingAccountService` with the port that owns each use case.
  3. `node scripts/graphify.mjs update .`.
- **Acceptance criteria:**
  - [ ] `grep -rn "CheckingAccountCommands\|CheckingAccountService" docs/arquitectura docs/contracts` finds nothing except a historical note.

### [ ] T13 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `pendientes.md`, `decisiones.md`, `historial.md`
- **Depends on:** T12
- **What to do:**
  1. #174 done in the status table and next step (`AccountWebController` or OAuth / Auth0).
  2. `pendientes.md`: the PMD baseline item without `CheckingAccountService`; `decisiones.md`: input ports per use case; a row in `historial.md`.
- **Acceptance criteria:**
  - [ ] The CI status check passes.

## Global acceptance criteria

**Specific:**

- [ ] `CheckingAccountCommands` and `CheckingAccountService` no longer exist; the four ports are interfaces in `application`, each implemented by one service.
- [ ] Every input adapter depends only on the ports it uses, never on a service (ArchUnit).
- [ ] `pmd-baseline.properties` no longer lists `CheckingAccountService` nor `CheckingAccountServiceTests`, and the analysis reports nothing new.
- [ ] No behaviour change: the existing tests pass pointed at the new ports, with no assertion changed; `docs/api/openapi.yaml` unchanged.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (the API does not change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: no new business terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **T7 and T8 touch many adapter tests** at once (the port of a `@MockitoBean` changes). They are mechanical, but longer than 10 minutes; if needed, each can be split into "port + service" and "adapters + their tests", keeping a temporary delegation in `CheckingAccountCommands` in between.
- **`AccountOpeningService`** keeps the admin opening rules (`newCustomer`, `ownerOfExisting`); if PMD still finds it too complex, the admin opening can become its own collaborator (`AdminAccountOpening`) within this plan.
- **Name clash:** `domain.service.SegmentReviewService` already exists, hence `AccountSegmentReviewService` for the application service.
- **`AccountWebController`** will receive three ports instead of two until its own split (next issue).
