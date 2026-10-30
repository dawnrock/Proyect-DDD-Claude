# 0010 · Graphify for dependency questions only, and a short project status

- **Status:** accepted · 2026-10-30
- **Issue:** #57

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
- To revisit in phase 2: repeat the measurement with the same method when the code base is larger (tracked in `docs/ia/pendientes.md`).
