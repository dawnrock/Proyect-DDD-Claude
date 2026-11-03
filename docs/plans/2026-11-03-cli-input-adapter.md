# Plan: Command-line input adapter

- **Issue:** [#19](https://github.com/dawnrock/Proyect-DDD-Claude/issues/19)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-03
- **Status:** in progress
- **Total estimate:** ~60 min (7 tasks; T4-T6 added on 2026-11-03 at the user's request)

## Goal

Show that the hexagon is used the same way from several adapters: the demo data runner becomes a **command-line input adapter** (Lab 27.7.3). `application.DemoDataLoader` moves to `adapter.in.cli.DomainModelRunner`, uses only the input port `CheckingAccountCommands`, runs with the `demo` profile and leaves a readable trace of each use case it runs.

With the `demo` profile the same operations as in phase 1 run: the three demo accounts (Ada Lovelace, Grace Hopper, Alan Turing) are opened with their owners. Since #18 they are opened by NIF, with a generated IBAN and the segment decided by the tax agency.

**Extended at the user's request (2026-11-03):** after opening the three accounts, the demo also deposits, withdraws (one approved and one rejected withdrawal) and transfers, reading the generated IBANs from the returned summaries, with a `[demo]` trace for each operation. The demo runs as `CallerDef.system()`, an administrator, which may operate any account (ADR 0008); a test pins it.

### Out of scope

- Command-line arguments or an interactive shell: the runner is an `ApplicationRunner` activated by the profile.
- Building the core in `config` (#20) and `HexagonalArchitectureTests` (#22).
- Updating `docs/analysis/` and `fase-1-capas.md`, which describe phase 1 and keep the old name on purpose.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in.cli` | `DomainModelRunner.java` (from `application/DemoDataLoader.java`, `git mv`) | CLI input adapter: `@Profile("demo")`, `ApplicationRunner`, only `CheckingAccountCommands` |
| tests | `adapter/in/cli/DomainModelRunnerTests.java` (from `application/DemoDataLoaderTests.java`) | Moved; the runner is built with the port type; trace tests |
| tests | `DddLabApplicationTests.java` | References the new class |
| docs | `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/*` | Class-role table, pending item from #16 resolved, project status |

Building block: **input adapter** (driving side). It *uses* the input port and translates nothing but its own fixed data into commands; it never exposes or receives aggregates (see `hexagonal-adapter` skill).

The architecture is already guarded by existing rules, which now cover the new package without changes: nobody outside `adapter.out`/`config` knows the output adapters; input adapters do not know `CheckingAccountService`; input adapters only know `DomainException` from the domain; the slices of `adapter.in.(*)` (`cli`, `rest`, `web`) do not know each other. T3 proves it by breaking them from the runner.

## Tasks

### [x] T1 — Move the runner to `adapter.in.cli` (~10 min)

- **Layer:** adapter.in
- **Files:** `application/DemoDataLoader.java` → `adapter/in/cli/DomainModelRunner.java`, `application/DemoDataLoaderTests.java` → `adapter/in/cli/DomainModelRunnerTests.java`, `DddLabApplicationTests.java`
- **Depends on:** —
- **What to do:**
  1. `git mv` both files and rename the classes; the runner's field and the test's construction are typed as `CheckingAccountCommands` (pending item from #16). Javadoc: CLI input adapter, demo profile.
  2. The existing tests act as characterization tests: same three accounts, owners and segments. `DddLabApplicationTests` checks that `DomainModelRunner` is not loaded without the `demo` profile.
- **Acceptance criteria:**
  - [x] `DomainModelRunnerTests`, `DemoProfileTests` and `DddLabApplicationTests` pass; `./mvnw verify` green.

### [x] T2 — Readable trace of each use case (~10 min)

- **Layer:** adapter.in
- **Files:** `DomainModelRunner.java`, `DomainModelRunnerTests.java`
- **Depends on:** T1
- **What to do:**
  1. Tests first (`OutputCaptureExtension`): each opening logs one line with the use case, the generated IBAN, the holder, the segment label and the initial balance (for example `[demo] Abrir cuenta · ES… · Grace Hopper · Alto patrimonio · 5000.00 EUR · propietario grace`), and a closing line with how many accounts were opened.
  2. Implement the trace in the runner.
- **Acceptance criteria:**
  - [x] The trace tests pass; the log of `./mvnw spring-boot:run` shows the three lines and the summary.

### [x] T3 — Architecture rules cover the CLI adapter (~5 min)

- **Layer:** tests
- **Files:** `LayeredArchitectureTests.java` (Javadoc only, if needed)
- **Depends on:** T1
- **What to do:**
  1. Validate by breaking them, one at a time, from `DomainModelRunner`: a reference to `CheckingAccountService`, to an output adapter and to a web class; each must fail its rule (`los_adaptadores_de_entrada_solo_conocen_los_puertos_de_entrada`, `solo_los_adaptadores_de_salida_y_config_conocen_los_adaptadores_de_salida`, `la_web_y_rest_no_se_conocen`). Revert. Mention the CLI in the Javadoc of the input-adapter rule.
- **Acceptance criteria:**
  - [x] Each sabotage fails its rule; after reverting `./mvnw verify` is green (issue criterion: the runner imports nothing from `adapter.out` nor the application service).

### [x] T4 — `CallerDef.system()` operates any account (~5 min)

- **Layer:** application
- **Files:** `CheckingAccountServiceOwnershipTests.java`
- **Depends on:** T3
- **What to do:**
  1. Test: the system caller deposits into, withdraws from and transfers from an account owned by another user (it is an administrator, ADR 0008). No production change expected; prove the test can fail by temporarily making `system()` a non-admin.
- **Acceptance criteria:**
  - [x] The test passes and fails when `system()` is not an administrator (sabotage, reverted).

### [x] T5 — Demo deposits and withdrawals (~10 min)

- **Layer:** adapter.in
- **Files:** `DomainModelRunner.java`, `DomainModelRunnerTests.java`
- **Depends on:** T4
- **What to do:**
  1. Tests first: after opening, the runner deposits 200.00 € into Ada's account, withdraws 50.00 € from Alan's (approved) and 1000.00 € from Alan's (rejected: retail, no overdraft); final balances and a `[demo]` line per operation with the IBAN, the amount and the result ("aprobada" / "rechazada").
  2. The runner keeps the summaries it opened (generated IBAN and id) and uses `CheckingAccountCommands.deposit` / `withdraw`.
- **Acceptance criteria:**
  - [x] Balances after the demo: Ada 1200.00, Alan 200.00; traces of the three movements.

### [x] T6 — Demo transfer (~10 min)

- **Layer:** adapter.in · tests
- **Files:** `DomainModelRunner.java`, `DomainModelRunnerTests.java`, `DemoProfileTests.java`
- **Depends on:** T5
- **What to do:**
  1. Tests first: Grace transfers 300.00 € to Ada, by Ada's generated IBAN read from her summary; final balances (Grace 4700.00, Ada 1500.00) and a `[demo]` trace with both IBANs and the result.
  2. `DemoProfileTests`: with the full context and the `demo` profile, the API shows those final balances.
- **Acceptance criteria:**
  - [x] Transfer done with the generated IBAN; demo tests adjusted to the new balances.

### [x] T7 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T6
- **What to do:**
  1. `fase-2-hexagonal.md`: "State after #19", row of `DomainModelRunner` in `adapter.in.cli` instead of `DemoDataLoader`; remove it from "classes that arrive later".
  2. Project status: #19 done, next step `/plan-feature 20`; remove the pending item about `DemoDataLoader` (#16); decisions that mention `DemoDataLoader` point to the new name.
- **Acceptance criteria:**
  - [x] No current document (outside `docs/plans/`, `historial.md`, `docs/analysis/` and `fase-1-capas.md`) names `DemoDataLoader` except as the former name of `DomainModelRunner`; the CI check of the project status passes.

### [x] T8 — Review fixes (~5 min)

- **Layer:** tests · docs
- **Files:** `DomainModelRunnerTests.java`, `LayeredArchitectureTests.java`, `docs/ia/arquitectura-ia.md`
- **Depends on:** T7
- **What to do:**
  1. `revisor-ddd`: the test passes the runner the port type (pending item from #16); the Javadoc of the input-adapter rule loses a stray line break; `arquitectura-ia.md` explains that the clean-up hook and the new branch-deletion step of the skills complement each other.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green.

## Global acceptance criteria

**Specific:**

- [ ] With the `demo` profile the same operations as in phase 1 run (three demo accounts with their owners).
- [ ] The runner imports nothing from `adapter.out` nor the application service (ArchUnit, validated by breaking it).
- [ ] The runner leaves a readable trace of each use case.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no REST change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: no new business terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Same operations as in phase 1:** only the three openings. The runner could also run a deposit, a withdrawal and a transfer to show more use cases from the CLI, but that changes the demo data the web and the tests see; left out unless you want it.
- **Trace format:** one line per use case with a `[demo]` prefix, in Spanish like the rest of the logs.
- This PR also carries the earlier commit that records the agreement to delete branches and worktrees after merging (project status and `implement-plan` skills).
