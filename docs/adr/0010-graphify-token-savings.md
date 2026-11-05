# 0010 · Graphify for dependency questions only, and a short project status

- **Status:** accepted · 2026-10-30 · confirmed on phase 2 on 2026-11-05 (#71)
- **Issue:** #57 (re-measured in #71)

## Context

The goal of #57 is to spend fewer tokens in every Claude Code session, above all ahead of phase 2, where the move to hexagonal touches every package.

[Graphify](https://pypi.org/project/graphifyy/) (`graphifyy` 0.9.97) builds a knowledge graph of the Java code with tree-sitter, locally and without LLM calls (about 9 s for this repository), and answers questions such as "who depends on X" (`affected`) or "how does A reach B" (`path`). Its `graphify claude install` also rewrites `CLAUDE.md` and adds two `PreToolUse` hooks (`hook-guard search|read`) that inject a reminder to use the graph before Grep, Read and Glob calls.

Two facts shaped the decision:

- **A session never loads the whole project.** What every new session reads is `CLAUDE.md` (~1.6k tokens) and, because `CLAUDE.md` asks for it, `docs/ia/estado-del-proyecto.md`, which had grown to 13.3 KB (~3.5k tokens). Code (~95k tokens) and `docs/` (~85k tokens) are only read on demand.
- **The graph only helps when searching code.** We measured it.

### Measurement

The same three questions to `buscador-referencias`, each in a new headless session (`claude -p --agent …`, Haiku), so that agent definitions and hooks are loaded fresh. Baseline: the agent as it was before #57 (taken from git) and no `graphify-out/`. With graph: the agent asked to query the graph first, plus the `hook-guard` hooks. Tokens processed (input + cache + output):

| Question | Without graph | With graph | Change |
| -------- | ------------- | ---------- | ------ |
| Where is `CheckingAccount.withdraw` used? (the method is `tryWithdraw`) | 59,188 | 80,520 | +36 % |
| Which classes depend on `MoneyTransferService`? | 34,108 | 24,849 | **−27 %** |
| Which `presentation` classes use `CheckingAccountService`? | 19,459 | 44,235 | +127 % |
| **Total** | **112,755** | **149,604** | +33 % |

The cost in dollars was the same (USD 0.0127 vs 0.0129), because most tokens are cheap cache reads. Single runs are noisy, but the pattern is clear:

- A dependency question is answered by one `affected` call with file and line for every dependant: cheaper.
- An ambiguous name (`withdraw` has no node) leads to retries and then Grep anyway: more expensive.
- When the question also covers `docs/`, the agent queries the graph and then greps the docs anyway: the graph adds cost.
- The hook added its reminder up to three times per session, in every session.

A first attempt measured inside the working session showed no graph use at all: subagent definitions and hooks are loaded when a session starts, so changes to them only take effect in a new session.

## Decision

1. **Graphify only for dependency questions.** `buscador-referencias` and `plan-feature` use `node scripts/graphify.mjs affected "<Class>"` and `path "<A>" "<B>"` when the question is who depends on a class or how two classes relate. Everything else keeps using Grep/Glob.
2. **No hook and no `graphify claude install`.** The reminders cost more than they saved, and the command rewrites `CLAUDE.md`.
3. **Code-only graph, not versioned.** `.graphifyignore` limits it to the Java code (no `docs/`, `.claude/`, `scripts/`, `target/`), so building it never costs tokens; `graphify-out/` is in `.gitignore`. `implement-plan` and `implement-plan-team` regenerate it when they create a worktree (`node scripts/graphify.mjs update .`). Graphify's git hooks are not used: they are not versioned and would run in every worktree.
4. **`scripts/graphify.mjs`** is the only entry point: it finds `graphify` on the PATH or as `py -m graphify` / `python3 -m graphify`, forces `GRAPHIFY_BACKEND=none`, and exits with 1 and a message when Graphify is missing, so callers fall back to Grep. Graphify is a development tool installed per machine (`pip install graphifyy`); the Maven build does not change.
5. **Short project status.** `docs/ia/estado-del-proyecto.md`, which every session reads, keeps only where we are, the next step and the working agreements (13.3 KB → 2.9 KB). Decisions, pending items and history move to `docs/ia/decisiones.md`, `pendientes.md` and `historial.md`, read only when a task needs them. This is the saving that applies to every session.

## Alternatives

- **Full integration (`graphify claude install`, graph first for every search):** measured above; more tokens on this repository size.
- **Remove Graphify entirely:** loses the one case where it saves (dependency questions), which becomes more frequent in phase 2.
- **Graphify's MCP server:** same graph, one more moving part, and the tool schemas would add tokens to every session.
- **Include `docs/` in the graph:** documents are extracted by the model, which costs tokens on every rebuild.

## Consequences

- Every new session reads ~2.5k-3k fewer tokens of project status.
- Dependency questions are cheaper and include indirect dependants (tests, controllers) in one call.
- Machines without Graphify behave exactly as before.
- The graph can be stale between commits; `node scripts/graphify.mjs update .` rebuilds it in seconds.
- ~~To revisit in phase 2: repeat the measurement with the same method when the code base is larger.~~ Done in #71: the decision holds (see below).

## Measurement on phase 2 (2026-11-05, #71)

The code grew from 89 to 125 Java files (`v1-monolito` → `Hexagonal`), with ports, adapters and events. Same method as above, now repeatable with [`scripts/measure-graphify.mjs`](../../scripts/measure-graphify.mjs): each question in a new headless session (`claude -p --agent buscador-referencias`, Haiku 5.5, Claude Code 2.1.318, cloud Linux session), Graphify 0.9.97, **two runs** per question and configuration. Tokens processed = input + cache creation + cache read + output.

- **A · no graph:** no `graphify-out/`; the agent as it is (it still checks for the graph on dependency questions, and that check fails at once).
- **B · current rule:** graph built; the agent as it is (graph only for dependency questions).
- **C · graph first:** graph built; an inline variant of the agent (`--agents`) told to query the graph first for every code search (`query`, `explain`, `affected`, `path`). `.claude/` was not changed.

Every one of the 30 answers was complete (checked against the expected classes). Mean of the two runs:

| Question | A · no graph | B · current rule | C · graph first |
| -------- | ------------ | ---------------- | --------------- |
| 1. Where is `CheckingAccount.withdraw` used? (ambiguous: the method is `tryWithdraw`) | 55,012 | 118,046 (+115 %) | 76,207 (+39 %) |
| 2. Which classes depend on `MoneyTransferService`? | 34,063 | **20,865 (−39 %)** | 32,439 (−5 %) |
| 3. Which input adapters use `CheckingAccountCommands`? | 35,877 | **20,632 (−42 %)** | 28,759 (−20 %) |
| 4. Which class implements `DomainEventPublisher`, and who calls it? | 59,707 | **46,939 (−21 %)** | 65,718 (+10 %) |
| 5. Where is rule R20 defined, tested and documented? (general, code + docs) | 46,871 | 55,169 (+18 %) | 51,126 (+9 %) |
| **Dependency questions (2-4)** | **129,647** | **88,436 (−32 %)** | **126,916 (−2 %)** |
| **All five** | **231,529** | **261,650 (+13 %)** | **254,249 (+10 %)** |

Cost of the ten runs of each configuration: A USD 0.038, B USD 0.037, C USD 0.050.

Reading:

- **Dependency questions are clearly cheaper with the current rule (−32 %)**, more than in phase 1 (−27 % on one question): one `affected` call answers with file and line, in two turns (questions 2 and 3) instead of three to seven without the graph.
- **"Graph first" (C) does not pay off.** It spends the saving on `explain`/`query` calls that do not answer the question, then greps anyway; on dependency questions it is barely better than no graph, and it is the most expensive in dollars.
- **The ambiguous question is where B loses** (+115 %): "where is X used" reads as a dependency question, `withdraw` has no node, and the agent retries before falling back to Grep. Same pattern as in phase 1. B's whole-set total is worse than A's only because of this question; without it, B saves 19 % (143,604 vs 176,517).
- **General searches (question 5) do not use the graph** in B, as intended; the B/A difference there is run-to-run noise (B's two runs: 48,842 and 61,495).

**Decision: confirmed.** The graph stays limited to dependency questions about classes; it is not widened. A possible refinement, not applied (it changes `.claude/`, which needs the user's approval): tell `buscador-referencias` to use Grep, not the graph, when the question names a **method** (`Class.method`), which would avoid the only case where the current rule costs more.
