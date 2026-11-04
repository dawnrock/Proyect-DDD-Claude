# Plan: Wire ports and adapters in config, without Spring in the core

- **Issue:** [#20](https://github.com/dawnrock/Proyect-DDD-Claude/issues/20)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-04
- **Status:** done
- **Total estimate:** ~45 min (5 tasks)

## Goal

The hexagon should not need Spring annotations: a configuration class builds the core and hands it its adapters. `config.DomainConfiguration` declares `@Bean`s for the domain service (`MoneyTransferService`), the factory (`CheckingAccountFactory`) and the application service (`CheckingAccountService`), which loses `@Service` and stops building its collaborators with `new`. The event consumers lose `@Component` and are declared in `config` too.

What must **not** change: observable behaviour (REST, web, CLI demo, events), and every existing test scenario. The existing context tests (`DddLabApplicationTests`, `DemoProfileTests`) act as characterization tests: the application must start and behave the same.

### Out of scope

- Transactions: the in-memory persistence has none (pending item from #7, for real persistence).
- Moving the output adapters to `@Bean`s in `config`: they are package-private and live outside the hexagon, where Spring is allowed, so they stay component-scanned; `config` receives them **by their port types** as `@Bean` method parameters (see open questions).
- `HexagonalArchitectureTests` (#22): the new rule goes to `LayeredArchitectureTests`, like the previous ones.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `config` | `DomainConfiguration.java` (new) | `@Configuration` with `@Bean`s: `MoneyTransferService`, `CheckingAccountFactory`, `CheckingAccountService`, `AuditLogListener`, `CustomerNotificationListener` |
| `application` | `CheckingAccountService.java` | No `@Service`; receives `CheckingAccountFactory` and `MoneyTransferService` by constructor instead of building them |
| `application.listener` | `AuditLogListener.java`, `CustomerNotificationListener.java` | No `@Component`; keep `@EventListener` (justified exception, see below) |
| tests | `CheckingAccountService*Tests.java`, `DomainModelRunnerTests.java` | Build the service with the new constructor (6 call sites) |
| tests | `config/DomainConfigurationTests.java` (new) | The configuration builds the core from the ports, without Spring |
| tests | `LayeredArchitectureTests.java` | `config` becomes a layer; new rule: the core does not depend on Spring |
| docs | `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/*` | Class-role table, decisions, pending items, project status |

Spring imports in the core today: `domain..` has none (`DomainIsolationTests`); in `application`, only `@Service` on `CheckingAccountService` and `@Component` + `@EventListener` on the two listeners.

**Listeners — justified exception (issue criterion).** The two consumers are reached through Spring's event mechanism (`SpringDomainEventPublisher` → `ApplicationEventPublisher` → `@EventListener`). Removing `@EventListener` would need a hand-made dispatcher (a listener port plus a registry in `config`) that only re-implements what Spring already does. They keep `@EventListener` and only that: `@Component` goes, `config` declares them, and the new ArchUnit rule allows `org.springframework.context.event` in `application.listener` only.

## Tasks

### [x] T1 — `DomainConfiguration` builds the factory and the domain service (~10 min)

- **Layer:** config · application
- **Files:** `config/DomainConfiguration.java`, `config/DomainConfigurationTests.java`, `CheckingAccountService.java`, the 6 test constructions, `LayeredArchitectureTests.java`
- **Depends on:** —
- **What to do:**
  1. Test first (plain JUnit, no Spring): `new DomainConfiguration()` builds a `CheckingAccountFactory` from stubbed ports and a `MoneyTransferService`.
  2. `@Configuration` with `@Bean MoneyTransferService` and `@Bean CheckingAccountFactory(AccountNumberSequence, IncomeVerifier)`. `CheckingAccountService` receives `CheckingAccountFactory` and `MoneyTransferService` by constructor (no more `new` inside); update the test constructions.
  3. `LayeredArchitectureTests`: `config` becomes a layer (`toda_clase_pertenece_a_una_capa` accepts it; it may use every other layer and nobody uses it).
- **Acceptance criteria:**
  - [x] `DomainConfigurationTests` passes; `./mvnw verify` green (the context still starts).

### [x] T2 — `CheckingAccountService` without `@Service` (~5 min)

- **Layer:** application · config
- **Files:** `CheckingAccountService.java`, `DomainConfiguration.java`, `DomainConfigurationTests.java`
- **Depends on:** T1
- **What to do:**
  1. Test first: the configuration builds a `CheckingAccountService` that is both input ports, from the output ports, the factory and the domain service.
  2. Remove `@Service` and its import; `@Bean CheckingAccountService` in `config`. Prove the context tests depend on it: without the `@Bean` the context fails to start (sabotage, reverted).
- **Acceptance criteria:**
  - [x] No `org.springframework` import in `CheckingAccountService`; `DddLabApplicationTests` and `DemoProfileTests` green.

### [x] T3 — Listeners declared in `config` (~5 min)

- **Layer:** application.listener · config
- **Files:** `AuditLogListener.java`, `CustomerNotificationListener.java`, `DomainConfiguration.java`
- **Depends on:** T2
- **What to do:**
  1. Remove `@Component`; `@Bean` for both in `config`. Their Javadoc explains why `@EventListener` stays.
  2. The integration test from #17 ("un ingreso llega al consumidor de auditoría") proves the wiring; check it fails without the `@Bean` (sabotage, reverted).
- **Acceptance criteria:**
  - [x] Listener tests and the audit integration test green.

### [x] T4 — ArchUnit: the core does not depend on Spring (~10 min)

- **Layer:** tests
- **Files:** `LayeredArchitectureTests.java`
- **Depends on:** T3
- **What to do:**
  1. Rule `el_nucleo_no_depende_de_spring`: no class in `..domain..` or `..application..` depends on `org.springframework..`, except `..application.listener..` on `org.springframework.context.event..` (the justified exception).
  2. Validate by breaking it: `@Service` back on `CheckingAccountService`, and `@Component` on a listener; each must fail. Revert.
- **Acceptance criteria:**
  - [x] The rule fails for each sabotage and passes after reverting (issue criterion).

### [x] T5 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T4
- **What to do:**
  1. `fase-2-hexagonal.md`: "State after #20"; `DomainConfiguration` row; core classes "built in `config`"; adapters "component-scanned, injected into `config` by their ports"; listeners' exception.
  2. Project status: #20 done, next step `/plan-feature 21`; decision (what `config` builds, adapters stay components, listeners keep `@EventListener`); pending items: remove "register `MoneyTransferService` as a bean in `config`" (#7) and update the #7 decision that said the service creates it with `new`.
- **Acceptance criteria:**
  - [x] The CI check of the project status passes.

### [x] T6 — Review fixes (~10 min)

- **Layer:** tests · docs
- **Files:** `LayeredArchitectureTests.java`, `DomainConfigurationTests.java`, `DddLabApplicationTests.java`, `docs/arquitectura/fase-2-hexagonal.md`
- **Depends on:** T5
- **What to do:**
  1. `revisor-ddd`: the listeners' exception allows only `@EventListener` (not the whole `org.springframework.context.event`); the output-adapter rule no longer exempts `config`, which never imports them (renamed `solo_adapter_out_conoce_los_adaptadores_de_salida`); `DomainConfigurationTests` covers the listener beans; a context test checks a single service behind both input ports and both listeners registered. Both rule changes validated by breaking them.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green; each tightened rule fails when broken.

## Global acceptance criteria

**Specific:**

- [x] No class of `domain..` nor `application` (except the listeners, justified) imports `org.springframework` (ArchUnit).
- [x] The application starts and `GET /actuator/health` responds `UP`.
- [x] `config.DomainConfiguration` builds the factory, the domain service and the application service.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR).
- [x] If the API changes: n/a (no REST change).
- [x] Any new business term is in `docs/dominio/glosario.md` (n/a: no new business terms).
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Listeners keep `@EventListener`:** the issue allows it "if justified". The alternative (a listener port and a dispatcher in `config`) removes Spring from `application` completely but re-implements Spring's event dispatch.
- **Output adapters stay `@Component`/`@Repository`:** they are package-private and outside the hexagon. Declaring them as `@Bean`s in `config` would force making them public, which undoes #15. `config` still "hands the core its adapters": it receives them by their port types.
- **Lombok `@Slf4j` in the listeners** stays: it is Lombok, not Spring, and it is allowed outside `domain..`.
