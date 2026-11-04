# Plan: Close phase 2 — layers vs hexagonal comparison and `v2-hexagonal` tag

- **Issue:** [#23](https://github.com/dawnrock/Proyect-DDD-Claude/issues/23)
- **Type:** docs (closes the phase; no production code changes)
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-04
- **Status:** draft
- **Total estimate:** ~60 min (7 tasks: 6 in the PR + 1 after it is merged)

## Goal

Document the second snapshot as it really ended up and compare it with the first one: a new `docs/arquitectura/comparativa-capas-hexagonal.md` with the diagrams of both phases, what changed in each package, the cost and the benefits (isolated tests, swappable adapters) and links to the `v1-monolito...v2-hexagonal` diffs. `README.md`, `fase-2-hexagonal.md` and `docs/ia/arquitectura-ia.md` are brought to the final state, and the phase is frozen with the `v2-hexagonal` tag on `Hexagonal`. Nothing under `src/` changes.

### Out of scope

- Any code, test or `openapi.yaml` change.
- Translating the documents that are still in Spanish (its own issue, see `pendientes.md`).
- Re-measuring Graphify in phase 2 (pending item from #57): it is a measurement with its own method, not documentation (see open questions).
- Opening a phase 3 or changing the default branch.
- Changes to `CLAUDE.md` or `.claude/` (none are needed).

## Context

| Area | File | Change |
| ---- | ---- | ------ |
| docs | `docs/arquitectura/comparativa-capas-hexagonal.md` (new) | Diagrams of both phases, package-by-package changes, cost and benefits, diff links |
| docs | `docs/arquitectura/fase-2-hexagonal.md` | "Target at the end of the phase" → final state frozen at `v2-hexagonal`; "Class → role" no longer "State after #20"; new "Issues of the phase" table (issue → PR), like `fase-1-capas.md` |
| docs | `README.md` | `Hexagonal` closed and frozen at `v2-hexagonal`; link to the comparison; the demo also deposits, withdraws and transfers (#19) |
| docs | `docs/ia/arquitectura-ia.md` | "Usage log · Phase 2": issue → plan → how → PR, and the adjustments of the phase |
| docs | `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md` | Status as it will be after the merge and the tag |
| git | tag `v2-hexagonal` | After the PR is merged, with the user's go-ahead |

Facts gathered for the documentation:

- Milestone "Fase 2": every issue is closed except #23 (#14-#22, PRs #61-#69).
- `Monolithic` is still at `v1-monolito` (10eb227, merge of #60) and `master` at 3ef3c48: no cross changes since phase 1 closed.
- `git diff --stat v1-monolito origin/Hexagonal`: 117 files, +4802 / −916 (code, tests and docs).
- Package moves at `v1-monolito`: `presentation.{rest,web}` + root support → `adapter.in.{rest,web}` + root; `persistence` → `adapter.out.persistence`; `application.DemoDataLoader` → `adapter.in.cli.DomainModelRunner`. New: output ports and events in `domain.model`, the factory, input ports and `listener` in `application`, `adapter.out.{event,taxagency}`, `config`.
- Diagrams that already exist: the layers diagram (Mermaid, in `fase-1-capas.md`) and `img/hexagonal.svg` (grayscale, `fase-2-hexagonal.md`). The comparison reuses them; no new diagram is planned.
- ArchUnit: `LayeredArchitectureTests` (phase 1, lives on in `Monolithic`) vs `HexagonalArchitectureTests` (17 rules, `onionArchitecture()` + 16 explicit), plus `DomainIsolationTests` (both) and `AdapterTestIsolationTests` (phase 2).

## Tasks

### [ ] T1 — Comparison: diagrams and changes per package (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/comparativa-capas-hexagonal.md`
- **Depends on:** —
- **What to do:**
  1. Both diagrams side by side: the phase-1 Mermaid layers diagram (copied, same grayscale style) and `img/hexagonal.svg`; one sentence each on where the dependencies point.
  2. Table "What changed in each package": phase-1 package → phase-2 package → what changed → issue (#14-#20), from the facts above.
  3. Section "See the diffs": links to `compare/v1-monolito...v2-hexagonal` (whole repository) and to the main changes (`src/main/java/.../adapter`, `application`, `domain/model`, `config`, ArchUnit tests).
- **Acceptance criteria:**
  - [ ] Every package of `src/main` at both tags appears in the table (checked with `git ls-tree` on `v1-monolito` and `HEAD`).
  - [ ] Every relative link resolves; the compare links use the final tag name.

### [ ] T2 — Comparison: cost and benefits (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/comparativa-capas-hexagonal.md`
- **Depends on:** T1
- **What to do:**
  1. "Benefits", each with its evidence in the repository: adapter tests without the full context (`@WebMvcTest` with mocked ports, `AdapterTestIsolationTests`); swappable adapters (port contracts `*Contract` that any implementation must pass; the tax agency and the numbering behind ports; the CLI using the same input port as REST and web); a core without Spring built in `config`; rules enforced by ArchUnit.
  2. "Cost": more types (ports, events, factory, config), indirection (who implements a port), wiring in `config`, the justified exceptions (`@EventListener`, ownership port in `application`, package-private adapters component-scanned), synchronous listeners (pending item from #17).
  3. "Numbers": files and lines from `git diff --stat`, production classes and tests per tag, ArchUnit rules per phase. "When each one fits": two or three lines.
- **Acceptance criteria:**
  - [ ] Every benefit and cost names a class, test or ADR that exists on `Hexagonal`.
  - [ ] The numbers match the commands they come from (written next to each number).

### [ ] T3 — `fase-2-hexagonal.md`: final state (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`
- **Depends on:** —
- **What to do:**
  1. A line at the top: final state of the phase, frozen at `v2-hexagonal`, with a link to the comparison. "Target at the end of the phase" → "Final package structure"; "State after #20" → final state, and the "Changes in" column (all "—") goes away.
  2. New "Issues of the phase" table: issue → title → PR, for #14-#23 (from `historial.md`).
- **Acceptance criteria:**
  - [ ] The table lists the 10 issues of the milestone; the package tree matches `src/main` on `Hexagonal`.

### [ ] T4 — `README.md` with the final state (~5 min)

- **Layer:** docs
- **Files:** `README.md`
- **Depends on:** T1
- **What to do:**
  1. Branches table: `Hexagonal` closed and frozen at `v2-hexagonal` (still the default branch). Documentation: link to the comparison.
  2. "Run": the `demo` profile opens the three accounts and also runs a deposit, two withdrawals (approved and rejected) and a transfer (#19).
- **Acceptance criteria:**
  - [ ] The commands in "Run" still work (`mvn verify`, `spring-boot:run`, `curl -u admin:demo …/api/accounts` returns 200).

### [ ] T5 — `arquitectura-ia.md`: usage log of phase 2 (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/arquitectura-ia.md`
- **Depends on:** —
- **What to do:**
  1. "Phase 2 · Hexagonal architecture (tag `v2-hexagonal`)": table issue → plan → implemented with → PR for #14-#23 (#18 with `implement-plan-team`, the rest with `implement-plan`).
  2. "Adjustments made during the phase", one line each with its PR: the whole phase worked from a cloud session (system `mvn` because the wrapper cannot download Maven, no force push); the team flow in the cloud with subagents in local worktrees and the contract agreed in one round (#65); branches and worktrees deleted after every merge, now a step of the `implement-plan` skills (#66); Telegram back on (#62); characterization tests and every ArchUnit rule validated by breaking it; what `revisor-ddd` caught (contract comparing by reference #62, missing 400 #68, `.and()/.or()` precedence #69); the `hexagonal-adapter` skill used for ports and adapters.
- **Acceptance criteria:**
  - [ ] Every phase-2 plan in `docs/plans/` appears in the table and every PR link resolves.

### [ ] T6 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1-T5
- **What to do:**
  1. `estado-del-proyecto.md` as it will be after the merge and the tag: phase 2 closed (#14-#23) at `v2-hexagonal`; next step: decide with the user what comes next (no open issues; candidates in the open questions).
  2. `decisiones.md`: `Hexagonal` is frozen at `v2-hexagonal` like `Monolithic` and `master` (changes only by explicit decision) and stays the default branch.
  3. `pendientes.md`: items that said "for phase 2" are rephrased as pending for future work (#7 transaction, Graphify re-measure).
  4. `historial.md`: one row for this PR.
- **Acceptance criteria:**
  - [ ] `git diff --name-only origin/Hexagonal...HEAD | node scripts/check-project-status.mjs -` (the CI check) passes.

### [ ] T7 — Tag `v2-hexagonal` (~5 min, after the PR is merged)

- **Layer:** build
- **Files:** —
- **Depends on:** the user merges the PR
- **What to do:**
  1. With the user's go-ahead: `git fetch`, `mvn verify` on `origin/Hexagonal`, check that `Monolithic` is still `v1-monolito` and `master` still 3ef3c48, then `git tag -a v2-hexagonal origin/Hexagonal -m "Fase 2 · Arquitectura hexagonal"` and `git push origin v2-hexagonal`.
  2. Check that #23 and the milestone are closed (close the milestone if GitHub does not) and that the compare links of the comparison now resolve.
- **Acceptance criteria:**
  - [ ] `git ls-remote --tags origin v2-hexagonal` points to the merge commit of this PR, where `mvn verify` ends in `BUILD SUCCESS`.
  - [ ] No open issue left in the "Fase 2 · Arquitectura hexagonal" milestone.

## Global acceptance criteria

**Specific:**

- [ ] Every issue in the "Fase 2 · Arquitectura hexagonal" milestone is closed.
- [ ] `Monolithic`, `Hexagonal` and `master` coexist without cross changes (`Monolithic` = `v1-monolito`, `master` unchanged).
- [ ] `./mvnw verify` green on `Hexagonal` at the tag `v2-hexagonal`.
- [ ] `comparativa-capas-hexagonal.md` exists with both diagrams, changes per package, cost and benefits and diff links; `README.md` and `arquitectura-ia.md` describe phase 2.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit. Unchanged: no code changes.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a (no new tests).
- [ ] No dependencies added beyond the planned ones.
- [ ] If the API changes: n/a (the API does not change).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a (no new terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Diagrams:** the plan reuses the two existing ones (both grayscale). If a new one is wanted (for example the same deposit flow drawn in both architectures), it is shown to the user before committing.
- **The compare links point to a tag that does not exist yet:** they resolve once T7 publishes `v2-hexagonal`; T7 checks them.
- **T7 happens after the merge** and asks for the go-ahead first (it publishes to the repository). The tag points to the merge commit, which already carries the status written as after the tag (as in #13).
- **What comes after phase 2:** there are no more open issues. Candidates for the next step: the translation issue for the docs still in Spanish, the Graphify re-measure (#57), the pending web items (segment in the list, transfer label) or a phase 3. The status will say "decide with the user" unless you choose now.
- **Freezing `Hexagonal`:** the plan treats it like `Monolithic` after #13 (changes only by explicit decision). If you would rather keep working on it after the tag, the decision line changes.
