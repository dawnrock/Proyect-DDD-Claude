# Plan: Each use case runs as a unit of work

- **Issue:** [#78](https://github.com/dawnrock/Proyect-DDD-Claude/issues/78)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-05
- **Status:** in progress
- **Total estimate:** ~60 min (6 tasks)

## Goal

**Current behaviour.** A transfer saves the source and the target with two `save` calls, without a transaction or a lock (#7 review). Analysing it showed the real risk is wider: the in-memory repository returns the **same** `CheckingAccount` object to every caller (references, not copies), so two simultaneous operations on one account change its balance and its transaction list at the same time, with no protection. Money can appear or disappear, and transactions can be lost.

**Expected behaviour.** Decided by the user on 2026-11-05: an application port **`UnitOfWork`** runs each use case (load → operate → save) as one unit. The in-memory adapter implements it with a lock; a real database would implement it with a transaction. The core stays free of Spring. Domain events are published **after** the unit finishes, as they are today published after saving.

What must not change: the behaviour of every use case, R1-R21, the ports' existing methods and the tests' scenarios.

### Out of scope

- Optimistic locking with a version on the aggregate, and a repository that stores copies (discarded by the user: a large change to the adapter and the tests).
- A real database or Spring's `@Transactional` in the core.
- Asynchronous event listeners or an outbox (pending item from #17).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `application` | `UnitOfWork.java` (new) | Output port: `<T> T execute(Supplier<T> work)`. In `application`, like `AccountOwnershipRepository`, because the boundary of a use case is an application concern |
| `application` | `CheckingAccountService.java` | Every use case (commands and queries) runs inside `unitOfWork.execute(...)`; the events are pulled inside and published after it |
| `adapter.out.persistence` | `InMemoryUnitOfWork.java` (new) | Package-private adapter: one fair `ReentrantLock` for every unit (simple and enough for a practice repository) |
| `config` | `DomainConfiguration.java` | `CheckingAccountService` receives `UnitOfWork` |
| tests | `UnitOfWorkContract.java` (new), `InMemoryUnitOfWorkTests.java` (new), `PersistenceTestAdapters.java`, `CheckingAccountServiceConcurrencyTests.java` (new), the service's test constructions, `DomainConfigurationTests.java` | Port contract, concurrency test, new constructor argument |
| docs | `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/*` | New term, class → role, adapter tests, status |

`HexagonalArchitectureTests` already covers the new pieces: an output adapter may only implement ports of the hexagon (`domain.model` or `application`), nobody outside `adapter.out` knows the adapter, and the core must not depend on Spring. No rule changes.

**New term:** unit of work (*unidad de trabajo*): the boundary inside which a use case loads, changes and saves aggregates as one step; nothing it does is seen half-done by another use case.

## Tasks

### [ ] T1 — Concurrency test that reproduces the problem (~10 min)

- **Layer:** application (test)
- **Files:** `src/test/java/com/pronoide/ddd/application/CheckingAccountServiceConcurrencyTests.java`
- **Depends on:** —
- **What to do:**
  1. Real `CheckingAccountService` with the in-memory adapters, no Spring. Two accounts; many threads at once transfer small amounts A → B and B → A, and deposit into both. Afterwards: the total money equals the initial total plus the deposits, each account's balance equals the sum of its approved transactions, and every operation left exactly its transactions.
  2. Run it against the current code and see it fail (red). Races are not deterministic: repeat the scenario enough times (in the same test) that it fails reliably here; if it still passes, record that and rely on the sabotage of T4 to prove the test can fail.
- **Acceptance criteria:**
  - [ ] The test fails on the current code (or the attempt is recorded as described).

### [x] T2 — `UnitOfWork` port and its contract (~10 min)

- **Layer:** application
- **Files:** `application/UnitOfWork.java`, `src/test/java/com/pronoide/ddd/application/UnitOfWorkContract.java`
- **Depends on:** —
- **What to do:**
  1. Port: `<T> T execute(Supplier<T> work)`; Javadoc says what any implementation guarantees: the work runs once, its result is returned, its exception propagates, and two units never overlap on the same data.
  2. Abstract contract (any implementation): returns the result; propagates the exception and a later unit still runs; while one unit is running, another started from a second thread does not enter until the first ends.
- **Acceptance criteria:**
  - [x] The port compiles; the contract exists (exercised in T3).

### [x] T3 — In-memory adapter (~10 min)

- **Layer:** adapter.out
- **Files:** `adapter/out/persistence/InMemoryUnitOfWork.java`, `InMemoryUnitOfWorkTests.java`, `PersistenceTestAdapters.java`
- **Depends on:** T2
- **What to do:**
  1. `InMemoryUnitOfWorkTests extends UnitOfWorkContract` first (red: no adapter), then the package-private `@Component` with a fair `ReentrantLock` (`lock` / `try` / `finally unlock`); re-entrant, so a unit that calls another does not deadlock.
  2. `PersistenceTestAdapters.unitOfWork()` returns it typed as the port.
- **Acceptance criteria:**
  - [x] Contract green; prove it can fail: an adapter that does not lock fails the overlap test (sabotage, reverted).

### [ ] T4 — Every use case runs in the unit (~10 min)

- **Layer:** application · config
- **Files:** `CheckingAccountService.java`, `DomainConfiguration.java`, `DomainConfigurationTests.java`, the service's test constructions (`CheckingAccountServiceTests`, `CheckingAccountServiceOwnershipTests`, `DomainModelRunnerTests`)
- **Depends on:** T1, T3
- **What to do:**
  1. `CheckingAccountService` receives `UnitOfWork`; each command and query runs inside `execute`. Inside: load, operate, save, pull the events; after `execute` returns: publish them (as today, after saving). `DomainConfiguration` passes the port.
  2. T1 turns green. Sabotage: `InMemoryUnitOfWork` without the lock makes T1 fail again; reverted.
- **Acceptance criteria:**
  - [ ] T1 green; every existing test green; `HexagonalArchitectureTests` green (no Spring in the core).

### [ ] T5 — Events are published after the unit (~5 min)

- **Layer:** application (test)
- **Files:** `CheckingAccountServiceTests.java` (or a new nested class)
- **Depends on:** T4
- **What to do:**
  1. Test with a recording unit of work and publisher: the publisher is called only once the unit has finished, and a use case that fails inside the unit publishes nothing.
- **Acceptance criteria:**
  - [ ] The test passes and fails if the publication is moved inside the unit (sabotage, reverted).

### [ ] T6 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T5
- **What to do:**
  1. Glossary: "Unit of work / Unidad de trabajo"; the "Simultaneous transfers" note and the R16 race note say the unit of work now prevents them.
  2. `fase-2-hexagonal.md`: rows for `UnitOfWork` and `InMemoryUnitOfWork`; the service row; the adapter-tests table gains the unit of work's contract.
  3. Status: #78 done, next step `/plan-feature 79`; decision (unit of work port, lock in memory, events after the unit); `pendientes.md` loses the transaction item of the #7 review; a row in `historial.md`.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] A test reproduces the problem and passes after the change.
- [ ] The `UnitOfWork` port and its contract exist; the in-memory adapter meets it.
- [ ] R17-R19 unchanged; ArchUnit rules green.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no REST change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (unit of work).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

Answered by the user on 2026-11-05: unit-of-work port (not optimistic locking, not only an atomic `saveAll`).

- **One global lock:** every use case waits for the previous one, also between unrelated accounts. Simple and correct for an in-memory practice repository; a real database would lock only the rows involved. Recorded in the decision.
- **Queries in the unit too:** reading an account while another use case changes it could see a half-updated transaction list; running queries in the unit avoids it at the cost of the same lock.
- **Flaky reproduction:** a race may not show on every run. T1 repeats the scenario; if the red cannot be reproduced reliably, the sabotage in T4 is the proof that the test can fail.
- **Rollback:** the in-memory adapter cannot undo changes already made to the aggregates if a use case fails half-way (it keeps references). Today no use case can fail after changing an account (validation happens first; the transfer checks R17-R19 before touching either account); recorded as a limit of the simulated adapter.
