# Plan: High net worth by verified income on Monolithic

- **Issue:** [#99](https://github.com/dawnrock/Proyect-DDD-Claude/issues/99)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic` (frozen; `src/` exception authorised by the user on 2026-11-12 and 2026-11-13; the `v1-monolito` tag does not move)
- **Date:** 2026-11-13
- **Status:** in progress
- **Total estimate:** ~90 min (11 tasks)

## Goal

Make `Monolithic` decide the customer segment exactly like `Hexagonal` after #98 (PR #100), without changing its layered architecture (concrete classes, no ports, ADR 0004). Today `Monolithic` decides the segment only by balance (#94); after this plan it also asks a simulated tax agency for the holder's verified annual income, at opening and when a segment review is due. The holder needs a tax id (NIF) for that, which `Monolithic` does not have yet.

Target behaviour (the rule agreed for #98; both branches must behave the same):

1. **At opening:** the holder is high net worth if the verified income is above 100.000,00 €.
2. **Promotion by balance:** reaching a 100.000,00 € balance promotes the holder immediately, without asking the agency (already done in #94).
3. **Falling below the minimum:** an approved withdrawal that leaves a high-net-worth account below the minimum schedules a review one month later; reaching the minimum again cancels it.
4. **Review:** the income is verified again. Not high → back to retail. High → keeps the segment and the next review is six months later, as long as the balance stays below the minimum.
5. **High net worth by income with a balance below the minimum at opening:** first review six months after opening.

### Out of scope

- Customer sign-up, holder e-mail, document uniqueness per customer and bank-generated IBAN (#95). The administrator keeps typing the account number in this plan.
- Domain events (`CustomerSegmentChanged`) and customer notifications: they exist only on `Hexagonal`.
- The demo movements that `Hexagonal`'s `DomainModelRunner` performs after opening (deposit, withdrawals, transfer); here only the opened accounts are aligned.
- Showing the holder's tax id or the next review date in the account views.
- Agency failures (timeouts, errors): the simulated agency answers "unverified" for unknown tax ids, same as `Hexagonal`.

## Context

What `Hexagonal` does (reused names, see PR #100): `TaxId` (value object, 8 digits + letter, normalised), `HighNetWorthIncome.isMetBy(Optional<MonetaryAmount>)` (threshold above 100.000,00 €), `AccountHolder.register(fullName, taxId, segment)` with `promoteToHighNetWorth`/`demoteToRetail` package-private, and `CheckingAccount` with `nextSegmentReview`, `segmentReviewDue(now)` and `resolveSegmentReview(now, incomeStillHigh)`. The agency is the output port `IncomeVerifier`, implemented by `FakeTaxAgencyIncomeVerifier`, and consulted by `CheckingAccountFactory` (opening) and the domain service `SegmentReviewService` (review).

What changes on `Monolithic`, and why it differs from `Hexagonal`:

- **No port, so the domain cannot call the agency.** `domain` knows nobody (rule D1), so the domain receives the *result* of the verification instead: the application service asks the agency and passes the segment to `AccountHolder.register(...)` and `incomeStillHigh` to `CheckingAccount.resolveSegmentReview(...)`. There is no `CheckingAccountFactory` and no `SegmentReviewService` here: their orchestration ("only ask the agency when the review is due") becomes a private method of `CheckingAccountService`. The decision itself (threshold, periods, what happens on each result) stays in the domain.
- **Where the simulated agency lives (decision to confirm):** a new `integration` layer, package `com.pronoide.ddd.integration`, sibling of `persistence`: only `application` may use it and it only depends on `java..` and `domain..`, like `persistence`. Class `TaxAgencyClient` (concrete, `@Component`, no interface), same demo table as `Hexagonal`. Recorded in ADR 0011 and enforced by `LayeredArchitectureTests`. Rejected alternatives: inside `persistence` (it is not storage; it would blur the layer's meaning) and inside `application` (mixes an external system with use cases; it would be the only infrastructure class there).
- **Time stays as in #94:** `CheckingAccountService` takes `clock.instant()` and passes it to the aggregate. The review before a debit is saved even if the debit then fails (the `Hexagonal` fix in commit `63cbb95`): here that means saving the account right after a review that changed something.
- **Demo data (same as `Hexagonal`):** Ada Lovelace `12345678Z` (income 40.000,00 €, deposit 1.000,00 €), Grace Hopper `87654321X` (250.000,00 €, deposit 5.000,00 €; high net worth by income, first review in six months), Alan Turing `11111111H` (25.000,00 €, deposit 250,00 €). Grace's deposit drops from 150.000,00 € to 5.000,00 €, as on `Hexagonal`.
- **Business rules:** on `Monolithic` the free numbers start at R24. R21 and R22 are rewritten; R24-R26 are new.
- **Relation with #95 (recommendation: #99 first, not merged).** Both add the holder's tax id. This plan introduces `TaxId` and `AccountHolder.taxId()` with `Hexagonal`'s names; #95 then reuses `TaxId` as the customer document and adds what is specific to sign-up (uniqueness per customer, e-mail, generated IBAN with `AccountNumberSequence`). Merging both would mix a domain rule with a large back + web team feature in one PR; doing #99 first keeps #95's plan smaller and avoids modelling the document twice.
- **Not a team plan.** The web side is one field in the opening form (T3), so it is implemented with `/implement-plan 99`. T10 amends the contract document (ADR 0007).
- **REST:** `holderTaxId` becomes required in `POST /api/accounts` (incompatible change, like on `Hexagonal`). `accountNumber` stays until #95.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `TaxId.java` (new) | Value object copied from `Hexagonal` |
| `domain.model` | `HighNetWorthIncome.java` (new) | Income threshold, copied from `Hexagonal` |
| `domain.model` | `AccountHolder.java` | `register(fullName, taxId, segment)`, `taxId()`, promote/demote package-private |
| `domain.model` | `CheckingAccount.java` | `nextSegmentReview`, `segmentReviewDue`, `resolveSegmentReview`, six-month review; removes `reviewSegment` and `belowMinimumNetWorthSince` |
| `integration` (new layer) | `TaxAgencyClient.java` (new) | Simulated tax agency with the demo table |
| `application` | `OpenAccountCommandDef.java` | `holderTaxId` with `TAX_ID_PATTERN` |
| `application` | `CheckingAccountService.java` | Segment at opening from the agency; review helper that asks the agency only when due |
| `application` | `DemoDataLoader.java` | Tax ids and deposits of `Hexagonal` |
| `presentation.rest` | `OpenAccountRequest.java`, `AccountController.java` | `holderTaxId` |
| `presentation.web` | `OpenAccountForm.java`, `AccountWebController.java`, `templates/accounts/new.html` | NIF field |
| tests | `LayeredArchitectureTests.java`, `support/TestAccounts.java` and the affected test classes | New layer and rules; holders with a tax id |
| docs | `docs/adr/0011-integration-layer.md` (new), `docs/arquitectura/fase-1-capas.md`, `docs/api/openapi.yaml`, `docs/contracts/open-query-list-accounts.md`, `docs/dominio/glosario.md`, `docs/ia/*` | See T9-T11 |

## Tasks

### [x] T1 — `TaxId` value object (~5 min)

- **Layer:** domain
- **Files:** `domain/model/TaxId.java`, `domain/model/TaxIdTests.java`
- **Depends on:** —
- **What to do:**
  1. Copy `TaxId` from `Hexagonal` (record, normalises spaces and case, 8 digits + letter, control letter not checked) and its tests.
- **Acceptance criteria:**
  - [x] `TaxIdTests` passes: `" 87654321x "` normalises to `87654321X`; null and wrong formats throw with the Spanish messages.

### [x] T2 — `HighNetWorthIncome` (~5 min)

- **Layer:** domain
- **Files:** `domain/model/HighNetWorthIncome.java`, `domain/model/HighNetWorthIncomeTests.java`
- **Depends on:** —
- **What to do:**
  1. Copy `HighNetWorthIncome.isMetBy(Optional<MonetaryAmount>)` from `Hexagonal`, citing R24 instead of R20.
- **Acceptance criteria:**
  - [x] Tests pass: 100.000,01 € → true; exactly 100.000,00 €, less, or empty → false; null → `NullPointerException`.

### [x] T3 — Holder's tax id from the form and the REST body to the command (~10 min)

- **Layer:** application · presentation (mechanical, end to end, so the build stays green)
- **Files:** `application/OpenAccountCommandDef.java`, `application/DemoDataLoader.java`, `presentation/rest/OpenAccountRequest.java`, `presentation/rest/AccountController.java`, `presentation/web/OpenAccountForm.java`, `presentation/web/AccountWebController.java`, `templates/accounts/new.html`, and the tests that build these objects
- **Depends on:** —
- **What to do:**
  1. Add `holderTaxId` to `OpenAccountCommandDef` with `TAX_ID_PATTERN = "^ *\\d{8} *[A-Za-z] *$"` and the message "El NIF del titular debe tener 8 dígitos y una letra", as on `Hexagonal`.
  2. Add the same field and pattern to `OpenAccountRequest` and `OpenAccountForm`, map it in both controllers, and add the "NIF del titular" input to `new.html` (as on `Hexagonal`).
  3. Give the demo accounts the `Hexagonal` tax ids (deposits change in T8). The service does not use the field yet.
- **Acceptance criteria:**
  - [x] `OpenAccountCommandTests`: missing or malformed tax id is rejected; `" 12345678z "` is accepted.
  - [x] REST and web tests: a request without `holderTaxId` gets 400 / the form shows the field error.

### [x] T4 — `AccountHolder` with tax id and given segment (~10 min)

- **Layer:** domain
- **Files:** `domain/model/AccountHolder.java`, `AccountHolderTests.java`, `support/TestAccounts.java`, `application/CheckingAccountService.java` (call site)
- **Depends on:** T1, T3
- **What to do:**
  1. `register(fullName, taxId, segment)` as on `Hexagonal`, `taxId()` accessor; `promoteToHighNetWorth`/`demoteToRetail` become package-private (only the account decides).
  2. `TestAccounts` registers high-net-worth holders through the segment argument instead of calling `promoteToHighNetWorth()`; default tax id `12345678Z`.
  3. The service calls `register(name, new TaxId(command.holderTaxId()), RETAIL)` for now (T7 asks the agency).
- **Acceptance criteria:**
  - [x] `AccountHolderTests`: keeps the tax id and the given segment; null tax id or segment throws; equality by id still holds.
  - [x] The test for the old R21 ("se registra siempre como minorista") is replaced.

### [x] T5 — Segment review in the aggregate (~10 min)

- **Layer:** domain
- **Files:** `domain/model/CheckingAccount.java`, `CheckingAccountTests.java`, `application/CheckingAccountService.java` (call sites)
- **Depends on:** T4
- **What to do:**
  1. Replace `belowMinimumNetWorthSince` and `reviewSegment(now)` with `nextSegmentReview`, `segmentReviewDue(now)`, `resolveSegmentReview(now, incomeStillHigh)` and `nextSegmentReview()`, as on `Hexagonal` (`SEGMENT_GRACE_PERIOD` one month, `INCOME_REVIEW_PERIOD` six months, calendar months in UTC).
  2. At opening, a high-net-worth holder whose balance is below the minimum gets the first review six months after the opening deposit (R26).
  3. In the service, keep today's behaviour until T7: `if (account.segmentReviewDue(now)) account.resolveSegmentReview(now, false)`.
- **Acceptance criteria:**
  - [x] Tests for R22, R23, R25 and R26: review one month after the withdrawal; not due one second before; recovering the minimum cancels it; with high income the next review is six months later; without it the holder is retail and no review remains.
  - [x] `resolveSegmentReview` before it is due, or for a retail holder, throws `IllegalStateException`; a null `now` throws `NullPointerException`.

### [x] T6 — `integration` layer with the simulated tax agency (~10 min)

- **Layer:** integration · tests
- **Files:** `integration/TaxAgencyClient.java`, `integration/TaxAgencyClientTests.java`, `LayeredArchitectureTests.java`
- **Depends on:** T1
- **What to do:**
  1. `TaxAgencyClient` (`@Component`, concrete class) with `Optional<MonetaryAmount> verifiedAnnualIncome(TaxId)` and the `Hexagonal` table (`87654321X` 250.000,00 €, `12345678Z` 40.000,00 €, `11111111H` 25.000,00 €; anything else empty).
  2. Add the `Integration` layer to the layered rule (only accessed by `Application`), a rule "integration only depends on `java..` and `com.pronoide.ddd.domain..`" (plus Spring stereotypes) and extend "no interfaces" to it.
  3. Validate the new rules by breaking them temporarily (a controller using `TaxAgencyClient`; an interface in `integration`) and revert.
- **Acceptance criteria:**
  - [x] `TaxAgencyClientTests` passes for the three demo tax ids, an unknown one and null.
  - [x] `LayeredArchitectureTests` fails with each deliberate break and passes after reverting.

### [x] T7 — The service asks the agency at opening and at review (~10 min)

- **Layer:** application
- **Files:** `application/CheckingAccountService.java`, `CheckingAccountServiceTests.java`, `SegmentReviewJobTests.java`
- **Depends on:** T2, T5, T6
- **What to do:**
  1. `openAccount`: segment = `HIGH_NET_WORTH` if `HighNetWorthIncome.isMetBy(taxAgency.verifiedAnnualIncome(taxId))`, else `RETAIL`.
  2. Private `reviewSegment(account)`: if `account.segmentReviewDue(clock.instant())`, verify the holder's income and call `resolveSegmentReview`; save the account right away. Used before `withdraw`, before `transfer` (source) and by `reviewSegments()`, which keeps returning how many holders went back to retail.
  3. Javadoc of the service and of `SegmentReviewJob` updated to the new rule.
- **Acceptance criteria:**
  - [x] Opening with `87654321X` and 5.000,00 € gives a high-net-worth holder with a review six months later; `12345678Z` with 1.000,00 € gives retail; `12345678Z` with 150.000,00 € gives high net worth by balance.
  - [x] With a clock moved forward: the agency is not asked when no review is due; Ada (low income) goes back to retail after a month; Grace (high income) keeps the segment and gets the next review six months later.

### [ ] T8 — Demo data aligned with `Hexagonal` (~5 min)

- **Layer:** application
- **Files:** `application/DemoDataLoader.java`, `DemoDataLoaderTests.java`
- **Depends on:** T7
- **What to do:**
  1. Deposits of `Hexagonal`: Ada 1.000,00 €, Grace 5.000,00 €, Alan 250,00 €. Javadoc explains that Grace is high net worth by verified income.
- **Acceptance criteria:**
  - [ ] `DemoDataLoaderTests`: Grace is `HIGH_NET_WORTH` with 5.000,00 €; Ada and Alan are `RETAIL`.

### [ ] T9 — ADR 0011 and phase 1 architecture (~5 min)

- **Layer:** docs
- **Files:** `docs/adr/0011-integration-layer.md`, `docs/adr/README.md`, `docs/arquitectura/fase-1-capas.md`
- **Depends on:** T6
- **What to do:**
  1. ADR 0011: why the simulated agency lives in a new `integration` layer, the rejected alternatives and how it maps to `adapter.out.taxagency` on `Hexagonal`.
  2. Add the layer to the phase 1 diagram/text (grey scale; shown to the user before committing).
- **Acceptance criteria:**
  - [ ] ADR listed in the README; the phase 1 document names the four layers' allowed dependencies.

### [ ] T10 — OpenAPI and contract (~5 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/contracts/open-query-list-accounts.md`
- **Depends on:** T3
- **What to do:**
  1. `holderTaxId` required in `OpenAccountRequest`, with the pattern and example `87654321X`; describe the incompatible change and that the segment is decided by verified income and balance.
  2. Amend the contract (ADR 0007): new command field and validation.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.

### [ ] T11 — Glossary and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/dominio/glosario.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1-T10
- **What to do:**
  1. Glossary: tax id (`TaxId`), verified annual income, tax agency (`TaxAgencyClient`), income review period, next segment review (replaces `belowMinimumNetWorthSince`). Rewrite R21 (the segment at opening comes from verified income or the opening balance) and R22 (the review checks the income again); add R24 (income above 100.000,00 € at opening), R25 (high income at review keeps the segment, next review in six months) and R26 (first review six months after opening).
  2. Status: #99 done, next step #95 (re-planned on top of this); decision about the `integration` layer; pending items updated (the in-memory review dates and the double time source still apply).
- **Acceptance criteria:**
  - [ ] Every rule R21-R26 cites a test that exists.

## Global acceptance criteria

**Specific:**

- [ ] The five points of the agreed rule behave as on `Hexagonal`, each one proven by a test.
- [ ] The agency is consulted only at opening and when a review is due, never on a promotion by balance.
- [ ] No interfaces were added: the layers still use concrete classes (ADR 0004), checked by `LayeredArchitectureTests`.
- [ ] `./mvnw spring-boot:run` opens the three demo accounts; Grace Hopper shows as high net worth with 5.000,00 €.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (layers: each layer only uses the one below), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Layer of the tax agency (needs the user's confirmation):** new `integration` layer (recommended) versus inside `persistence`. The rest of the plan does not change with the choice, only T6 and T9.
- **Order with #95:** recommended #99 first and separate; #95's plan is rewritten on top of it (`TaxId` already on the holder).
- **Grace Hopper's demo deposit** drops from 150.000,00 € to 5.000,00 € to match `Hexagonal`: the demo stops showing a promotion by balance at opening.
- **Incompatible REST change:** clients of `POST /api/accounts` must send `holderTaxId`.
- Pending warnings that still apply (`pendientes.md`): review dates live in memory and are lost on restart; `Transaction.occurredOn` uses `Instant.now()` and the service uses the `Clock`.
