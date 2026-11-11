# Plan: Claude Code configuration — Grep for method questions, team lessons and `CLAUDE.md` notes

- **Issue:** [#79](https://github.com/dawnrock/Proyect-DDD-Claude/issues/79)
- **Type:** refactor (Claude Code configuration; no application code changes)
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-11
- **Status:** in progress
- **Total estimate:** ~60 min (6 tasks)

## Goal

Close the pending items of `docs/ia/pendientes.md` that touch the Claude Code configuration. Every change to `.claude/` and `CLAUDE.md` needs the user's **explicit approval** (working agreement); approving this plan is asked as that approval.

1. **Graphify for methods (#71, ADR 0010).** With the current rule the graph only costs more when the question names a method (`CheckingAccount.withdraw`, +115 %): the graph has no node for it and the agent retries before using Grep. `buscador-referencias` will use Grep directly when the question names a method (`Class.method`), and the graph only for classes. Measured before and after.
2. **Team lessons (#7, #18, #50).** `implement-plan-team` still tells the teammates to "answer each other", but subagents cannot message each other: in #65, #72 (and #39-#51) the lead merged their contract reviews. It also does not say why the lead creates the worktrees (the ones `Agent` creates with `isolation: worktree` start from another branch). Both go into the skill.
3. **Two notes for `CLAUDE.md`** suggested since #11 and #54: the `demo` profile of `spring-boot:run` (and how to start without data), and that phase-1 `presentation` may know the domain exceptions (rule D4).

### Out of scope

- Widening the use of the graph (discarded in #71).
- Code changes: they go to #82.
- Other `.claude/` changes not listed here.

## Context

| Area | File | Change |
| ---- | ---- | ------ |
| `.claude/agents` | `buscador-referencias.md` | Method questions (`Class.method`, or a lowercase member name) → Grep first; the graph only for class dependencies |
| `.claude/skills` | `implement-plan-team/SKILL.md` | Step 2.3: the teammates review separately and the lead merges the reviews (they cannot message each other); step 3.1: why the lead creates the worktrees from the contract commit; the teammate checks that every path it edits is inside its worktree |
| root | `CLAUDE.md` | Commands: `spring-boot:run` starts with the `demo` profile; `-Dspring-boot.run.profiles=default` starts without data. Phase 1: `presentation` may know `DomainException` and its subclasses (D4) |
| docs | `docs/adr/0010-graphify-token-savings.md`, `docs/ia/arquitectura-ia.md`, `docs/ia/*` | Measurement, pieces table, status |

**Measurement method** (same as #71): `node scripts/measure-graphify.mjs B 2 <question>` for questions 1-4 (the method question and the three dependency ones), before and after the change, in the same session, so the comparison does not depend on #71's absolute numbers. Headless sessions load the agent definition fresh, so the "after" runs see the new rule.

## Tasks

### [x] T1 — Baseline with the current rule (~10 min)

- **Layer:** tooling
- **Files:** — (measurement notes for T3)
- **Depends on:** —
- **What to do:**
  1. Install Graphify if the session lacks it (`pip install --user graphifyy==0.9.97`) and build the graph. Run questions 1-4 of `scripts/measure-graphify.mjs` with configuration B, two runs each; keep the output (scratchpad).
- **Acceptance criteria:**
  - [x] 8 runs recorded, all answers complete.

### [x] T2 — `buscador-referencias`: Grep for method questions (~5 min, needs approval)

- **Layer:** `.claude`
- **Files:** `.claude/agents/buscador-referencias.md`
- **Depends on:** T1
- **What to do:**
  1. In the "dependency questions" paragraph: when the question names a method or a field (`Class.method`, `method(`), go straight to Grep; the graph only answers who depends on a **class** or how two classes relate. Nothing else in the agent changes.
- **Acceptance criteria:**
  - [x] The diff only touches that paragraph.

### [ ] T3 — Measure again and record it in ADR 0010 (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0010-graphify-token-savings.md`
- **Depends on:** T2
- **What to do:**
  1. Same runs as T1. The method question must not call the graph and must cost less; the dependency questions must keep using it without a clear change (> 15 %).
  2. New short section "Methods go to Grep (#79)" with a before/after table; the "Decision" says the refinement is applied.
- **Acceptance criteria:**
  - [ ] Every number comes from the kept output; the conclusion follows from it (or, if the change does not help, the agent is reverted and the ADR says so).

### [x] T4 — `implement-plan-team`: lessons of the team issues (~10 min, needs approval)

- **Layer:** `.claude`
- **Files:** `.claude/skills/implement-plan-team/SKILL.md`
- **Depends on:** —
- **What to do:**
  1. Step 2.3: launch the two reviews separately (in parallel); subagents cannot message each other, so the lead merges both reviews into the contract and says so in its "Agreed by" line. Up to two rounds as today.
  2. Step 3.1: the lead creates the side worktrees from the contract commit **because** worktrees created with `isolation: worktree` start from another branch; each teammate gets its worktree's absolute path and checks that every file it edits is inside it, and does not push.
- **Acceptance criteria:**
  - [x] The skill matches what was done in #65 and #72; `pendientes.md` no longer lists the #7, #18 and #50 team items.

### [x] T5 — `CLAUDE.md`: demo profile and D4 (~5 min, needs approval)

- **Layer:** root
- **Files:** `CLAUDE.md`
- **Depends on:** —
- **What to do:**
  1. Commands block: the comment of `./mvnw spring-boot:run` says it starts with the `demo` profile (three accounts and their operations), and a line with `-Dspring-boot.run.profiles=default` to start without data.
  2. Phase-1 paragraph: `presentation` may know `DomainException` and its subclasses, to translate them (D4). Kept short: `CLAUDE.md` is loaded in every session.
- **Acceptance criteria:**
  - [x] Only those two places change; both statements match the code (`pom.xml`, `LayeredArchitectureTests` at `v1-monolito`).

### [ ] T6 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/arquitectura-ia.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T3, T4, T5
- **What to do:**
  1. `arquitectura-ia.md`: the code-graph row mentions the method rule; the team flow notes the lead merging the reviews.
  2. Status: #79 done, next step `/plan-feature 82`; decision; `pendientes.md` loses the items handled here (Graphify, team, the two `CLAUDE.md` suggestions); a row in `historial.md`.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] The agent uses Grep for the method question and the graph for the dependency ones (measured); the method question costs less without a clear worsening of the others; ADR 0010 updated.
- [ ] `implement-plan-team` records the lessons; `pendientes.md` no longer lists them.
- [ ] `CLAUDE.md` with both notes, approved by the user.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected, verified by ArchUnit. Unchanged: no code changes.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a (no new tests).
- [ ] No dependencies added beyond the planned ones (Graphify is a per-machine tool, ADR 0010).
- [ ] If the API changes: n/a.
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English (the `.claude/` files and `CLAUDE.md` keep their Spanish).
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Approval of `.claude/` and `CLAUDE.md`:** T2, T4 and T5 change them; approving the plan is taken as the explicit approval only if you say so. You can approve some and not others.
- **Cost:** 16 Haiku runs (well under USD 0.10 in #71's terms).
- **Pending items not handled here or in #82** stay as notes, because they are accepted risks or depend on future work: Telegram switch (information), synchronous event consumers (#17, until a real listener exists), login limitations (#50, ADR 0008).
