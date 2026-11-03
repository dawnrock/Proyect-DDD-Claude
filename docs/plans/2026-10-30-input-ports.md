# Plan: Input ports CheckingAccountCommands and CheckingAccountQueries

- **Issue:** [#16](https://github.com/dawnrock/Proyect-DDD-Claude/issues/16)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-10-30
- **Status:** in progress
- **Total estimate:** ~55 min (6 tasks)

## Goal

Make the input adapters depend on what the application **offers** (its use cases) and not on the class that implements them (Lab 27.7). The application declares two input ports, `CheckingAccountCommands` (open, deposit, withdraw, transfer) and `CheckingAccountQueries` (list, detail); the application service `CheckingAccountService` implements both (it keeps its name, see below). REST and web controllers, and the demo data loader, only know the ports.

What must **not** change: observable behaviour (REST, web, status codes, demo data), the use-case signatures (`Caller` + Immutables command or id → Immutables view) and every existing test scenario. The existing application and controller tests act as characterization tests: they must pass unchanged in substance after each task.

### Out of scope

- New query objects: the two queries take only the `Caller` and, for the detail, an id, so they keep their current parameters. Decided with the user (2026-11-03): a type wrapping a single id adds code and no rule; the views they return are already Immutables.
- Renaming the service to `CheckingAccountApplicationService` (the name in the issue and Lab 27.7). Decided with the user (2026-11-03): once the adapters only see the ports, the implementation's name matters little, and the rename would touch ~15 files with no behaviour change.
- Domain events (#17), factory and generated account number (#18), CLI adapter (#19), building the core in `config` (#20).
- `HexagonalArchitectureTests` (#22): the new rule goes to `LayeredArchitectureTests`, like the #15 ones.
- REST contract: no route, body or status code changes (OpenAPI n/a).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `application` | `CheckingAccountService.java` | Implements both input ports |
| `application` | `CheckingAccountCommands.java`, `CheckingAccountQueries.java` | New input ports (interfaces) with the current use-case Javadoc |
| `application` | `DemoDataLoader.java` | Depends on `CheckingAccountCommands` |
| `adapter.in.rest` | `AccountController.java` (both ports), `TransferController.java` (commands) | Depend on the ports |
| `adapter.in.web` | `AccountWebController.java` (both ports) | Depends on the ports |
| tests | `CheckingAccountServiceTests.java`, `DemoDataLoaderTests.java` | Typed by port |
| tests | `AccountControllerTests`, `TransferControllerTests`, `AccountWebControllerTests`, `WebSecurityTests` | `@MockitoBean` of the ports instead of the service |
| tests | `LayeredArchitectureTests.java` | New rule: input adapters do not know the application service |
| docs | `docs/arquitectura/fase-2-hexagonal.md`, contracts, `docs/ia/*` | Class-role table, service name, project status |

Building blocks: **input ports** (driving side, interfaces in `application`, see `hexagonal-adapter` skill) and the **application service** that implements them. The commands and views stay as they are (Immutables, `*Def` + generated class).

Technical notes:

- One Spring bean implements both interfaces, so injection by interface works without qualifiers. `@MockitoBean` replaces each port separately in `@WebMvcTest` slices; a controller that uses both gets two mocks.
- `DemoDataLoader` lives in `application` but behaves like an input adapter (it becomes `DomainModelRunner` in #19), so it uses the commands port.

## Tasks

### [x] T1 — `CheckingAccountCommands` and `CheckingAccountQueries` (~10 min)

- **Layer:** application
- **Files:** `CheckingAccountCommands.java`, `CheckingAccountQueries.java`, `CheckingAccountService.java`, `CheckingAccountServiceTests.java`
- **Depends on:** —
- **What to do:**
  1. Create both interfaces; move each use case's Javadoc (rules, `@throws`) to the port and leave `@Override` in the service.
  2. Test (red first): the service is a `CheckingAccountCommands` and a `CheckingAccountQueries`; type the service fields in the application tests by port where they only use one side.
- **Acceptance criteria:**
  - [x] The service implements both ports; `./mvnw verify` green.

### [x] T2 — `DemoDataLoader` uses the commands port (~5 min)

- **Layer:** application
- **Files:** `DemoDataLoader.java`, `DemoDataLoaderTests.java`
- **Depends on:** T1
- **What to do:**
  1. Field typed as `CheckingAccountCommands`; the test builds it with the port type.
- **Acceptance criteria:**
  - [x] `DemoDataLoaderTests` and `DemoProfileTests` pass.

### [x] T3 — REST controllers depend on the ports (~10 min)

- **Layer:** adapter.in
- **Files:** `AccountController.java`, `TransferController.java`, `AccountControllerTests.java`, `TransferControllerTests.java`
- **Depends on:** T1
- **What to do:**
  1. Tests first: `@MockitoBean CheckingAccountCommands commands` and `@MockitoBean CheckingAccountQueries queries` (only `commands` in `TransferControllerTests`); stubs and `verify` point to the right port. They fail to start the slice until the controllers change.
  2. `AccountController` receives both ports; `TransferController` only `CheckingAccountCommands`. Javadoc updated.
- **Acceptance criteria:**
  - [x] Both test classes pass mocking only the ports.
  - [x] No import of `CheckingAccountService` in `adapter.in.rest`.

### [ ] T4 — Web controller depends on the ports (~10 min)

- **Layer:** adapter.in
- **Files:** `AccountWebController.java`, `AccountWebControllerTests.java`, `WebSecurityTests.java`
- **Depends on:** T1
- **What to do:**
  1. Same as T3 for the web: tests mock both ports, then the controller receives `CheckingAccountCommands` and `CheckingAccountQueries`.
- **Acceptance criteria:**
  - [ ] `AccountWebControllerTests` and `WebSecurityTests` pass mocking only the ports.
  - [ ] No import of `CheckingAccountService` in `adapter.in.web`.

### [ ] T5 — ArchUnit rule for the input side (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/LayeredArchitectureTests.java`
- **Depends on:** T3, T4
- **What to do:**
  1. Rule `los_adaptadores_de_entrada_solo_conocen_los_puertos_de_entrada`: no class in `..adapter.in..` depends on `CheckingAccountService` (by class, so a rename breaks compilation instead of silently weakening it).
  2. Validate by breaking it: temporarily inject the service into a controller, see the rule fail, revert. Mention it in the commit.
- **Acceptance criteria:**
  - [ ] The rule fails when broken and passes after reverting; `./mvnw verify` green.

### [ ] T6 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T5
- **What to do:**
  1. `fase-2-hexagonal.md`: "State after #16"; rows for the controllers, `DemoDataLoader`, the service and the two input ports; remove them from "classes that arrive later".
  2. Project status: #16 done, next step `/plan-feature 17`; decisions about the ports, no query objects and keeping the service name; historial row.
- **Acceptance criteria:**
  - [ ] The class-role table reflects the ports.
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] No input adapter imports `CheckingAccountService` (ArchUnit).
- [ ] The `@WebMvcTest` tests mock the ports with Mockito (`@MockitoBean`).
- [ ] Same behaviour: every pre-existing test scenario still runs and passes.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no route, body or status code changes).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: ports are not business terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Decisions taken with the user (2026-11-03):** no query objects and no rename (see Out of scope).
- The ArchUnit rule names `CheckingAccountService` by class, so a future rename breaks compilation instead of silently weakening it.
- Ownership checks stay inside the use cases, behind the ports (ADR 0008).
