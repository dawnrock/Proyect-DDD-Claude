# Plan: Close phase 2 — layers vs hexagonal comparison and `v2-hexagonal` tag

- **Issue:** [#23](https://github.com/dawnrock/Proyect-DDD-Claude/issues/23)
- **Type:** docs (closes the phase; no production code changes)
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-04
- **Status:** in progress
- **Total estimate:** ~75 min (8 tasks: 7 in the PR + 1 after it is merged; open questions answered by the user on 2026-11-04)

## Goal

Document the second snapshot as it really ended up and compare it with the first one: a new `docs/arquitectura/comparativa-capas-hexagonal.md` with the diagrams of both phases, what changed in each package, the cost and the benefits (isolated tests, swappable adapters) and links to the `v1-monolito...v2-hexagonal` diffs. `README.md`, `fase-2-hexagonal.md` and `docs/ia/arquitectura-ia.md` are brought to the final state, and the phase-2 snapshot is marked with the `v2-hexagonal` tag on `Hexagonal`. `Hexagonal` stays the active branch after the tag (user's decision): the next work (translation, Graphify re-measure, pending web items) goes there, each in its own issue. Nothing under `src/` changes.

### Out of scope

- Any code, test or `openapi.yaml` change.
- Translating the documents that are still in Spanish (its own issue, see `pendientes.md`).
- Doing the translation, the Graphify re-measure (pending item from #57) or the pending web items: this plan only opens their issues (T6).
- Opening a phase 3 or changing the default branch.
- Changes to `CLAUDE.md` or `.claude/` (none are needed).

## Context

| Area | File | Change |
| ---- | ---- | ------ |
| docs | `docs/arquitectura/comparativa-capas-hexagonal.md` (new) | Diagrams of both phases, package-by-package changes, cost and benefits, diff links |
| docs | `docs/arquitectura/fase-2-hexagonal.md` | "Target at the end of the phase" → final state, tagged `v2-hexagonal`; "Class → role" no longer "State after #20"; new "Issues of the phase" table (issue → PR), like `fase-1-capas.md` |
| docs | `README.md` | Phase 2 closed at `v2-hexagonal`, `Hexagonal` still active; link to the comparison; the demo also deposits, withdraws and transfers (#19) |
| docs | `docs/ia/arquitectura-ia.md` | "Usage log · Phase 2": issue → plan → how → PR, and the adjustments of the phase |
| docs | `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md` | Status as it will be after the merge and the tag |
| GitHub | three new issues | Translation of the docs still in Spanish, Graphify re-measure, pending web items (T6) |
| git | tag `v2-hexagonal` | After the PR is merged, with the user's go-ahead |

Facts gathered for the documentation:

- Milestone "Fase 2": every issue is closed except #23 (#14-#22, PRs #61-#69).
- `Monolithic` is still at `v1-monolito` (10eb227, merge of #60) and `master` at 3ef3c48: no cross changes since phase 1 closed.
- `git diff --stat v1-monolito origin/Hexagonal`: 117 files, +4802 / −916 (code, tests and docs).
- Package moves at `v1-monolito`: `presentation.{rest,web}` + root support → `adapter.in.{rest,web}` + root; `persistence` → `adapter.out.persistence`; `application.DemoDataLoader` → `adapter.in.cli.DomainModelRunner`. New: output ports and events in `domain.model`, the factory, input ports and `listener` in `application`, `adapter.out.{event,taxagency}`, `config`.
- Diagrams that already exist: the layers diagram (Mermaid, in `fase-1-capas.md`) and `img/hexagonal.svg` (grayscale, `fase-2-hexagonal.md`). The comparison reuses them and adds a new one, approved by the user: the same deposit drawn in both architectures.
- ArchUnit: `LayeredArchitectureTests` (phase 1, lives on in `Monolithic`) vs `HexagonalArchitectureTests` (17 rules, `onionArchitecture()` + 16 explicit), plus `DomainIsolationTests` (both) and `AdapterTestIsolationTests` (phase 2).

## Tasks

### [ ] T1 — Comparison: diagrams and changes per package (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/comparativa-capas-hexagonal.md`
- **Depends on:** —
- **What to do:**
  1. Both diagrams side by side: the phase-1 Mermaid layers diagram (copied, same grayscale style) and `img/hexagonal.svg`; one sentence each on where the dependencies point.
  2. New diagram (Mermaid, grayscale): the same deposit in both architectures, from the REST controller to the repository, showing which class each call reaches (concrete classes in phase 1; input port → service → output port ← adapter, plus the event published after saving, in phase 2). **Shown to the user before committing.**
  3. Table "What changed in each package": phase-1 package → phase-2 package → what changed → issue (#14-#20), from the facts above.
  4. Section "See the diffs": links to `compare/v1-monolito...v2-hexagonal` (whole repository) and to the main changes (`src/main/java/.../adapter`, `application`, `domain/model`, `config`, ArchUnit tests).
- **Acceptance criteria:**
  - [ ] Every package of `src/main` at both tags appears in the table (checked with `git ls-tree` on `v1-monolito` and `HEAD`).
  - [ ] Every relative link resolves; the compare links use the final tag name.
  - [ ] The deposit diagram matches the code at both tags and the user approved it.

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

### [x] T3 — `fase-2-hexagonal.md`: final state (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`
- **Depends on:** —
- **What to do:**
  1. A line at the top: state at the end of the phase, tagged `v2-hexagonal`, with a link to the comparison. "Target at the end of the phase" → "Final package structure"; "State after #20" → final state, and the "Changes in" column (all "—") goes away.
  2. New "Issues of the phase" table: issue → title → PR, for #14-#23 (from `historial.md`).
- **Acceptance criteria:**
  - [x] The table lists the 10 issues of the milestone; the package tree matches `src/main` on `Hexagonal`.

### [ ] T4 — `README.md` with the final state (~5 min)

- **Layer:** docs
- **Files:** `README.md`
- **Depends on:** T1
- **What to do:**
  1. Branches table: phase 2 closed at tag `v2-hexagonal`; `Hexagonal` stays the default and active branch. Documentation: link to the comparison.
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

### [ ] T6 — Issues for the next work (~5 min)

- **Layer:** docs (GitHub)
- **Files:** —
- **Depends on:** —
- **What to do:**
  1. Open three issues on `Hexagonal` (labels `fase:2-hexagonal` and the type/layer ones; no milestone, since phase 2 closes), in Spanish like the others, each with goal, scope and acceptance criteria taken from `pendientes.md`:
     - Translate to English the docs still in Spanish (glossary, ADRs, `docs/ia` annexes, the rest listed in `pendientes.md`; `docs/analysis/` stays in Spanish as agreed in #52).
     - Re-measure Graphify on the phase-2 code with the #57 method (`claude -p --agent buscador-referencias`, ADR 0010) and decide whether to widen its use.
     - Pending web items from #48: segment in the account list, own label for transfers, non-breaking spaces in `WebFormat.maskedIban`, movements order.
- **Acceptance criteria:**
  - [ ] The three issues exist and their numbers are written in the project status (T7).

### [ ] T7 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1-T6
- **What to do:**
  1. `estado-del-proyecto.md` as it will be after the merge and the tag: phase 2 closed (#14-#23) at `v2-hexagonal`; `Hexagonal` still active; next step: `/plan-feature` of the first of the three new issues.
  2. `decisiones.md`: `v2-hexagonal` marks the phase-2 snapshot; unlike `Monolithic` after #13, `Hexagonal` is not frozen: work continues on it and it stays the default branch (user's decision, 2026-11-04).
  3. `pendientes.md`: the items now covered by an issue point to it (translation, Graphify, web from #48); the ones that said "for phase 2" are rephrased as pending for future work (#7 transaction).
  4. `historial.md`: one row for this PR.
- **Acceptance criteria:**
  - [ ] `git diff --name-only origin/Hexagonal...HEAD | node scripts/check-project-status.mjs -` (the CI check) passes.

### [ ] T8 — Tag `v2-hexagonal` (~5 min, after the PR is merged)

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
- [ ] The issues for the translation, the Graphify re-measure and the pending web items exist.
- [ ] `Monolithic`, `Hexagonal` and `master` coexist without cross changes (`Monolithic` = `v1-monolito`, `master` unchanged).
- [ ] `./mvnw verify` green on `Hexagonal` at the tag `v2-hexagonal`.
- [ ] `comparativa-capas-hexagonal.md` exists with both diagrams, the deposit in both architectures, changes per package, cost and benefits and diff links; `README.md` and `arquitectura-ia.md` describe phase 2.

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

Answered by the user on 2026-11-04: (1) yes to a new diagram, the deposit in both architectures; (2) next work: translation, Graphify re-measure and pending web items, each in its own issue; (3) `Hexagonal` stays active after the tag.

- **The compare links point to a tag that does not exist yet:** they resolve once T8 publishes `v2-hexagonal`; T8 checks them.
- **T8 happens after the merge** and asks for the go-ahead first (it publishes to the repository). The tag points to the merge commit, which already carries the status written as after the tag (as in #13).
- **The tag and the active branch:** with `Hexagonal` still active, the phase-2 snapshot lives only in the tag; the comparison and the compare links use the tag, never the branch.
- **New issues without a milestone:** they are not part of phase 2. If you would rather group them (for example a "Mantenimiento" milestone), say so.
