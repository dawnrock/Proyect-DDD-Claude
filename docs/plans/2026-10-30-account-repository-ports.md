# Plan: Output ports for the account repository and account ownership

- **Issue:** [#15](https://github.com/dawnrock/Proyect-DDD-Claude/issues/15)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-10-30
- **Status:** in progress
- **Total estimate:** ~60 min (8 tasks)

## Goal

Invert the dependency between the application and persistence: the core declares what it needs (an output port) and `adapter.out.persistence` implements it. `domain.model.CheckingAccountRepository` is the port for the aggregate; `application.AccountOwnershipRepository` is the port for account ownership, which lives in `application` because the user's identity never reaches the domain (ADR 0008). Both in-memory adapters become package-private implementations of their port.

What must **not** change: observable behaviour (REST, web, demo data), the repository semantics (stores references, unique account number, list sorted by number, unmodifiable list) and every existing test scenario.

### Out of scope

- Input ports `CheckingAccountCommands` / `CheckingAccountQueries` (#16).
- `config.DomainConfiguration` and building the core without Spring annotations (#20): the adapters stay Spring beans via `@Repository` component scanning, which works with package-private classes.
- `HexagonalArchitectureTests` (#22): until then the rules keep living in `LayeredArchitectureTests`.
- Saving both accounts of a transfer in one transaction (pending item from #7).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `src/main/java/com/pronoide/ddd/domain/model/CheckingAccountRepository.java` | New output port (interface): `save`, `findById`, `findByAccountNumber`, `findAll` |
| `application` | `src/main/java/com/pronoide/ddd/application/AccountOwnershipRepository.java` | New output port (interface): `assign`, `ownerOf`, `accountsOf` |
| `application` | `src/main/java/com/pronoide/ddd/application/CheckingAccountService.java` | Depends on the two ports, no import of `adapter.out` |
| `adapter.out.persistence` | `InMemoryCheckingAccountRepository.java`, `InMemoryAccountOwnershipRepository.java` | Implement their port; package-private |
| tests | `domain/model/CheckingAccountRepositoryContract.java`, `application/AccountOwnershipRepositoryContract.java` | Abstract, reusable port contract tests (any future implementation extends them) |
| tests | `adapter/out/persistence/InMemory*Tests.java` | Extend the contract; keep only what is specific to the in-memory adapter |
| tests | `adapter/out/persistence/PersistenceTestAdapters.java` | Public test helper that hands the in-memory adapters to other test packages **typed as their ports** |
| tests | `application/CheckingAccountService*Tests.java`, `DemoDataLoaderTests.java` | Use the port types through the helper |
| tests | `LayeredArchitectureTests.java` | New rule: only `adapter.out.persistence` (and `config`) know the adapters |
| docs | `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/*` | Class-role table, project status (including the Telegram agreement) |

DDD building blocks: **repository as an output port** (one per aggregate, declared next to the aggregate in `domain.model`) and its **output adapter**. The ownership port is not a domain repository: it is an application port, because ownership is a security concern decided in `application` with `Caller` (ADR 0008).

Technical notes:

- The duplicate-number `IllegalStateException` of `save` becomes part of the port contract (equivalent of a unique index; see `decisiones.md`, R16). It is documented in the port's Javadoc.
- "Stores references, not copies" is a property of the in-memory adapter only, so it stays in `InMemoryCheckingAccountRepositoryTests` and out of the contract.
- The port interfaces carry no Spring annotations (D1 / `DomainIsolationTests`).
- `CheckingAccountServiceTests` spies the repository with Mockito; spying an instance typed as the port keeps working.
- `graphify-out/graph.json` does not exist in this checkout; the impact list above comes from `grep` (`CheckingAccountService` and four test classes are the only users of the adapters).

## Tasks

### [x] T1 — `CheckingAccountRepository` port (~5 min)

- **Layer:** domain · adapter.out
- **Files:** `src/main/java/com/pronoide/ddd/domain/model/CheckingAccountRepository.java`, `src/main/java/com/pronoide/ddd/adapter/out/persistence/InMemoryCheckingAccountRepository.java`
- **Depends on:** —
- **What to do:**
  1. Create the interface in `domain.model` with the four current methods and their Javadoc (unique account number, sorted unmodifiable list, mandatory arguments).
  2. `InMemoryCheckingAccountRepository implements CheckingAccountRepository` (`@Override` on each method; it stays public for now).
- **Acceptance criteria:**
  - [x] `./mvnw verify` green; `DomainIsolationTests` and `check-domain.mjs` pass with the new interface.

### [x] T2 — Reusable contract test for `CheckingAccountRepository` (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/domain/model/CheckingAccountRepositoryContract.java`, `src/test/java/com/pronoide/ddd/adapter/out/persistence/InMemoryCheckingAccountRepositoryTests.java`
- **Depends on:** T1
- **What to do:**
  1. Move the implementation-agnostic scenarios (find by id / number, no duplicates on re-save, unique number, nulls rejected, sorted and unmodifiable list) into an abstract `CheckingAccountRepositoryContract` with `protected abstract CheckingAccountRepository createRepository()`, keeping their `@Nested` groups, names and `@DisplayName`.
  2. `InMemoryCheckingAccountRepositoryTests extends CheckingAccountRepositoryContract` and keeps only "stores the account itself, not a copy".
- **Acceptance criteria:**
  - [x] `./mvnw test -Dtest='InMemoryCheckingAccountRepositoryTests*'` runs the same number of scenarios as before (14), all green.

### [x] T3 — `AccountOwnershipRepository` port and its contract test (~10 min)

- **Layer:** application · adapter.out · tests
- **Files:** `src/main/java/com/pronoide/ddd/application/AccountOwnershipRepository.java`, `InMemoryAccountOwnershipRepository.java`, `src/test/java/com/pronoide/ddd/application/AccountOwnershipRepositoryContract.java`, `InMemoryAccountOwnershipRepositoryTests.java`
- **Depends on:** —
- **What to do:**
  1. Interface in `application` with `assign`, `ownerOf`, `accountsOf` (Javadoc: one owner per account, ADR 0008, why it is not in `domain.model`).
  2. The in-memory adapter implements it.
  3. Move its four scenarios into the abstract `AccountOwnershipRepositoryContract`; the adapter's test extends it.
  4. *(Added while implementing)* `LayeredArchitectureTests`: the `Application` layer may now be accessed by `Persistence`, because the adapter implements a port that lives in `application`.
- **Acceptance criteria:**
  - [x] The four ownership scenarios run through the contract and pass.

### [ ] T4 — Test helper typed as ports (~5 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/adapter/out/persistence/PersistenceTestAdapters.java`
- **Depends on:** T1, T3
- **What to do:**
  1. Public final class with `static CheckingAccountRepository accounts()` and `static AccountOwnershipRepository ownership()` returning new in-memory adapters. It is the only way other test packages reach the adapters once they are package-private.
- **Acceptance criteria:**
  - [ ] Compiles; return types are the ports, not the adapters.

### [ ] T5 — Application tests use the ports (~10 min)

- **Layer:** tests
- **Files:** `CheckingAccountServiceTests.java`, `CheckingAccountServiceOwnershipTests.java`, `DemoDataLoaderTests.java`
- **Depends on:** T4
- **What to do:**
  1. Replace fields and constructions of `InMemory*` with the port types and `PersistenceTestAdapters` (`spy(PersistenceTestAdapters.accounts())` in `CheckingAccountServiceTests`).
- **Acceptance criteria:**
  - [ ] No test outside `adapter.out.persistence` imports an `InMemory*` class (`grep -rn "InMemory.*Repository" src/test` only matches that package).
  - [ ] `./mvnw verify` green.

### [ ] T6 — `CheckingAccountService` depends on the ports (~5 min)

- **Layer:** application
- **Files:** `src/main/java/com/pronoide/ddd/application/CheckingAccountService.java`
- **Depends on:** T1, T3, T5
- **What to do:**
  1. Fields typed as `CheckingAccountRepository` and `AccountOwnershipRepository`; remove the `adapter.out.persistence` imports. Spring injects the adapters by type.
- **Acceptance criteria:**
  - [ ] `grep -rn "adapter.out" src/main/java/com/pronoide/ddd/application` returns nothing.
  - [ ] `DddLabApplicationTests` and `DemoProfileTests` pass (the context wires the ports).

### [ ] T7 — Package-private adapters and ArchUnit rule (~10 min)

- **Layer:** adapter.out · tests
- **Files:** both `InMemory*Repository.java`, `src/test/java/com/pronoide/ddd/LayeredArchitectureTests.java`
- **Depends on:** T6
- **What to do:**
  1. Remove `public` from both adapter classes (their methods stay `public` because they implement the port).
  2. In `LayeredArchitectureTests`: `whereLayer("Persistence").mayNotBeAccessedByAnyLayer()` and a new rule `solo_la_persistencia_y_config_conocen_los_adaptadores_de_salida` (`noClasses().that().resideOutsideOfPackages("..adapter.out.persistence..", "..config..").should().dependOnClassesThat().resideInAPackage("..adapter.out.persistence..")`). Update the Javadoc of `la_persistencia_no_tiene_interfaces` (the ports live outside the package, so it still holds) and of the class (provisional until #22).
  3. Validate by breaking it: temporarily import an adapter from `application`, check `./mvnw test -Dtest=LayeredArchitectureTests` fails on the new rule, revert. Mention it in the commit.
- **Acceptance criteria:**
  - [ ] The new rule fails when broken and passes after reverting.
  - [ ] `./mvnw verify` green.

### [ ] T8 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/historial.md`
- **Depends on:** T7
- **What to do:**
  1. `fase-2-hexagonal.md`: rows of the two adapters and `CheckingAccountService` ("State after #15"), add the two ports to the table and remove `CheckingAccountRepository` from "classes that arrive later".
  2. `estado-del-proyecto.md`: #15 done, next step `/plan-feature 16`. **Fix the working agreement that still says Telegram notifications are disabled**: the user has turned them back on, so skills run `scripts/notify.mjs` again.
  3. `pendientes.md`: update the Telegram line accordingly (no longer silenced with `TELEGRAM_DISABLED=1`); remove the "application uses `adapter.out.persistence` until #15" provisional note from `decisiones.md` and add the decision about the ownership port in `application` and the `PersistenceTestAdapters` helper.
  4. `historial.md`: one row for the PR.
- **Acceptance criteria:**
  - [ ] `estado-del-proyecto.md` no longer says Telegram is disabled.
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] `application` depends only on the interfaces (accounts and ownership): no import of `adapter.out` in `src/main/java/.../application`.
- [ ] No class outside `adapter.out.persistence` and `config` knows the adapters, verified by ArchUnit; both adapters are package-private.
- [ ] The repository port has a reusable contract test (`CheckingAccountRepositoryContract`), and so does the ownership port.
- [ ] Same behaviour: every pre-existing test scenario still runs and passes.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no REST change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: ports are not business terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Name of the ownership port:** the issue suggests `AccountOwnership`; this plan uses `AccountOwnershipRepository` for symmetry with `CheckingAccountRepository` and its adapter. Easy to rename before implementing.
- **Test helper in `adapter.out.persistence`:** application tests still reach the adapters through `PersistenceTestAdapters` (test code, not checked by ArchUnit). The alternative, a hand-written fake per test package, duplicates the in-memory logic.
- **Contract test location:** `CheckingAccountRepositoryContract` lives in the test `domain.model` package next to its port; it is abstract and not named `*Tests`, so Surefire only runs it through its subclasses.
- Wiring stays on component scanning until #20; a package-private `@Repository` is instantiated by Spring without problems, but `DddLabApplicationTests` confirms it in T6/T7.
