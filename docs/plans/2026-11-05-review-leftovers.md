# Plan: Review leftovers — holder name test, own account as counterpart and the CLI segment label

- **Issue:** [#77](https://github.com/dawnrock/Proyect-DDD-Claude/issues/77)
- **Type:** refactor (one test for existing behaviour, one new domain guard, one label moved)
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-05
- **Status:** in progress
- **Total estimate:** ~40 min (5 tasks)

## Goal

Close three small items from `docs/ia/pendientes.md`:

1. **Holder name test.** `AccountHolder.register` and `rename` already reject a null or blank name, but no test proves it (the glossary lists it under "Rules implemented without a test").
2. **Own account as counterpart (#72 review).** `CheckingAccount.tryWithdrawForTransfer` and `depositFromTransfer` accept the account's own number as the counterpart. R17 is only checked in `MoneyTransferService`; the aggregate should protect itself too, with the same `SameAccountTransferException`.
3. **Segment label in the CLI (#72 review).** `DomainModelRunner` keeps its own segment labels while the web uses `WebFormat.segment`. The label moves to the support shared by the input adapters (root of `adapter.in`), which both may use without knowing each other.

What must not change: R17-R19 in `MoneyTransferService`, the web texts and the `[demo]` trace.

### Out of scope

- Transfer atomicity (#78) and the Graphify rule for methods (#79).
- Labels of other enumerations (transaction types and statuses) on the web: they stay in the templates.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `AccountHolderTests.java` | Tests for the mandatory holder name (existing behaviour) |
| `domain.model` | `CheckingAccount.java`, `CheckingAccountTests.java` | The two transfer methods reject the account's own number (`SameAccountTransferException`, R17) before recording anything |
| `adapter.in` (root) | `SegmentLabels.java` (new), `SegmentLabelsTests.java` (new) | Spanish label of a segment code, shared by the input adapters |
| `adapter.in.web`, `adapter.in.cli` | `WebFormat.java`, `DomainModelRunner.java` | Both delegate to `SegmentLabels` |
| docs | `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/*` | Rule table, class → role, status |

The root of `adapter.in` already holds the security support shared by REST and web (`SecuritySupport`, `CurrentCaller`). `HexagonalArchitectureTests` lets `cli`, `rest` and `web` use it and forbids the opposite (`el_soporte_de_adapter_in_no_conoce_a_los_adaptadores`), so no rule changes. `SegmentLabels` is a plain final class with a static method (no Spring), so the CLI tests keep building the runner without a context.

## Tasks

### [x] T1 — The holder name is mandatory (test) (~5 min)

- **Layer:** domain
- **Files:** `src/test/java/com/pronoide/ddd/domain/model/AccountHolderTests.java`
- **Depends on:** —
- **What to do:**
  1. Tests (characterization, they pass at once): `register` and `rename` with `null` throw `NullPointerException`, and with a blank name `IllegalArgumentException`; a failed `rename` keeps the previous name.
  2. Prove they can fail: remove the blank check from `AccountHolder.requireName` temporarily, see red, revert.
- **Acceptance criteria:**
  - [x] The tests pass and fail when the check is removed.

### [x] T2 — The aggregate rejects its own number as counterpart (~10 min)

- **Layer:** domain
- **Files:** `domain/model/CheckingAccount.java`, `CheckingAccountTests.java`
- **Depends on:** —
- **What to do:**
  1. Tests first: `tryWithdrawForTransfer` and `depositFromTransfer` with the account's own number throw `SameAccountTransferException`, record no transaction, change no balance and record no event.
  2. Check it before recording, in both methods. `MoneyTransferService` keeps its own R17 check (it compares source and target before touching either account).
- **Acceptance criteria:**
  - [x] New tests green; `MoneyTransferServiceTests` unchanged and green; `check-domain.mjs` without errors.

### [x] T3 — `SegmentLabels` in the input adapters' support (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/SegmentLabels.java`, `SegmentLabelsTests.java`, `adapter/in/web/WebFormat.java`
- **Depends on:** —
- **What to do:**
  1. Tests first: `RETAIL` → "Minorista", `HIGH_NET_WORTH` → "Alto patrimonio", an unknown code is shown as it is.
  2. `WebFormat.segment` delegates to it (`WebFormatTests` unchanged and green).
- **Acceptance criteria:**
  - [x] `SegmentLabelsTests` and `WebFormatTests` green; `HexagonalArchitectureTests` green.

### [ ] T4 — The CLI uses `SegmentLabels` (~5 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/cli/DomainModelRunner.java`
- **Depends on:** T3
- **What to do:**
  1. Remove the runner's own map and `segmentLabel`; use `SegmentLabels`. The trace tests of `DomainModelRunnerTests` act as characterization tests (same `[demo]` lines).
  2. Check the architecture rules: the CLI may use the support in the root of `adapter.in`, and the support still knows no adapter.
- **Acceptance criteria:**
  - [ ] `DomainModelRunnerTests` and `HexagonalArchitectureTests` green; no segment label left outside `SegmentLabels` (grep).

### [ ] T5 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/dominio/glosario.md`, `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1-T4
- **What to do:**
  1. Glossary: the holder-name rule moves from "Rules implemented without a test" to its tests (the section is removed if empty); R17 says the aggregate also rejects its own number in the transfer methods.
  2. `fase-2-hexagonal.md`: `SegmentLabels` in the row of the shared support of `adapter.in`; `WebFormat` row.
  3. Status: #77 done, next step `/plan-feature 78`; `pendientes.md` loses the three items; a row in `historial.md`.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] Test for the mandatory holder name; the glossary no longer lists it as a rule without a test.
- [ ] The aggregate rejects its own number as counterpart (test), R17 in the service unchanged.
- [ ] One place for the segment label in the input adapters; ArchUnit green.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no REST change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: no new terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Where the label lives:** the plan puts it in the root of `adapter.in`, next to the security support. The alternative is to leave each adapter with its own label and only document it; it is two lines of duplication, but the web and the CLI could drift apart.
- **Same exception in two places:** the aggregate and the domain service both throw `SameAccountTransferException`; the service checks first, so the message the user sees does not change.
