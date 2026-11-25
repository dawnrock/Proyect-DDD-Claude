# Plan: CheckingAccount stops being a god class and reconstitute is simplified

- **Issue:** [#182](https://github.com/dawnrock/Proyect-DDD-Claude/issues/182)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-25
- **Status:** in progress
- **Total estimate:** ~60 min (6 tasks)

## Goal

The PMD baseline (#168, ADR 0020) flags the `CheckingAccount` aggregate as `GodClass` (WMC 72, ATFD 10, TCC 24.8 %) and `reconstitute` with `CyclomaticComplexity` 15. The aggregate keeps four responsibilities in one class; two of them are concepts of their own and move to package-private objects of `domain.model` (Clean Code ch. 10 and 19; DDD: model the concepts the rules talk about):

- **Transaction history** (`TransactionHistory`): the account's transactions in order, and the check of a stored account (no repeated transaction, all of this account, none pending, the balance equals the approved deposits minus the approved withdrawals).
- **Account segment** (`AccountSegment`): what the holder's segment means for the account: the overdraft it allows (R6-R8), the promotion to high net worth when the balance reaches the minimum net worth, and the segment review with its grace period and six-monthly income review (R22-R27).

`CheckingAccount` keeps the identity, the IBAN change (R32), the movements and the domain events, and delegates to both. The last entry of the PMD baseline goes away.

**What must not change:** the aggregate's public API (`open`, `reconstitute`, `deposit`, `tryWithdraw`, transfers, `segmentReviewDue`, `resolveSegmentReview`, `nextSegmentReview`, `transactions`, `pullDomainEvents`, `reassignAccountNumber`), its domain events and their order, the error messages and the order of the checks, rules R1-R32, persistence, the REST API and the web.

### Out of scope

- Any business rule change.
- `AccountHolder`, `Transaction` and the factory, beyond what the new objects call.
- `Monolithic` (frozen).

## Context

Measured with a throwaway prototype before writing this plan (`./mvnw compile pmd:pmd -Dpmd.baseline=`):

| Version | `CheckingAccount` | New classes |
| ------- | ----------------- | ----------- |
| Today | `GodClass` (WMC 72, ATFD 10, TCC 24.8 %); `reconstitute` complexity 15 | — |
| Only the history and a review-date value object | still `GodClass` (WMC 61, ATFD 9, TCC 26.6 %) | `restore` complexity 12 |
| **This plan:** history (with its check split in named steps) and the account segment | no warning | no warning |

With the prototype, the existing domain tests passed unchanged. PMD's `GodClass` needs WMC ≥ 47, ATFD > 5 and TCC < 1/3 at once; most foreign accesses were the transaction reads of `reconstitute` and the holder reads of the segment rules.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `TransactionHistory.java` (new, package-private) | Transactions in order; `restore(accountId, stored, balance)` with the stored-account check split in `requireStorable` and `approvedBalance` |
| `domain.model` | `AccountSegment.java` (new, package-private) | Holder + next review; `allows`, `afterDeposit`, `afterWithdrawal`, `afterOpening`, `reviewDue`, `resolveReview`, `nextReview`, `restore` |
| `domain.model` | `CheckingAccount.java` | Delegates to both; constants `OVERDRAFT_LIMIT`, `MINIMUM_NET_WORTH`, `SEGMENT_GRACE_PERIOD`, `INCOME_REVIEW_PERIOD` move to `AccountSegment` |
| tests | `TransactionHistoryTests`, `AccountSegmentTests` (new, POJO), `CheckingAccountReconstitutionTests` | Unit tests of the new objects; characterization of the reconstitution messages and order |
| build | `pmd-baseline.properties` | The `CheckingAccount` line out (the file stays, empty of entries, for future baselines) |
| docs | `glosario.md`, `fase-2-hexagonal.md`, `docs/ia/*` | New terms and classes, constants' new home, status |

Both new classes are package-private: only the aggregate uses them, so the hexagon's surface does not grow, and `DomainIsolationTests` and `check-domain.mjs` apply to them as to any domain class (no Spring, no Lombok, private state).

## Tasks

### [x] T1 — Characterization of the reconstitution (~5 min)

- **Layer:** tests
- **Files:** `CheckingAccountReconstitutionTests.java`
- **Depends on:** —
- **What to do:**
  1. The balance-mismatch test checks the whole message (today it only checks it contains `1250.00 EUR`).
  2. A retail account with a pending review **and** a transaction of another account reports the segment check first (today's order: required data, segment review, transactions, balance).
  3. Sabotage: change the order of two checks in `reconstitute`, see the red, revert.
- **Acceptance criteria:**
  - [x] Every reconstitution message and the order of the checks are fixed by tests; sabotage seen red and reverted.

### [x] T2 — TransactionHistory (~10 min)

- **Layer:** domain
- **Files:** `TransactionHistory.java`, `TransactionHistoryTests.java`
- **Depends on:** T1
- **What to do:**
  1. Tests first (POJO): a new history is empty; `add` keeps the order and returns the transaction; the list it gives is a copy; `restore` keeps a valid stored history and rejects, with today's messages, a repeated transaction, one of another account, a pending one, a `null` list and a balance that does not match the approved movements (rejected withdrawals do not count).
  2. Write it, with `restore` split in `requireStorable` and `approvedBalance` so no method is above PMD's threshold.
- **Acceptance criteria:**
  - [x] `TransactionHistoryTests` green; `check-domain.mjs` without errors.

### [ ] T3 — CheckingAccount on TransactionHistory (~5 min)

- **Layer:** domain
- **Files:** `CheckingAccount.java`
- **Depends on:** T2
- **What to do:**
  1. The aggregate keeps a `TransactionHistory`; `register`, `transactions()` and `reconstitute` use it. `reconstitute` keeps its order: required data, segment review, `TransactionHistory.restore`.
- **Acceptance criteria:**
  - [ ] All domain, persistence and end-to-end tests green without changes.

### [ ] T4 — AccountSegment (~15 min)

- **Layer:** domain
- **Files:** `AccountSegment.java`, `AccountSegmentTests.java`
- **Depends on:** T3
- **What to do:**
  1. Tests first (POJO), one per rule: overdraft allowed only to high net worth and down to -€1,000.00; a deposit that reaches the minimum net worth promotes a retail holder (answers `true` once) and cancels a pending review; a withdrawal that leaves a high net worth account below the minimum schedules the review one month later, and a second one does not move it; an opening below the minimum by a high net worth holder schedules the first review six months later; the review is due from its date on; resolving it keeps the segment and reschedules six months later, or demotes to retail (answers `true`); resolving before it is due, or without a moment, fails with today's messages; `restore` rejects a pending review for a retail holder.
  2. Write it, moving the four constants and `after` (calendar months in UTC) out of the aggregate.
- **Acceptance criteria:**
  - [ ] `AccountSegmentTests` green; `check-domain.mjs` without errors.

### [ ] T5 — CheckingAccount on AccountSegment (~10 min)

- **Layer:** domain
- **Files:** `CheckingAccount.java`
- **Depends on:** T4
- **What to do:**
  1. The aggregate keeps an `AccountSegment` built in its constructors (`restore` in `reconstitute`); deposits, withdrawals, the opening, `segmentReviewDue`, `resolveSegmentReview` and `nextSegmentReview` delegate to it, and the aggregate records `CustomerSegmentChanged` when it answers `true`. Event order unchanged (opening first, then the deposit, then the segment change).
- **Acceptance criteria:**
  - [ ] All tests green without changes (domain, `SegmentReviewServiceTests`, persistence and end-to-end).

### [ ] T6 — Out of the PMD baseline, glossary and project status (~10 min)

- **Layer:** build · docs
- **Files:** `pmd-baseline.properties`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T5
- **What to do:**
  1. Remove the `CheckingAccount` line and its comment; keep the file's header for future baselines. `./mvnw verify` green; sabotage: with today's `CheckingAccount` and no baseline, PMD fails.
  2. Glossary: «Transaction history» and «Account segment» rows; `OVERDRAFT_LIMIT`, `SEGMENT_GRACE_PERIOD` and the minimum net worth point at `AccountSegment`. Architecture: the two classes in the domain table.
  3. Status files: issue done, the PMD baseline pending item resolved (the baseline is empty), history row; next step OAuth / Auth0.
- **Acceptance criteria:**
  - [ ] `pmd-baseline.properties` has no entries and `./mvnw verify` is green; glossary and architecture up to date.

## Global acceptance criteria

**Specific:**

- [ ] `CheckingAccount` without `GodClass` or `CyclomaticComplexity`, and no new PMD warning in `domain.model`.
- [ ] Same public API, events, messages and order: the existing domain, persistence and end-to-end tests pass unchanged.
- [ ] The new objects have their own POJO tests and satisfy `DomainIsolationTests` and `check-domain.mjs`.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: … (n/a: no API change).
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **`AccountSegment` changes the holder:** promotion and demotion still go through `AccountHolder.promoteToHighNetWorth` / `demoteToRetail`, inside the aggregate's boundary; the aggregate stays the only entry point.
- **Mutable parts:** both objects hold state that changes with the account, so they are parts of the aggregate (like `AccountHolder`), not value objects; their equality is not used.
- **Unreachable check:** «Solo se revisa el segmento de un titular de alto patrimonio» cannot happen through the public API (a pending review always belongs to a high net worth holder); it stays as a defensive check, now testable in `AccountSegmentTests`.
- No open questions for the user: the names are internal (package-private) and the change is reversible.
