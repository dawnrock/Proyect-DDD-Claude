# Plan: Fix the discrepancies between openapi.yaml and the REST API

- **Issue:** [#108](https://github.com/dawnrock/Proyect-DDD-Claude/issues/108)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-17
- **Status:** in progress
- **Total estimate:** ~70 min (8 tasks)

## Goal

Schemathesis 4.33.4, run against `Hexagonal` on 2026-11-17 (607 cases, 17 failures), found places where the REST API and `docs/api/openapi.yaml` disagree. The contract is the single source of truth, so each case is settled either by making the contract describe the real, intended behaviour or by changing the implementation where the behaviour was wrong.

Current vs. expected behaviour:

| # | Case | Current | Expected (decided with the user, 2026-11-17) |
| - | ---- | ------- | -------------------------------------------- |
| 1 | Amount `0.001` | Contract says valid (`exclusiveMinimum: 0`); API answers 400 because amounts are rounded half-up to two decimals (decision of 2026-10-23). For initial deposits the 400 comes from the domain safety net, not from validation | Contract says `minimum: 0.005` (the real limit) and explains the rounding; commands and REST DTOs reject it before the domain |
| 2 | Amount `1.79e308` | Accepted; the balance grows to hundreds of digits | Technical limit of **999,999,999.99 per operation**, checked in the commands of `application` (so REST, web and CLI share it) and with `@DecimalMax` in the REST DTOs; `maximum` in the contract. Not a business rule |
| 3 | `ownerUsername: null` (also `holderName`, `holderEmail`, `currency`) | Accepted (`null` = absent) but the contract says `type: string` | Contract says `type: [string, 'null']` |
| 4 | `POST /api/customers` with wrong credentials | 401, undocumented | 401 documented in the contract (standard Spring Security behaviour) |
| 5 | Method `QUERY` | 400 with Spring's default JSON (rejected by Spring Security's `StrictHttpFirewall`) | 405 `ProblemDetail` with the `Allow` header, like any other undeclared method |
| 6 | `currency` | Contract accepts any `^[A-Z]{3}$`; a non-ISO code gives 400 and another currency 422, although every account is in EUR | Contract says `enum: [EUR]`; the Java behaviour does not change |

### Out of scope

- The contract-test job in the CI (#107).
- Changing the rounding rule of 2026-10-23 or making amounts with more than two decimals invalid.
- Web forms (`adapter.in.web`): they get the new limit through the commands, whose `IllegalArgumentException` the web controllers already catch; no `@DecimalMax` on the forms.
- Spring Boot's standard JSON for a path that does not exist (already documented in the contract).
- Bumping `info.version` (versioning policy, #112).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `application` | `CommandValidation.java` | New `MAX_AMOUNT` (999,999,999.99) and an amount check shared by movements and initial deposits (rounded half-up to the currency digits: positive and not above the maximum) |
| `application` | `DepositCommandDef`, `WithdrawCommandDef`, `TransferCommandDef` | Get the maximum through `requireValidMovement` (no code change beyond the shared check) |
| `application` | `OpenAccountCommandDef`, `SignUpCommandDef`, `OpenAnotherAccountCommandDef` | Initial deposit uses the shared check instead of `signum() <= 0` |
| `adapter.in.rest` | `MovementRequest`, `TransferRequest`, `OpenAccountRequest`, `SignUpRequest` | `@DecimalMin("0.005")` instead of `@Positive`, plus `@DecimalMax("999999999.99")` |
| `adapter.in.rest` | `ApiSecurityConfig` (or a `config` bean) | `StrictHttpFirewall` that also allows `QUERY`, so Spring MVC answers 405 |
| `docs` | `docs/api/openapi.yaml` | `minimum`/`maximum` and rounding note on amounts, nullable optional strings, `enum: [EUR]`, 401 on `signUp` |

No new DDD building block: the limit is a format check of the input, so it lives where the commands already "validate before calling the domain" (`decisiones.md`); `MonetaryAmount` and the aggregate do not change. No new dependency, no ArchUnit change.

## Tasks

### [x] T1 — Maximum amount in movement commands (~10 min)

- **Layer:** application
- **Files:** `src/main/java/com/pronoide/ddd/application/CommandValidation.java`, `src/test/java/com/pronoide/ddd/application/MovementCommandsTests.java`
- **Depends on:** —
- **What to do:**
  1. Test first (`@ParameterizedTest` + `@CsvSource`): a deposit, a withdrawal and a transfer of `999999999.99` are built; `1000000000.00` and `999999999.995` (rounds up to 1,000,000,000.00) throw `IllegalArgumentException` with a message in Spanish naming the maximum.
  2. Add `MAX_AMOUNT` and the check after rounding in `requireValidMovement`.
- **Acceptance criteria:**
  - [x] The new tests fail before the change and pass after it.
  - [x] Existing amount tests still pass (minimum unchanged).

### [x] T2 — Initial deposit uses the same amount check (~10 min)

- **Layer:** application
- **Files:** `CommandValidation.java`, `OpenAccountCommandDef.java`, `SignUpCommandDef.java`, `OpenAnotherAccountCommandDef.java`, `src/test/java/com/pronoide/ddd/application/OpenAccountCommandTests.java`
- **Depends on:** T1
- **What to do:**
  1. Test first: an opening, a sign-up and another-account command with an initial deposit of `0.004` (rounds to 0.00) or `1000000000.00` throw `IllegalArgumentException` in the command itself; `0.005` is built (the domain will then answer 422 for the minimum deposit, R1).
  2. Extract the amount part of `requireValidMovement` into a method used by the three initial-deposit commands instead of `signum() <= 0`.
- **Acceptance criteria:**
  - [x] `0.004` no longer reaches the domain (the command rejects it).
  - [x] `CheckingAccountService*Tests` still pass.

### [ ] T3 — REST movement and transfer DTOs validate the limits (~10 min)

- **Layer:** adapter.in
- **Files:** `MovementRequest.java`, `TransferRequest.java`, `AccountControllerTests.java`, `TransferControllerTests.java`
- **Depends on:** T1
- **What to do:**
  1. Tests first (`@WebMvcTest`, port mocked): `amount` `0.004` and `1000000000.00` on deposits, withdrawals and transfers answer 400 with the field in `errors`, and the port is never called.
  2. Replace `@Positive` with `@DecimalMin(value = "0.005")` and add `@DecimalMax("999999999.99")`, messages in Spanish.
- **Acceptance criteria:**
  - [ ] 400 `ValidationProblemDetail` with `errors.amount` for both limits.

### [ ] T4 — REST opening and sign-up DTOs validate the limits (~10 min)

- **Layer:** adapter.in
- **Files:** `OpenAccountRequest.java`, `SignUpRequest.java`, `AccountControllerTests.java`, `CustomerControllerTests.java`
- **Depends on:** T2
- **What to do:**
  1. Tests first: `initialDeposit` `0.004` and `1000000000.00` answer 400 with `errors.initialDeposit`.
  2. Same annotations as T3.
- **Acceptance criteria:**
  - [ ] 400 with `errors.initialDeposit` for both limits; the port is never called.

### [ ] T5 — Wrong credentials on the public sign-up are a 401 (~5 min)

- **Layer:** adapter.in
- **Files:** `CustomerControllerTests.java` (or the existing security test of `ApiSecurityConfig`)
- **Depends on:** —
- **What to do:**
  1. Characterisation test: `POST /api/customers` with a valid body and HTTP Basic credentials that do not exist answers 401 `ProblemDetail` ("No autenticado"); without credentials it reaches the port.
- **Acceptance criteria:**
  - [ ] The test fixes the current behaviour that T7 documents.

### [ ] T6 — Undeclared `QUERY` method answers 405 (~10 min)

- **Layer:** adapter.in
- **Files:** `ApiSecurityConfig.java`, its test
- **Depends on:** —
- **What to do:**
  1. Test first (`fix`: reproduce): `QUERY /api/accounts` with valid credentials answers 405 with the `Allow` header and a `ProblemDetail` body (today: 400).
  2. Declare a `StrictHttpFirewall` bean that allows the standard methods plus `QUERY` (Spring Security picks it up for every filter chain, so the web is covered too).
  3. Confirm the cause first: if the 400 does not come from the firewall, stop and update the plan.
- **Acceptance criteria:**
  - [ ] `curl -X QUERY -u admin:demo localhost:8080/api/accounts` → 405 with `Allow`.
  - [ ] `PUT`/`DELETE` still answer 405 as before.

### [ ] T7 — Update `openapi.yaml` (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`
- **Depends on:** T3, T4, T5, T6
- **What to do:**
  1. Every amount (`MovementRequest.amount`, `TransferRequest.amount`, `OpenAccountRequest.initialDeposit`, `SignUpRequest.initialDeposit`): `minimum: 0.005`, `maximum: 999999999.99` (instead of `exclusiveMinimum: 0`) and a description: rounded half-up to the currency's two decimals; below the minimum or above the maximum fails with 400.
  2. Optional strings that accept `null` (`holderName`, `holderEmail`, `ownerUsername`, every `currency`): `type: [string, 'null']`.
  3. Every request `currency`: `enum: [EUR]`, keeping `default: EUR`; description: another ISO currency fails with 422, an invalid code with 400.
  4. `signUp`: add the `401` response (`$ref: '#/components/responses/Unauthorized'`) with a description: only when wrong credentials are sent; no credentials are needed.
  5. `npx @redocly/cli@2.71.0 lint docs/api/openapi.yaml`.
- **Acceptance criteria:**
  - [ ] Redocly without errors.
  - [ ] The contract matches cases 1-6 of the goal.

### [ ] T8 — Verify with Schemathesis and update the project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T7
- **What to do:**
  1. Start the app with the `demo` profile and run `schemathesis run docs/api/openapi.yaml --url http://localhost:8080 --auth admin:demo` (version 4.33.4, outside the repository; it is not added to the build yet, that is #107).
  2. Record in `decisiones.md`: 999,999,999.99 technical limit in the commands; 401 on the public sign-up documented; `QUERY` allowed in the firewall; `enum: [EUR]`.
  3. Record in `pendientes.md` whatever Schemathesis still reports (see risks), for #107.
- **Acceptance criteria:**
  - [ ] No failures left of classes ST108, ST201, ST103 or ST204; any ST202 left is a 422 for a business rule (see risks).

## Global acceptance criteria

**Specific:**

- [ ] The six cases of the goal behave as the "Expected" column says.
- [ ] `0.001` is rejected by validation (adapter or command), never by the domain safety net, in all four amount fields.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: the maximum is a technical limit, not a business rule).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Business-rule 422 with schema-valid data:** Schemathesis' `positive_data_acceptance` check treats any 4XX other than 401/403/404/409/429 on valid data as a failure, but a 422 for a business rule (initial deposit below 50.00 EUR, transfer to the same account, duplicate document) is legitimate and cannot be expressed in the schema. This is not a discrepancy to fix here: #107 must declare 422 as an expected status for that check (declaring it is not disabling the check). Noted in `pendientes.md`.
- **Restricting the contract:** `maximum` rejects inputs that used to be accepted (incompatible change). `minimum: 0.005` and `enum: [EUR]` only describe what was already rejected. The version bump follows #112.
- **Float precision:** no `multipleOf: 0.01` on purpose: JSON Schema validators compute it with floats (`0.07 / 0.01 ≠ 7`) and would produce false failures, and the API rounds instead of rejecting extra decimals.
- **Firewall:** a custom `StrictHttpFirewall` also applies to the web chain; T6 keeps every other default of the firewall.
