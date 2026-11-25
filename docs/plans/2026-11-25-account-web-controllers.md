# Plan: One web controller per use case instead of `AccountWebController`

- **Issue:** [#177](https://github.com/dawnrock/Proyect-DDD-Claude/issues/177)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-25
- **Status:** in progress
- **Total estimate:** ~110 min (14 tasks)

## Goal

`AccountWebController` (369 lines) serves the 11 routes of the account screens: list, detail, deposit, withdrawal, transfer, IBAN regeneration, the administrator's opening and the customer's another account. PMD flags it as `GodClass` (WMC 58) and `CouplingBetweenObjects` (28 types since #174), and its test class has 1,745 lines. Following the Clean Code course (ch. 19 and 22) and the split of the input ports in #174, each use case gets its own small controller in `adapter.in.web` that receives only the input ports it uses. What the detail and the operations share (rendering the detail, the notice after an operation with htmx or Post/Redirect/Get) moves to one shared component.

- **Improves:** each controller has one reason to change and few dependencies; the tests are split by use case.
- **Must NOT change:** any behaviour. Same routes, templates, model attributes, messages, status codes and redirects, same answers with and without htmx (#125, #126), same role rules (ADR 0008). The existing web tests prove it once they point at the new controllers, with no assertion changed. The REST API and `docs/api/openapi.yaml` are not touched.

### Out of scope

- `CheckingAccount` and the `validate()` of `OpenAccountCommandDef`/`SignUpCommandDef`: they keep their baseline lines and their own issue.
- `SignUpController`, `LoginController`, `AccessDeniedController`, `WebExceptionHandler` and the templates (only the test annotations that name `AccountWebController` change).
- `Monolithic`, frozen.

## Context

**Target design** (all in `adapter.in.web`, routes unchanged):

| Controller | Routes | Input ports |
| ---------- | ------ | ----------- |
| `AccountListController` | `GET /` | `CheckingAccountQueries` |
| `AccountDetailController` | `GET /accounts/{id}` | — (through `AccountDetailView`) |
| `DepositWebController` | `POST /accounts/{id}/deposits` | `MoneyMovementCommands` |
| `WithdrawalWebController` | `POST /accounts/{id}/withdrawals` | `MoneyMovementCommands` |
| `TransferWebController` | `POST /accounts/{id}/transfers` | `MoneyMovementCommands` |
| `AccountNumberWebController` | `POST /accounts/{id}/account-number` | `AccountOpeningCommands` |
| `OpenAccountWebController` | `GET`/`POST /accounts/new` | `AccountOpeningCommands` |
| `AnotherAccountWebController` | `GET`/`POST /accounts/another` | `AccountOpeningCommands`, `CheckingAccountQueries` |

**Shared component `AccountDetailView`** (`@Component`, package-private): takes `renderDetail`, `notice`, `backLabel`, `isCustomer`, `redirectToDetail` and the currency list from the controller. It depends on `CheckingAccountQueries` (the detail and the customer check). The literal routes `/accounts/new` and `/accounts/another` keep winning over `/accounts/{id}`: Spring ranks patterns across controllers, not inside one.

**Why one controller per operation and not one per port.** A single movement controller would hold the three forms, three commands, two summaries and their errors, which is again close to PMD's coupling threshold (20). One per use case matches the comment in `pmd-baseline.properties` ("one controller per use case"). If deposit and withdrawal end up as copies (CPD), the shared steps (form → command, the `IllegalArgumentException`/`DomainException` handling) move to a small helper rather than merging the controllers.

**Tests.** `@WebMvcTest` only picks up `@Controller` and `@ControllerAdvice` beans, so every web test that renders the detail imports `AccountDetailView`. An abstract base class `AccountWebTestSupport` holds what every account web test repeats: the `@MockitoBean` ports, `MockMvc`, the CSRF `post` helper and the `@Import` list. Each new test class names its controller(s) in `@WebMvcTest`. The nested groups of `AccountWebControllerTests` map to the new classes:

| Nested groups today | New test class |
| ------------------- | -------------- |
| `Listado`, `InicioSegunElRol`, `Cabecera` | `AccountListControllerTests` (`Cabecera` also needs the detail controller for the 404 page) |
| `Detalle`, `DetalleConUsuario`, `FormularioDeTransferencia`, `Acciones` | `AccountDetailControllerTests` (`DetalleConUsuario` also posts operations, so it imports their controllers) |
| `Ingresos`, `IngresosConHtmx` | `DepositWebControllerTests` |
| `Retiradas`, `RetiradasConHtmx` | `WithdrawalWebControllerTests` |
| `EnvioDeTransferencias`, `ErroresDeTransferencia`, `TransferenciasConHtmx` | `TransferWebControllerTests` |
| `Alta`, `ErroresDelAlta`, `UsuarioDelTitularEnMinusculas` | `OpenAccountWebControllerTests` |
| `ErroresAccesibles`, `CamposObligatoriosYTeclado` | `AccountFormsAccessibilityTests` (they cover the forms of several controllers) |

`AnotherAccountWebTests` becomes `AnotherAccountWebControllerTests` and `RegenerateAccountNumberWebTests` becomes `AccountNumberWebControllerTests`. `WebSecurityTests` and `SignUpControllerTests` name the controllers they reach instead of `AccountWebController`.

**Affected files:**

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in.web` | `AccountDetailView.java` (new) | Shared detail rendering and notices |
| `adapter.in.web` | the eight controllers above (new); `AccountWebController.java` (deleted) | One controller per use case |
| tests | `AccountWebTestSupport.java` (new), the new test classes, `WebSecurityTests`, `SignUpControllerTests` | Tests split by controller |
| build | `pmd-baseline.properties` | Line of `AccountWebController` removed |
| docs | `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/decisiones.md` (#125 entry names `AccountWebController.notice`) | Name the new controllers |

## Tasks

### [x] T1 — Baseline (~5 min)

- **Layer:** tests
- **Files:** — (scratchpad only)
- **Depends on:** —
- **What to do:**
  1. `./mvnw clean verify` green; record the unique test cases (class + method) of the web tests.
  2. `./mvnw pmd:pmd -Dpmd.baseline=`: record WMC and coupling of `AccountWebController`.
- **Acceptance criteria:**
  - [x] Test cases and PMD metrics recorded. (./mvnw clean verify green: 1,049 unique test cases; PMD on AccountWebController: WMC 58, ATFD 28, TCC 11.1 %, coupling 28)

### [x] T2 — Base class for the account web tests (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/dawnrock/ddd/adapter/in/web/AccountWebTestSupport.java` (new), `AccountWebControllerTests.java`, `AnotherAccountWebTests.java`, `RegenerateAccountNumberWebTests.java`
- **Depends on:** T1
- **What to do:**
  1. Move the `@MockitoBean` ports, `MockMvc`, the CSRF `post` helper and the `@Import` list to an abstract base class; the three test classes extend it.
- **Acceptance criteria:**
  - [x] Same test cases as T1; `./mvnw verify` green. (RegenerateAccountNumberWebTests writes MockMvcRequestBuilders.post in full, so its request without a CSRF token keeps having none)

### [x] T3 — Extract `AccountDetailView` (~10 min)

- **Layer:** adapter.in
- **Files:** `AccountDetailView.java` (new), `AccountWebController.java`, `AccountWebTestSupport.java`, `WebSecurityTests.java` and `SignUpControllerTests.java` (they import it)
- **Depends on:** T2
- **What to do:**
  1. Move `renderDetail`, `notice`, `backLabel`, `isCustomer`, `redirectToDetail` and the currencies to the component; the controller delegates.
- **Acceptance criteria:**
  - [x] The detail, htmx and Post/Redirect/Get tests pass unchanged. (1,049 test cases, unchanged)

### [x] T4 — `AccountListController` (~5 min)

- **Layer:** adapter.in · tests
- **Files:** `AccountListController.java` (new), `AccountWebController.java`, `AccountListControllerTests.java` (moved groups)
- **Depends on:** T3
- **What to do:**
  1. Move `GET /` and `totalsByCurrency`; move `Listado`, `InicioSegunElRol` and `Cabecera`.
- **Acceptance criteria:**
  - [x] Moved tests pass; `./mvnw verify` green. (the shared test data (callers, ids, summaries, htmx and asset matchers) moved to AccountWebTestSupport; tests that still reach GET / name AccountListController too; 1,049 test cases, unchanged)

### [x] T5 — `AccountDetailController` (~10 min)

- **Layer:** adapter.in · tests
- **Files:** `AccountDetailController.java` (new), `AccountWebController.java`, `AccountDetailControllerTests.java` (moved groups)
- **Depends on:** T4
- **What to do:**
  1. Move `GET /accounts/{id}`; move `Detalle`, `DetalleConUsuario`, `FormularioDeTransferencia` and `Acciones`.
- **Acceptance criteria:**
  - [x] Moved tests pass; a 404 for someone else's account is unchanged. (1,049 test cases, unchanged; until T11 the moved tests also name AccountWebController, which still serves the operations)

### [x] T6 — `DepositWebController` (~10 min)

- **Layer:** adapter.in · tests
- **Files:** `DepositWebController.java` (new), `AccountWebController.java`, `DepositWebControllerTests.java`, `WebSecurityTests.java` (posts deposits)
- **Depends on:** T5
- **What to do:**
  1. Move `POST /accounts/{id}/deposits`; move `Ingresos` and `IngresosConHtmx`; `WebSecurityTests` names the deposit controller.
- **Acceptance criteria:**
  - [x] Moved tests and `WebSecurityTests` pass. (1,049 test cases, unchanged)

### [x] T7 — `WithdrawalWebController` (~10 min)

- **Layer:** adapter.in · tests
- **Files:** `WithdrawalWebController.java` (new), `AccountWebController.java`, `WithdrawalWebControllerTests.java`
- **Depends on:** T6
- **What to do:**
  1. Move `POST /accounts/{id}/withdrawals`; move `Retiradas` and `RetiradasConHtmx`. If CPD finds the deposit and withdrawal steps duplicated, extract them to a small shared helper in this task.
- **Acceptance criteria:**
  - [x] Moved tests pass; CPD reports nothing. (CPD found no duplication; both movement controllers build their command in a toCommand method; 1,049 test cases, unchanged)

### [ ] T8 — `TransferWebController` (~10 min)

- **Layer:** adapter.in · tests
- **Files:** `TransferWebController.java` (new), `AccountWebController.java`, `TransferWebControllerTests.java`
- **Depends on:** T7
- **What to do:**
  1. Move `POST /accounts/{id}/transfers`, keeping the unknown target IBAN as a field error and someone else's source account as a 404; move the three transfer groups.
- **Acceptance criteria:**
  - [ ] Moved tests pass.

### [ ] T9 — `AccountNumberWebController` (~5 min)

- **Layer:** adapter.in · tests
- **Files:** `AccountNumberWebController.java` (new), `AccountWebController.java`, `RegenerateAccountNumberWebTests.java` → `AccountNumberWebControllerTests.java`
- **Depends on:** T8
- **What to do:**
  1. Move `POST /accounts/{id}/account-number` (administrator only, unchanged).
- **Acceptance criteria:**
  - [ ] Renamed tests pass.

### [ ] T10 — `OpenAccountWebController` (~10 min)

- **Layer:** adapter.in · tests
- **Files:** `OpenAccountWebController.java` (new), `AccountWebController.java`, `OpenAccountWebControllerTests.java`
- **Depends on:** T9
- **What to do:**
  1. Move `GET`/`POST /accounts/new` and `toCommand`; move `Alta`, `ErroresDelAlta` and `UsuarioDelTitularEnMinusculas`.
- **Acceptance criteria:**
  - [ ] Moved tests pass.

### [ ] T11 — `AnotherAccountWebController`; delete `AccountWebController` (~10 min)

- **Layer:** adapter.in · tests
- **Files:** `AnotherAccountWebController.java` (new), `AccountWebController.java` (deleted), `AnotherAccountWebTests.java` → `AnotherAccountWebControllerTests.java`, `AccountFormsAccessibilityTests.java` (the two remaining groups), `SignUpControllerTests.java`, `WebSecurityTests.java`
- **Depends on:** T10
- **What to do:**
  1. Move `GET`/`POST /accounts/another`; delete the old controller and its test class once empty.
  2. `SignUpControllerTests` and `WebSecurityTests` name the controllers they reach.
- **Acceptance criteria:**
  - [ ] No reference to `AccountWebController` left in `src/`; `./mvnw verify` green with the T1 test cases, only moved between classes.

### [ ] T12 — PMD baseline (~5 min)

- **Layer:** build
- **Files:** `pmd-baseline.properties`
- **Depends on:** T11
- **What to do:**
  1. Remove the `AccountWebController` line; `./mvnw verify` with no new warning. If a new controller or test class trips a rule, split it further rather than add it to the baseline.
- **Acceptance criteria:**
  - [ ] `./mvnw pmd:pmd -Dpmd.baseline=` only lists `CheckingAccount`, `OpenAccountCommandDef` and `SignUpCommandDef`.

### [ ] T13 — Documents (~5 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/decisiones.md`
- **Depends on:** T12
- **What to do:**
  1. Component and test tables of phase 2 name the new controllers and `AccountDetailView`; the #125 decision names `AccountDetailView.notice`.
- **Acceptance criteria:**
  - [ ] `grep -rn AccountWebController docs/arquitectura docs/ia/decisiones.md` finds only history.

### [ ] T14 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `pendientes.md`, `decisiones.md`, `historial.md`
- **Depends on:** T13
- **What to do:**
  1. #177 done and next step (OAuth / Auth0, or the next baseline refactoring); the PMD baseline item without `AccountWebController`; a row in `historial.md`.
- **Acceptance criteria:**
  - [ ] The CI status check passes.

## Global acceptance criteria

**Specific:**

- [ ] `AccountWebController` no longer exists; each account route is served by its own controller, which receives only the input ports it uses.
- [ ] `pmd-baseline.properties` no longer lists `AccountWebController`, and the analysis reports nothing new.
- [ ] No behaviour change: the existing web tests pass pointed at the new controllers, with no assertion changed; templates and `docs/api/openapi.yaml` unchanged.

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

- **Several controllers in one test.** Some groups reach more than one route (the header test hits the 404 detail; `DetalleConUsuario` posts operations; the sign-up flow lands on the detail). Their `@WebMvcTest` lists every controller they reach; the plan accepts that over splitting those tests.
- **Spring Security matchers** are path-based (`WebSecurityConfig`), not controller-based, so moving routes does not change who may call them; `WebSecurityTests` proves it.
- **Granularity.** Eight controllers instead of one is more files. If the user prefers one controller per input port (list + detail, movements, opening), T6-T8 and T9-T11 merge, at the risk of tripping PMD's coupling threshold again.
