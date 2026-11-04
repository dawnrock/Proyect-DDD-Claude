# Plan: Adapter tests without the hexagon

- **Issue:** [#21](https://github.com/dawnrock/Proyect-DDD-Claude/issues/21)
- **Type:** refactor (tests only)
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-04
- **Status:** in progress
- **Total estimate:** ~40 min (4 tasks)

## Goal

Test every adapter in isolation, as the hexagonal architecture allows (Lab 27.9.4). Most of it already exists from #15-#20; this plan **audits** it, fills the real gaps and makes the main criterion enforceable:

- An ArchUnit rule over the **test code**: no input-adapter test starts the full context (`@SpringBootTest`).
- The Spring event adapter is tested with a **test listener** in a minimal Spring context (today it is only tested against a mocked `ApplicationEventPublisher`).
- The numbering adapter, the other port implemented in `adapter.out.persistence`, gets a reusable **port contract** like the repositories.

Production code does not change.

### Out of scope

- New behaviour or new error paths: the audit below shows every endpoint's error paths are already covered.
- `HexagonalArchitectureTests` (#22).
- A contract for `IncomeVerifier`: its only implementation is the fake tax agency, already tested on its own; the issue asks for persistence contracts.

## Context

**Audit of the existing adapter tests** (all `@WebMvcTest` with `CheckingAccountCommands`/`CheckingAccountQueries` mocked with `@MockitoBean`; none starts the full context):

| Endpoint | 404 | 422 | 400 | Test class |
| -------- | --- | --- | --- | ---------- |
| `POST /api/accounts` | n/a (creates) | deposit below minimum, duplicate number | field errors (NIF, name, deposit, currency, user), malformed JSON | `AccountControllerTests` (+ 403 for non-admins) |
| `GET /api/accounts` | n/a | n/a | n/a | `AccountControllerTests` (200 only: no error path) |
| `GET /api/accounts/{id}` | unknown or not owned | n/a | malformed UUID | `AccountControllerTests` |
| `POST /api/accounts/{id}/deposits` | yes | currency | amount, currency format | `AccountControllerTests` |
| `POST /api/accounts/{id}/withdrawals` | yes | currency | amount | `AccountControllerTests` |
| `POST /api/transfers` | source and target | same account, currency | IBAN, amount | `TransferControllerTests` |
| Web: detail, deposits, withdrawals, transfers, open account | custom 404 page | back to the form with the domain message | back to the form with the field error | `AccountWebControllerTests` (+ 403 page, `WebSecurityTests`) |
| Translation of every error type to HTTP | | | | `RestExceptionHandlerTests` (`FailingTestController`) |

Output adapters: `InMemoryCheckingAccountRepositoryTests` and `InMemoryAccountOwnershipRepositoryTests` extend the port contracts (#15); `InMemoryAccountNumberSequenceTests` and `FakeTaxAgencyIncomeVerifierTests` test the adapters directly (#18); `SpringDomainEventPublisherTests` uses a mocked `ApplicationEventPublisher` (#17). CLI: `DomainModelRunnerTests` uses the real service with in-memory adapters, without Spring (#19).

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| tests (root) | `AdapterTestIsolationTests.java` (new) | ArchUnit over test classes: input-adapter tests do not use `@SpringBootTest` |
| tests `adapter.out.event` | `SpringDomainEventPublisherContextTests.java` (new) | Minimal Spring context with the adapter and a test listener |
| tests `domain.model` / `adapter.out.persistence` | `AccountNumberSequenceContract.java` (new), `InMemoryAccountNumberSequenceTests.java` | Port contract; the adapter test extends it |
| docs | `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/*` | "How adapters are tested" section with the matrix above, project status |

## Tasks

### [x] T1 — No input-adapter test starts the full context (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/AdapterTestIsolationTests.java`
- **Depends on:** —
- **What to do:**
  1. ArchUnit test that imports **only test classes** (`ImportOption.OnlyIncludeTests`): no class in `..adapter.in..` is annotated (or meta-annotated) with `@SpringBootTest`. The full-context tests stay in the root package (`DddLabApplicationTests`, `DemoProfileTests`), as `src/test/CLAUDE.md` says.
  2. Validate by breaking it: temporarily annotate a test in `adapter.in.rest` with `@SpringBootTest`, see the rule fail, revert.
- **Acceptance criteria:**
  - [x] The rule passes and fails when broken (issue criterion: no input-adapter test starts the full context).

### [x] T2 — Spring event adapter with a test listener (~10 min)

- **Layer:** tests (adapter.out)
- **Files:** `src/test/java/com/pronoide/ddd/adapter/out/event/SpringDomainEventPublisherContextTests.java`
- **Depends on:** —
- **What to do:**
  1. `@SpringJUnitConfig` with only `SpringDomainEventPublisher` and a test `@EventListener` that records what it receives (no application, no hexagon). Publishing two events through the port reaches the listener in order; a listener for `MoneyDeposited` does not receive `MoneyWithdrawn`.
  2. Prove it can fail: make the adapter not forward (sabotage), see red, revert.
- **Acceptance criteria:**
  - [x] The test passes with a real Spring event bus and fails when the adapter does not forward (issue criterion: Spring adapter with a test listener).

### [x] T3 — Port contract for `AccountNumberSequence` (~10 min)

- **Layer:** tests (adapter.out)
- **Files:** `src/test/java/com/pronoide/ddd/domain/model/AccountNumberSequenceContract.java`, `InMemoryAccountNumberSequenceTests.java`
- **Depends on:** —
- **What to do:**
  1. Abstract contract (any implementation): generated numbers are valid Spanish IBANs (`AccountNumber` accepts them and the mod-97 check holds) and never repeat. The in-memory test extends it and keeps only what is specific (bank 2100, branch 0418, consecutive numbers, concurrency).
  2. Prove it can fail: an adapter that repeats a number fails the contract (sabotage), revert.
- **Acceptance criteria:**
  - [x] Same scenarios as before, now split between the contract and the adapter test; all green (issue criterion: persistence contract against the in-memory adapter).

### [ ] T4 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1, T2, T3
- **What to do:**
  1. `fase-2-hexagonal.md`: new section "How adapters are tested" with the matrix of error paths per endpoint and the test type of each adapter.
  2. Project status: #21 done, next step `/plan-feature 22`; decision (adapter tests never start the full context, enforced by ArchUnit over the tests).
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] No input-adapter test starts the full context (ArchUnit over the tests).
- [ ] The error paths of every endpoint (404, 422, 400) are covered, as the audit shows.
- [ ] The Spring event adapter is tested with a test listener; the persistence ports have contracts tested against the in-memory adapters.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no REST change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: no new business terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Most of the issue is already done:** the plan does not add tests for coverage's sake; it adds the enforceable rule, the event test with a real listener and the missing contract. If you would rather have an extra test per endpoint and error, say so and the audit turns into tasks.
- **ArchUnit over test classes:** a second `@AnalyzeClasses` with `OnlyIncludeTests`; it does not change the existing rules on production code.
