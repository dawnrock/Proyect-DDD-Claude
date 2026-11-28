# 0027 · Remove Graphify: Grep + LSP for code searches

- **Status:** accepted · 2026-11-28
- **Supersedes:** the Graphify part of [ADR 0010](0010-graphify-token-savings.md) (decisions 1-4). Its decision 5, the short project status with annexes, still applies.

## Context

ADR 0010 brought in Graphify (`graphifyy`, a Python package) to build a local graph of the Java code. `buscador-referencias` and `plan-feature` queried it only for dependency questions (`affected`, `path`), and `implement-plan`/`implement-plan-team` rebuilt it in every worktree. The re-measurement on phase 2 (#71) found a 32 % saving on dependency questions, but the graph cost more on ambiguous questions, did nothing for general searches, and over all five questions the current rule spent 13 % more tokens than having no graph. The cost in dollars was the same either way.

A review of the project's technologies weighed that saving against what it takes to keep:

- **A Python toolchain for one helper** in a Java + Node project: `pip install graphifyy` on every machine, a launcher (`scripts/graphify.mjs`) to find it, a measurement script (`scripts/measure-graphify.mjs`), `.graphifyignore`, and a rebuild step in two skills.
- **Two ways to get the same graph.** The Graphify connector of claude.ai indexes the repository on GitHub and offers the same queries (and more: callers, impact, linked tests), without anything in the repository.
- **The code base is small** (~130 production classes): Grep and `buscador-referencias` answer dependency questions well, and they always see the current worktree, including uncommitted changes.

## Decision

- Remove Graphify from the repository: `scripts/graphify.mjs`, `scripts/measure-graphify.mjs`, `.graphifyignore`, its `.gitignore`/`.dockerignore` entries, the `permissions.allow` entry, the commands in `CLAUDE.md`, and every use in `buscador-referencias`, `plan-feature`, `implement-plan` and `implement-plan-team`.
- **Grep + LSP.** Code searches use Grep/Glob, directly or through `buscador-referencias`. For references, definitions and implementations of Java symbols, the `jdtls-lsp` plugin (Anthropic's official marketplace, Eclipse JDT Language Server) gives Claude Code an `LSP` tool that resolves names like the compiler and sees the current worktree. It is enabled for the project in `.claude/settings.json` (`enabledPlugins`); `buscador-referencias` and `plan-feature` use it first for those questions and fall back to Grep when it is missing or returns nothing.
- `jdtls` is installed per machine and must be on the `PATH` (it needs Java 21, already required by the build). Without it, everything works with Grep, as before.
- The claude.ai Graphify connector is not part of the workflow: no skill or agent depends on it. It is an account setting of the user, not a repository setting.

## Alternatives

- **Keep ADR 0010 as is:** keeps the 32 % saving on dependency questions at the price of the toolchain above.
- **Grep only:** simplest, but "who calls this method" by name is ambiguous (the reason ADR 0010 sent methods to Grep anyway) and slower on wide questions.
- **Switch to the Graphify connector:** no local install, but it only sees what is pushed to the default branch (`Hexagonal`), not the worktree in progress, and it ties the skills to one user's account.

## Consequences

- One tool fewer and no Python requirement for development (Schemathesis still needs Python, for the contract tests, ADR 0013).
- Dependency questions use the LSP when `jdtls` is installed (exact answers, method level included) and Grep otherwise.
- The first LSP query of a session waits for `jdtls` to index the project; it uses noticeably more memory than Grep.
- Local `graphify-out/` folders left in existing checkouts can be deleted.
