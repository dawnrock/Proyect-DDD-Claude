# Plan: Domain events: publication and consumers

- **Issue:** [#17](https://github.com/dawnrock/Proyect-DDD-Claude/issues/17)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-03
- **Status:** in progress
- **Total estimate:** ~95 min (10 tasks)

## Goal

Model what happens to an account as domain events and publish them without coupling the domain to Spring (Lab 23). The aggregate records `CheckingAccountOpened`, `MoneyDeposited`, `MoneyWithdrawn` and `WithdrawalRejected` when its state changes; the application service publishes them through the output port `DomainEventPublisher` **after saving**; the adapter `adapter.out.event.SpringDomainEventPublisher` hands them to Spring, and two consumers in `application.listener` react: an audit log and a (simulated) customer notification, both with `@Slf4j`.

Observable behaviour of REST and web does not change; the only new effect is the log lines of the consumers.

### Out of scope

- Persisting the events or an outbox; events live in memory until the use case publishes them.
- Asynchronous or transactional listeners (`@Async`, `@TransactionalEventListener`): there are no transactions in the in-memory persistence.
- A specific transfer event (`MoneyTransferred`): a transfer is a withdrawal plus a deposit (glossary), so it produces their events.
- Building the publisher in `config` (#20) and moving the rules to `HexagonalArchitectureTests` (#22).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `DomainEvent.java` | New contract (interface): `accountId()`, `occurredOn()` |
| `domain.model` | `CheckingAccountOpened.java`, `MoneyDeposited.java`, `MoneyWithdrawn.java`, `WithdrawalRejected.java` | New events (`record`) |
| `domain.model` | `CheckingAccount.java` | Records its events; `pullDomainEvents()` returns and clears them |
| `domain.model` | `DomainEventPublisher.java` | New output port |
| `application` | `CheckingAccountService.java` | Publishes the events of every account it saves, after `save` |
| `application.listener` | `AuditLogListener.java`, `CustomerNotificationListener.java` | Consumers (`@EventListener`, `@Slf4j`) |
| `adapter.out.event` | `SpringDomainEventPublisher.java` | Output adapter over `ApplicationEventPublisher` (package-private) |
| tests | `CheckingAccountTests`, `MoneyTransferServiceTests`, `CheckingAccountService*Tests`, `DemoDataLoaderTests`, `DddLabApplicationTests`, `LayeredArchitectureTests` | Events per operation, publication after saving, integration, architecture |
| docs | `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/*` | New terms, class-role table, project status |

Building blocks: **domain event** (immutable `record`, past tense, aggregate id, minimal data and `occurredOn`, see `reglas-ddd.md`), the **aggregate** that records them, an **output port** declared in `domain.model` and its **output adapter**, and **event consumers** in the application.

Event data (minimal, all domain types, `occurredOn` taken from the transaction so event and movement share the instant):

| Event | Fields |
| ----- | ------ |
| `CheckingAccountOpened` | `accountId`, `accountNumber`, `holderName`, `initialDeposit`, `occurredOn` |
| `MoneyDeposited` | `accountId`, `transactionId`, `amount`, `occurredOn` |
| `MoneyWithdrawn` | `accountId`, `transactionId`, `amount`, `occurredOn` |
| `WithdrawalRejected` | `accountId`, `transactionId`, `amount`, `occurredOn` |

Technical notes:

- Opening an account records `CheckingAccountOpened` **and** the `MoneyDeposited` of the initial deposit, in that order, because the initial deposit is a real movement (R2).
- `pullDomainEvents()` returns an unmodifiable copy and empties the list, so publishing twice never repeats an event. The events are not part of `equals`/`hashCode` (identity by id).
- Publishing after `save` keeps the rule "the application service loads, invokes the domain, saves and publishes". A listener that throws would surface in the use case after the account is saved; the listeners only log, so it is accepted and noted.
- `CheckingAccountService` gets a third constructor argument; tests pass a Mockito mock of the port (allowed: it is a port).
- `LayeredArchitectureTests.toda_clase_pertenece_a_una_capa` and the "nobody knows the output adapters" rule only know `adapter.out.persistence`; T8 widens them to `adapter.out..` so `adapter.out.event` is covered.
- The integration test goes into `DddLabApplicationTests` (the only full-context test, `src/test/CLAUDE.md`) and checks the audit log line with `OutputCaptureExtension`, so the listener stays stateless.

## Tasks

### [x] T1 — `DomainEvent` and `MoneyDeposited` (~10 min)

- **Layer:** domain
- **Files:** `DomainEvent.java`, `MoneyDeposited.java`, `CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** —
- **What to do:**
  1. Tests first: a deposit records one `MoneyDeposited` with the account id, the transaction id, the amount and the transaction's instant; `pullDomainEvents()` empties the list (a second call returns nothing).
  2. Interface `DomainEvent` (`accountId()`, `occurredOn()`), record `MoneyDeposited` (null checks in the compact constructor), private event list and `pullDomainEvents()` in the aggregate.
- **Acceptance criteria:**
  - [x] The new tests pass; `check-domain.mjs` and `DomainIsolationTests` stay green.

### [x] T2 — `MoneyWithdrawn` and `WithdrawalRejected` (~10 min)

- **Layer:** domain
- **Files:** `MoneyWithdrawn.java`, `WithdrawalRejected.java`, `CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** T1
- **What to do:**
  1. Tests first: an approved withdrawal records `MoneyWithdrawn`; a rejected one records `WithdrawalRejected` and no `MoneyWithdrawn`.
  2. Both records and the recording in `tryWithdraw`.
- **Acceptance criteria:**
  - [x] Each outcome of a withdrawal records exactly its event.

### [ ] T3 — `CheckingAccountOpened` (~5 min)

- **Layer:** domain
- **Files:** `CheckingAccountOpened.java`, `CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** T1
- **What to do:**
  1. Test first: opening records `CheckingAccountOpened` followed by the `MoneyDeposited` of the initial deposit; a failed opening (R1) records nothing because no account exists.
  2. Record and recording in `open`.
- **Acceptance criteria:**
  - [ ] `pullDomainEvents()` after `open` returns `[CheckingAccountOpened, MoneyDeposited]`.

### [ ] T4 — Events of a transfer (~5 min)

- **Layer:** domain (tests)
- **Files:** `MoneyTransferServiceTests.java`
- **Depends on:** T2
- **What to do:**
  1. Characterization of the composition: an approved transfer leaves `MoneyWithdrawn` in the source and `MoneyDeposited` in the target; a rejected one leaves `WithdrawalRejected` in the source and nothing in the target. No production change expected; prove the tests can fail by temporarily breaking `transfer`.
- **Acceptance criteria:**
  - [ ] Both tests pass and were seen failing when `transfer` was sabotaged.

### [ ] T5 — `DomainEventPublisher` port; the service publishes after saving (~10 min)

- **Layer:** domain · application
- **Files:** `DomainEventPublisher.java`, `CheckingAccountService.java`, `CheckingAccountServiceTests.java`, `CheckingAccountServiceOwnershipTests.java`, `DemoDataLoaderTests.java`
- **Depends on:** T3
- **What to do:**
  1. Port in `domain.model`: `void publish(DomainEvent event)` and a `default void publishAll(List<DomainEvent>)`.
  2. Tests first (publisher mocked): open, deposit and withdraw publish the account's events **after** `save` (`InOrder`); a rejected withdrawal publishes `WithdrawalRejected`; a use case that fails before saving publishes nothing.
  3. Service: `publisher.publishAll(account.pullDomainEvents())` after each `save`. Existing test constructions get a mock publisher.
- **Acceptance criteria:**
  - [ ] Publication happens after saving, verified with `InOrder`.
  - [ ] `./mvnw verify` green.

### [ ] T6 — Publishing a transfer (~5 min)

- **Layer:** application
- **Files:** `CheckingAccountService.java`, `CheckingAccountServiceTests.java`
- **Depends on:** T5
- **What to do:**
  1. Test first: an approved transfer publishes `MoneyWithdrawn` (source) and `MoneyDeposited` (target) after both saves; a rejected one publishes only `WithdrawalRejected`.
  2. Service: publish the events of both accounts after saving them.
- **Acceptance criteria:**
  - [ ] Both transfer outcomes publish exactly their events.

### [ ] T7 — Consumers in `application.listener` (~10 min)

- **Layer:** application
- **Files:** `application/listener/AuditLogListener.java`, `application/listener/CustomerNotificationListener.java`, their tests
- **Depends on:** T1-T3
- **What to do:**
  1. Tests first (plain unit tests with `OutputCaptureExtension`): the audit listener logs every `DomainEvent` with its type, account and instant; the notification listener logs a simulated message to the customer for deposits, withdrawals and rejected withdrawals (not for the opening).
  2. Both listeners: `@Component`, `@Slf4j`, `@EventListener` methods; Javadoc says the notification is simulated.
- **Acceptance criteria:**
  - [ ] Both listener tests pass.

### [ ] T8 — `SpringDomainEventPublisher` and architecture rules (~10 min)

- **Layer:** adapter.out · tests
- **Files:** `adapter/out/event/SpringDomainEventPublisher.java`, its test, `LayeredArchitectureTests.java`
- **Depends on:** T5
- **What to do:**
  1. Test first: the adapter forwards each event to `ApplicationEventPublisher` (mocked).
  2. Package-private `@Component` that implements `DomainEventPublisher`.
  3. `LayeredArchitectureTests`: the output-adapter layer becomes `..adapter.out..`; the rule "nobody knows the output adapters" covers `adapter.out..`; `toda_clase_pertenece_a_una_capa` accepts `adapter.out..`. Validate by breaking it (reference the adapter from `application`), then revert.
- **Acceptance criteria:**
  - [ ] Adapter test passes; the widened rule fails when broken and passes after reverting.

### [ ] T9 — Integration: a deposit reaches the audit consumer (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java`
- **Depends on:** T6, T7, T8
- **What to do:**
  1. With the full context: open an account and deposit through `CheckingAccountCommands` as admin, and check (`OutputCaptureExtension`) that the audit log line for the `MoneyDeposited` of that account appears.
- **Acceptance criteria:**
  - [ ] The integration test passes, and fails if the publisher adapter is removed from the context (sabotage, reverted).

### [ ] T10 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T9
- **What to do:**
  1. Glossary: "Evento de dominio" and the four events, the publisher port and the consumers (the glossary stays in Spanish until its translation issue).
  2. `fase-2-hexagonal.md`: "State after #17", rows for the new classes; remove them from "classes that arrive later".
  3. Project status: #17 done, next step `/plan-feature 18`; decisions (events published after saving, opening records two events, no transfer event); pending items (listener failure surfaces in the use case).
- **Acceptance criteria:**
  - [ ] New terms in the glossary; the CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] Aggregate tests: each operation records the expected event.
- [ ] Integration test: a deposit reaches the audit consumer.
- [ ] The domain still does not depend on Spring (`DomainIsolationTests`), and events are plain `record`s.
- [ ] Events are published after saving, through the `DomainEventPublisher` port; nobody outside `adapter.out` and `config` knows `SpringDomainEventPublisher` (ArchUnit).

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no route, body or status code changes).
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

Decided with the user (2026-11-03):

- **Opening records two events** (`CheckingAccountOpened` + `MoneyDeposited` of the initial deposit), so every deposit, the first one included, has its event.
- **No transfer event:** consumers see a withdrawal and a deposit on two accounts, consistent with the glossary.
- **Synchronous listeners:** a failing listener surfaces in the use case after the account is saved. Accepted while they only log; noted in `pendientes.md` for when an asynchronous listener is needed.

Other notes:

- Spring's `@EventListener` in `application.listener` is allowed (the application already uses `@Service`; the domain stays free of Spring).
