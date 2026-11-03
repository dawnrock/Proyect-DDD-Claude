# Plan: Account factory with numbering and income verification ports

- **Issue:** [#18](https://github.com/dawnrock/Proyect-DDD-Claude/issues/18)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-03
- **Status:** in progress
- **Total estimate:** ~135 min (15 tasks: T0 + 9 back + 2 web + 3 both)

## Goal

Encapsulate opening an account in a domain **factory** that needs external collaborators (Lab 25). `CheckingAccountFactory` asks the output port `AccountNumberSequence` for a new IBAN and the output port `IncomeVerifier` for the holder's verified annual income: only holders whose verified income is **above a threshold** are registered as high net worth. Opening stops being done with `CheckingAccount.open` from outside the domain.

This changes the open-account contract on both sides: the administrator no longer types the IBAN nor chooses the segment; instead the form and the REST body carry the holder's **tax id (NIF)**, and the response shows the generated IBAN and the computed segment. Implemented as a team (`/implement-plan-team 18`): contract first (ADR 0007), then back and web in parallel.

### Out of scope

- Validating the NIF check letter (only its format, see open questions).
- Validating the IBAN check digits in `AccountNumber` (the generator produces valid ones; existing tests use format-only IBANs).
- Storing the tax id in `AccountHolder` or showing it in the views: the factory only needs it to verify income.
- The CLI adapter `DomainModelRunner` (#19) and building the factory in `config` (#20): until then the service builds the factory from the two ports.
- Showing the segment in the accounts list (pending item from #48); the view gains the field, the list screen can use it later.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `TaxId.java` | New value object: Spanish NIF, 8 digits + letter |
| `domain.model` | `AccountNumberSequence.java`, `IncomeVerifier.java` | New output ports |
| `domain.model` | `CheckingAccountFactory.java` | New factory: number + segment rule (R20) + `CheckingAccount.open` |
| `domain.model` | `CheckingAccount.java` | `open` becomes package-private (only the factory and same-package tests use it) |
| `adapter.out.persistence` | `InMemoryAccountNumberSequence.java` | Simulated generator of valid Spanish IBANs (package-private) |
| `adapter.out.taxagency` | `FakeTaxAgencyIncomeVerifier.java` | Simulated tax agency with a fixed table (package-private) |
| `application` | `OpenAccountCommandDef.java`, `AccountSummaryDef.java` | Contract: command loses `accountNumber`/`segment`, gains `holderTaxId`; summary gains `segment` |
| `application` | `CheckingAccountService.java`, `DemoDataLoader.java` | Opens through the factory; duplicate number only as a safety net; demo accounts with NIFs |
| `adapter.in.rest` | `OpenAccountRequest.java`, `AccountSummaryResponseDef.java` | New body (incompatible change) and `segment` in the response |
| `adapter.in.web` | `OpenAccountForm.java`, `AccountWebController.java`, `templates/accounts/new.html` | New form: NIF instead of IBAN and segment; confirmation shows IBAN and segment |
| tests | `support/TestAccounts.java` + bridge in test `domain.model` | Build accounts without the factory |
| docs | `docs/contracts/account-factory.md`, `docs/api/openapi.yaml`, glossary, `fase-2-hexagonal.md`, `docs/ia/*` | Contract, API, terms, status |

Building blocks: **factory** (creation that needs collaborators; it validates what needs the outside world, the aggregate keeps protecting its own invariants), two **output ports** in `domain.model` and their **simulated output adapters**, and a **value object** (`TaxId`).

Domain rules:

- **R20 (new):** a holder is registered as `HIGH_NET_WORTH` only if the tax agency verifies an annual income **above 100,000.00 €**; otherwise (lower income or not verified) as `RETAIL`.
- **R16** stays: `DuplicateAccountNumberException` is now only a safety net against a faulty generator (the service checks the generated number before saving).

Simulated adapters (deterministic, documented in their Javadoc):

- `InMemoryAccountNumberSequence`: bank `2100`, branch `0418`, national check digits computed with the Spanish CCC algorithm, a 10-digit sequential account number and the IBAN check digits (ISO 13616, mod 97). Thread-safe.
- `FakeTaxAgencyIncomeVerifier`: fixed table for the demo holders (`87654321X` Grace Hopper → 250,000 €, `12345678Z` Ada Lovelace → 40,000 €, `11111111H` Alan Turing → 25,000 €); any other NIF is not verified.

**Contract transition (expand → contract).** Removing two required fields from `OpenAccountCommand` in T0 would break every caller before back and web have migrated. So T0 *expands* the contract: it adds `holderTaxId` and makes `accountNumber`/`segment` optional and deprecated (ignored once the back opens through the factory). Each side migrates in parallel, and T12 (`both`) *contracts* it by removing the two deprecated fields. `./mvnw verify` stays green at every step.

## Tasks

### [x] T0 — Contract (~15 min)

- **Side:** both
- **Layer:** application · docs
- **Files:** `docs/contracts/account-factory.md`, `OpenAccountCommandDef.java`, `AccountSummaryDef.java`, `CheckingAccountService.java` (only `toSummary`), test builders of `AccountSummary`, fragment of `docs/api/openapi.yaml`
- **Depends on:** —
- **What to do:**
  1. Write the contract from `contract-template.md`: `CheckingAccountCommands.openAccount(Caller, OpenAccountCommand)` → `AccountSummary`; command fields (`holderName`, `holderTaxId` — 8 digits + uppercase letter —, `initialDeposit`, `currency`, `ownerUsername`); view `AccountSummary` + `segment`; errors (`InsufficientInitialDepositException` 422, `DuplicateAccountNumberException` 422 as safety net, `ForbiddenOperationException` 403, invalid NIF → field error / 400); REST body and response; web screen `/accounts/new`; the expand → contract transition.
  2. Expand the types: `holderTaxId` (`@Value.Default ""` during the transition), `accountNumber` and `segment` optional and `@Deprecated`; `AccountSummary.segment` filled from the holder in `toSummary`; fix the test builders of `AccountSummary`.
  3. backend-dev and web-dev review and agree; freeze it in a commit.
- **Acceptance criteria:**
  - [x] Contract agreed and frozen in a commit; `./mvnw verify` green against it.

### [x] T1 — `TaxId` and the two output ports (~10 min)

- **Side:** back
- **Layer:** domain
- **Files:** `TaxId.java`, `AccountNumberSequence.java`, `IncomeVerifier.java`, `TaxIdTests.java`
- **Depends on:** T0
- **What to do:**
  1. Tests first: a NIF is normalized (spaces, lowercase) and rejected if it is not 8 digits + a letter.
  2. `record TaxId`; ports `AccountNumber next()` and `Optional<MonetaryAmount> verifiedAnnualIncome(TaxId)`.
- **Acceptance criteria:**
  - [x] `TaxIdTests` pass; `check-domain.mjs` and `DomainIsolationTests` green.

### [x] T2 — `CheckingAccountFactory` (~10 min)

- **Side:** back
- **Layer:** domain
- **Files:** `CheckingAccountFactory.java`, `CheckingAccountFactoryTests.java`
- **Depends on:** T1
- **What to do:**
  1. Tests first, with stubs (lambdas) of both ports: the account gets the generated number; income above the threshold → `HIGH_NET_WORTH`; exactly the threshold, lower or not verified → `RETAIL`; the aggregate's own rules still apply (R1 minimum deposit).
  2. `open(String holderName, TaxId taxId, MonetaryAmount initialDeposit)`: registers the holder with the computed segment and calls `CheckingAccount.open`.
- **Acceptance criteria:**
  - [x] Factory tests pass with stubs of the two ports (issue criterion).

### [x] T3 — `InMemoryAccountNumberSequence` (~10 min)

- **Side:** back
- **Layer:** adapter.out
- **Files:** `adapter/out/persistence/InMemoryAccountNumberSequence.java`, its test
- **Depends on:** T1
- **What to do:**
  1. Tests first: consecutive numbers are different, start with `ES`, have the bank and branch, and pass the IBAN mod-97 check and the CCC check digits.
  2. Package-private `@Component`, `AtomicLong` counter.
- **Acceptance criteria:**
  - [x] Generated IBANs are valid (mod 97 = 1) and unique.

### [x] T4 — `FakeTaxAgencyIncomeVerifier` (~5 min)

- **Side:** back
- **Layer:** adapter.out
- **Files:** `adapter/out/taxagency/FakeTaxAgencyIncomeVerifier.java`, its test
- **Depends on:** T1
- **What to do:**
  1. Tests first: the demo NIFs return their income; an unknown NIF is not verified.
  2. Package-private `@Component` with the fixed table; Javadoc says it is simulated.
- **Acceptance criteria:**
  - [x] Verifier test passes; the ArchUnit rules on `adapter.out..` cover the new package (adapters do not know each other).

### [x] T5 — The service opens through the factory (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `CheckingAccountService.java`, `CheckingAccountServiceTests.java`, `PersistenceTestAdapters.java`
- **Depends on:** T2, T3, T4
- **What to do:**
  1. Tests first: opening uses `holderTaxId`, the summary has a generated IBAN and the computed segment; a generated number already in use → `DuplicateAccountNumberException` (stubbed sequence); events and ownership as before.
  2. Explicit constructor that builds `CheckingAccountFactory` from the two new ports (moves to `config` in #20); with `holderTaxId`, `openAccount` uses the factory and ignores the deprecated fields; without it, it keeps the old path until T12 (contract, round 1).
- **Acceptance criteria:**
  - [x] Opening goes through the factory; the duplicate check is a safety net after generation.

### [ ] T6 — Remaining application tests and demo data (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `CheckingAccountServiceTests.java` (transfers, movements), `CheckingAccountServiceOwnershipTests.java`, `DemoDataLoader.java`, `DemoDataLoaderTests.java`
- **Depends on:** T5
- **What to do:**
  1. Tests that relied on a typed IBAN read it from the returned summary.
  2. `DemoDataLoader` opens the three demo accounts with their NIFs; Grace stays `HIGH_NET_WORTH` because of the tax agency, not because of a typed segment.
- **Acceptance criteria:**
  - [ ] `DemoDataLoaderTests` still finds `RETAIL`, `RETAIL`, `HIGH_NET_WORTH`; `./mvnw verify` green.

### [ ] T7 — `CheckingAccount.open` only inside the domain (~5 min)

- **Side:** back
- **Layer:** domain · tests
- **Files:** `CheckingAccount.java`, `support/TestAccounts.java`, test bridge `domain/model/TestCheckingAccounts.java`
- **Depends on:** T6
- **What to do:**
  1. `open` becomes package-private. `TestAccounts` keeps building accounts **without the factory** through a small public bridge in the test package `domain.model` (issue criterion).
- **Acceptance criteria:**
  - [ ] No class outside `domain.model` calls `CheckingAccount.open` (the compiler enforces it); all tests green.

### [ ] T8 — REST: new open-account body and segment in the response (~10 min)

- **Side:** back
- **Layer:** adapter.in
- **Files:** `OpenAccountRequest.java`, `AccountSummaryResponseDef.java`, `AccountController.java`, `AccountControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. Tests first (`@MockitoBean` of the ports): the body carries `holderTaxId` (field error if it is not 8 digits + letter) and no IBAN or segment; the response includes `segment`.
  2. Request record and mapping to the command.
- **Acceptance criteria:**
  - [ ] REST tests of the new open-account, including the computed segment in the response (issue criterion).

### [ ] T9 — OpenAPI (~5 min)

- **Side:** back
- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`
- **Depends on:** T8
- **What to do:**
  1. `OpenAccountRequest` without `accountNumber`/`segment` and with `holderTaxId`; `AccountSummary.segment`; note the incompatible change.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` without errors (issue criterion).

### [ ] T10 — Web: form and controller (~10 min)

- **Side:** web
- **Layer:** adapter.in
- **Files:** `OpenAccountForm.java`, `AccountWebController.java`, `AccountWebControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. Tests first (`@MockitoBean` of the ports): the form posts `holderTaxId` (field error if invalid) and builds the command without IBAN or segment; after opening, the confirmation message shows the generated IBAN and the computed segment ("Alto patrimonio" / "Minorista").
  2. Form and controller; remove the segment list from the model.
- **Acceptance criteria:**
  - [ ] Web tests of the new open-account, including the computed segment (issue criterion).

### [ ] T11 — Web: open-account template (~10 min)

- **Side:** web
- **Layer:** adapter.in
- **Files:** `templates/accounts/new.html`, `AccountWebControllerTests.java`
- **Depends on:** T10
- **What to do:**
  1. Test first: the page has a NIF field and no IBAN or segment fields.
  2. Template: NIF input with its error, a short note that the IBAN is generated and the segment is decided from the verified income.
- **Acceptance criteria:**
  - [ ] Rendered form matches the contract's screen.

### [ ] T12 — Contract the command (~5 min)

- **Side:** both
- **Layer:** application
- **Files:** `OpenAccountCommandDef.java`, `OpenAccountCommandTests.java`, `docs/contracts/account-factory.md`
- **Depends on:** T7, T9, T11
- **What to do:**
  1. Remove the deprecated `accountNumber`, `segment` and `SEGMENTS`, and the service's legacy path without NIF; `holderTaxId` becomes required with its format validation; update the command tests and mark the contract as final.
- **Acceptance criteria:**
  - [ ] Nobody references the removed fields; `./mvnw verify` green.

### [ ] T13 — End-to-end tests (~10 min)

- **Side:** both
- **Layer:** tests
- **Files:** `DddLabApplicationTests.java`, `DemoProfileTests.java`
- **Depends on:** T12
- **What to do:**
  1. With the real service: REST and web open-account with a NIF return a generated IBAN; `87654321X` gives `HIGH_NET_WORTH`, an unknown NIF gives `RETAIL`. Update the existing end-to-end tests that typed an IBAN.
- **Acceptance criteria:**
  - [ ] End-to-end tests pass with the real factory and adapters.

### [ ] T14 — Documentation and project status (~10 min)

- **Side:** both
- **Layer:** docs
- **Files:** `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T13
- **What to do:**
  1. Glossary: factory, NIF, numbering, income verification, R20, R16 as safety net.
  2. `fase-2-hexagonal.md`: "State after #18", rows for the new classes.
  3. Project status: #18 done, next step `/plan-feature 19`; decisions (threshold, unknown NIF → retail, factory built by the service until #20, expand → contract); pending items.
- **Acceptance criteria:**
  - [ ] New terms in the glossary; the CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] Factory tests with stubs of the two ports.
- [ ] Test builder updated to create accounts without the factory.
- [ ] Contract agreed and frozen in `docs/contracts/`.
- [ ] `docs/api/openapi.yaml` reflects the new open-account and `npx @redocly/cli lint` reports no errors.
- [ ] REST and web tests of the new open-account (including the computed segment).
- [ ] `CheckingAccount.open` is not called from outside `domain.model`.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **What identifies the holder for the tax agency:** the plan uses the Spanish **NIF** (8 digits + letter), validated by **format only**. Checking the control letter is easy in the domain, but then the form and the REST body would need the same check to avoid a 400 from the domain.
- **Threshold and unknown NIF:** high net worth only **above 100,000.00 €**; an NIF the tax agency does not know opens a **retail** account instead of being an error.
- **Expand → contract:** the command keeps the old fields (deprecated, ignored) from T0 to T12 so both sides can work in parallel with the build green; T12 removes them.
- **Incompatible REST change:** `POST /api/accounts` changes its body; there are no external clients (practice project), so no versioning.
- **Team run in the cloud:** `implement-plan-team` uses one worktree per side; if parallel agents are not available in this session, the lead runs the back and web tasks in order on the integration branch, keeping the frozen contract.
