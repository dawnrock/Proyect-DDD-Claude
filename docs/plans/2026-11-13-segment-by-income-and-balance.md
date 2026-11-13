# Plan: Customer segment by verified income and by balance

- **Issue:** [#98](https://github.com/dawnrock/Proyect-DDD-Claude/issues/98)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-13
- **Status:** draft
- **Total estimate:** ~115 min (13 tasks)

## Goal

`Hexagonal` already decides the segment when the account is opened, from the income verified at the tax agency (R20). `Monolithic` now decides it from the account balance, in both directions (#94, PR #97). The user decided to combine both rules and keep the hexagonal structure: the `IncomeVerifier` port and its adapter stay, and the balance rule is added on top. The generated IBAN also takes the entity code of Mon-A Bank (`9999`, branch `0001`). `Monolithic` will be aligned with this behaviour in #99.

### Out of scope

- Customer sign-up, holder document stored in the holder, e-mail (#95 and its twin).
- Verifying the income again after the account is opened.
- Adding up several accounts of the same person.
- Aligning `Monolithic` (#99).

## Context

`CheckingAccountFactory.open` asks `IncomeVerifier` and registers the holder with its segment. Every credit goes through the private `CheckingAccount.deposit(Transaction)` (plain deposit, incoming transfer and opening deposit) and every debit through the private `tryWithdraw(Transaction)`, so the balance rules live there. The aggregate already records domain events, which the service publishes after the unit of work.

Combined rule (rule 4 is a proposal, to be confirmed on review):

1. Opening: high net worth if the verified annual income is above 100.000,00 € (R20, unchanged).
2. Any approved credit that takes the balance to 100.000,00 € promotes the holder at once (R22, R23).
3. A holder who is high net worth **by balance** and stays one calendar month (UTC) below the minimum goes back to retail (R24); reaching the minimum again cancels the countdown (R25).
4. A holder who is high net worth **by verified income** never goes back to retail because of the balance (R26).

Decisions taken in this plan:

- **The holder remembers why.** `AccountHolder` keeps whether its income was verified above the threshold (`hasVerifiedHighIncome()`), set by the factory. Only holders without it get a countdown.
- **Time.** The domain receives the instant (`reviewSegment(Instant now)`); the service takes it from a `java.time.Clock` bean defined in `config`. No new port: `Clock` is a JDK type.
- **Who triggers the review.** The service, before every withdrawal and outgoing transfer; and a daily scheduled task, which is a new input adapter (`adapter.in.scheduler`) calling a new method of the input port `CheckingAccountCommands`.
- **Domain event** `CustomerSegmentChanged` (account, holder name, new segment, instant), recorded by the aggregate on every promotion and demotion; the audit and customer-notification listeners handle it.
- **No input changes:** the segment is not an input here, so forms and requests stay as they are. It is not a team plan: `/implement-plan 98`.
- **Rule numbers** differ between branches: here R22-R26; on `Monolithic` the balance rules are R20-R23.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `CheckingAccount.java`, `AccountHolder.java`, `CheckingAccountFactory.java`, new `CustomerSegmentChanged.java` | Balance rules, grace period, event, verified-income flag |
| `application` | `CheckingAccountCommands.java`, `CheckingAccountService.java`, `listener/*` | `reviewSegments()`, review before debits, `Clock`, listeners |
| `adapter.in.scheduler` | new `SegmentReviewScheduler.java` | Daily task calling the input port |
| `adapter.out.persistence` | `InMemoryAccountNumberSequence.java` | Entity `9999`, branch `0001` |
| `config` | `DomainConfiguration.java` | `Clock` bean, scheduling |
| tests | `HexagonalArchitectureTests.java` and the tests of each task | New adapter in the onion rule |
| docs | `api/openapi.yaml`, `contracts/account-factory.md`, `dominio/glosario.md`, `arquitectura/fase-2-hexagonal.md`, `arquitectura/comparativa-capas-hexagonal.md`, `analysis/functional-analysis.md`, `README.md`, `ia/*` | Rules, adapter, IBAN prefix, status |

## Tasks

### [ ] T1 — The aggregate promotes the holder at the minimum net worth (~10 min)

- **Layer:** domain
- **Files:** `domain/model/CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** —
- **What to do:**
  1. Failing tests first: a deposit that takes the balance to exactly 100.000,00 € promotes the holder (R22); one cent less does not; an account opened by a retail holder with 100.000,00 € is high net worth from the start (R23).
  2. Add `MINIMUM_NET_WORTH` and promote in the private `deposit(Transaction)` after updating the balance.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=CheckingAccountTests` passes; `./mvnw verify` green.

### [ ] T2 — An incoming transfer can promote the target holder (~5 min)

- **Layer:** domain (test only)
- **Files:** `MoneyTransferServiceTests.java`
- **Depends on:** T1
- **What to do:** a retail target with 99.900,00 € receives 100,00 € and becomes high net worth; a rejected transfer promotes nobody. Validate by breaking the promotion temporarily.
- **Acceptance criteria:**
  - [ ] The two tests pass and fail when the promotion is removed.

### [ ] T3 — The holder remembers a verified high income (~10 min)

- **Layer:** domain
- **Files:** `AccountHolder.java`, `CheckingAccountFactory.java`, `AccountHolderTests.java`, `CheckingAccountFactoryTests.java`
- **Depends on:** —
- **What to do:**
  1. Failing tests first: the factory registers a holder with income above the threshold as high net worth with `hasVerifiedHighIncome()` true; with lower, equal or unverified income, retail and false.
  2. Replace `register(fullName, segment)` with a factory method that takes the verification result; keep R20 in `CheckingAccountFactory`. Add `demoteToRetail()`, rejected for a holder with verified high income.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest='AccountHolderTests,CheckingAccountFactoryTests'` passes; `./mvnw verify` green.

### [ ] T4 — The account remembers since when it is below the minimum (~10 min)

- **Layer:** domain
- **Files:** `CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** T1, T3
- **What to do:**
  1. Failing tests first: an approved withdrawal that leaves a holder who is high net worth by balance below the minimum records `belowMinimumNetWorthSince` (the withdrawal's `occurredOn`); a second withdrawal does not move it; a credit that reaches the minimum clears it; a rejected withdrawal, a retail holder and a holder with verified high income record nothing (R26).
  2. Add the field and the accessor (`Optional<Instant>`).
- **Acceptance criteria:**
  - [ ] The six tests pass; `./mvnw verify` green.

### [ ] T5 — Back to retail after one month below the minimum (~10 min)

- **Layer:** domain
- **Files:** `CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** T4
- **What to do:**
  1. Failing tests first: one second before the month the holder keeps the segment; at one month it is retail and the date is cleared (R24); recovering the minimum in between cancels it (R25); reviewing a retail account does nothing; a holder demoted in overdraft keeps the debt and its next withdrawal is rejected; after a demotion, reaching the minimum promotes again; `reviewSegment(null)` is rejected.
  2. Add `reviewSegment(Instant now)` with `SEGMENT_GRACE_PERIOD` (one calendar month, UTC).
- **Acceptance criteria:**
  - [ ] The seven tests pass; `check-domain.mjs` without errors.

### [ ] T6 — Domain event `CustomerSegmentChanged` (~10 min)

- **Layer:** domain
- **Files:** new `domain/model/CustomerSegmentChanged.java`, `CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** T5
- **What to do:**
  1. Failing tests first: a promotion and a demotion each record one event with the account, the holder name, the new segment and the instant; a credit to a holder who is already high net worth records none.
  2. Record the event only when the segment really changes.
- **Acceptance criteria:**
  - [ ] The three tests pass; the existing event tests stay green.

### [ ] T7 — The listeners handle the segment change (~5 min)

- **Layer:** application
- **Files:** `application/listener/AuditLogListener.java`, `CustomerNotificationListener.java` + tests
- **Depends on:** T6
- **What to do:** one `@EventListener` in each; tests in the style of the existing listener tests.
- **Acceptance criteria:**
  - [ ] The listener tests pass; `el_nucleo_no_depende_de_spring` stays green.

### [ ] T8 — The service reviews the segment before every debit (~10 min)

- **Layer:** application · config
- **Files:** `CheckingAccountService.java`, `config/DomainConfiguration.java`, service tests, `DomainConfigurationTests.java`
- **Depends on:** T5
- **What to do:**
  1. Failing tests first, with a clock that can be moved forward: after more than a month below the minimum, an overdraft withdrawal is rejected and the detail shows `RETAIL`; the same for the source of a transfer; before the month it is still approved.
  2. Inject `Clock` (bean in `config`); call `reviewSegment(clock.instant())` before the withdrawal and before the transfer, inside the unit of work; the event is published with the others.
- **Acceptance criteria:**
  - [ ] The three tests pass; `./mvnw verify` green.

### [ ] T9 — Input port `reviewSegments()` (~10 min)

- **Layer:** application
- **Files:** `CheckingAccountCommands.java`, `CheckingAccountService.java` + tests
- **Depends on:** T8
- **What to do:**
  1. Failing tests first: after a month, only the holder still below the minimum is demoted, the count is returned and one `CustomerSegmentChanged` is published; before the month, nothing changes.
  2. Add the method to the input port (no `Caller`: internal process) and implement it in one unit of work.
- **Acceptance criteria:**
  - [ ] The two tests pass; `los_puertos_de_entrada_son_interfaces` stays green.

### [ ] T10 — Scheduler input adapter (~10 min)

- **Layer:** adapter.in · config · tests
- **Files:** new `adapter/in/scheduler/SegmentReviewScheduler.java` + test, `config` (`@EnableScheduling`), `HexagonalArchitectureTests.java`
- **Depends on:** T9
- **What to do:**
  1. Test first: the scheduler calls `CheckingAccountCommands.reviewSegments()` (port mocked) and logs the count.
  2. `@Scheduled(cron = "0 0 3 * * *")`; register the adapter `scheduler` in `las_dependencias_apuntan_hacia_el_hexagono` and validate the rule by breaking it (make the scheduler depend on `CheckingAccountService`) and restoring it.
- **Acceptance criteria:**
  - [ ] The ArchUnit rule fails when broken and passes when restored; the application starts without scheduling errors.

### [ ] T11 — Mon-A Bank entity code in generated IBANs (~10 min)

- **Layer:** adapter.out
- **Files:** `adapter/out/persistence/InMemoryAccountNumberSequence.java`, `InMemoryAccountNumberSequenceTests.java`, tests and demo output that assert generated IBANs
- **Depends on:** —
- **What to do:**
  1. Failing test first: the first IBAN has entity `9999` and branch `0001`, valid Spanish control digits and a valid mod-97 check.
  2. Change the two constants; update the tests that compare against the old generated values (the IBANs typed by hand in other tests are arbitrary and stay).
- **Acceptance criteria:**
  - [ ] `./mvnw verify` green; with the `demo` profile the three accounts start with `ES` + 2 digits + `99990001`.

### [ ] T12 — Documentation (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/contracts/account-factory.md`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/arquitectura/comparativa-capas-hexagonal.md`, `docs/analysis/functional-analysis.md`, `README.md`
- **Depends on:** T10, T11
- **What to do:** segment description in the details response and example IBANs; contract amendment; terms (minimum net worth, grace period, segment review, `CustomerSegmentChanged`, Mon-A Bank) and rules R22-R26 with their tests; the scheduler adapter and the `Clock` in the architecture documents; demo IBANs.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` without errors; every rule R22-R26 names an existing test.

### [ ] T13 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T12
- **What to do:** status and next step (#99, then #95); decision (combined rule, who triggers the review); pending warnings (two time sources, countdowns lost on restart).
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] A holder with verified income above 100.000,00 € opens as high net worth and never goes back to retail.
- [ ] A retail holder becomes high net worth when the balance reaches 100.000,00 € (opening, deposit or incoming transfer) and goes back to retail after one month below it, unless the balance recovers.
- [ ] Every segment change is audited and notified through `CustomerSegmentChanged`.
- [ ] Generated IBANs carry entity `9999` and branch `0001`.
- [ ] The `IncomeVerifier` port and its adapter are still in use.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones.
- [ ] `docs/api/openapi.yaml` reflects the change and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Rule 4 (to confirm):** a holder with verified high income never loses the segment. The alternative (income only counts at opening, and afterwards everybody follows the balance) is simpler but would demote Grace Hopper one month after her first withdrawal unless her deposit is raised.
- **Demo data:** with rule 4 the demo deposits stay as they are (Grace Hopper qualifies by income).
- **Entity code `9999`:** invented; not checked against the Banco de España registry.
- **Two time sources:** `Transaction.occurredOn` uses `Instant.now()` and the review uses the `Clock` bean, as on `Monolithic`; no end-of-month test is possible until movements can be dated.
- **Countdowns in memory:** a restart loses them.
- **Behaviour differs from `Monolithic` until #99** is done: there the income rule does not exist yet.
