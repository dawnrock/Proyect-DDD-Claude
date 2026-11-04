# 0007 · Back/web team with the contract first

- **Status:** accepted · 2026-10-23

## Context

The phase 1 features touch the back (domain, application, persistence and REST) and the web (Thymeleaf) at the same time. Doing them in sequence means waiting for the back before starting the screens. The Claude Code course (chapter 13) proposes **agent teams**: a lead and several teammates with their own context, who talk to each other and share a task list, each one in its own git worktree.

With Thymeleaf, the web does not consume the REST API: its controllers call the application services inside the same application (ADR 0005).

## Decision

- For each issue that touches back and web (label `equipo`), a **contract** is agreed first in `docs/contracts/<slug>.md`, covering the two real boundaries:
  - **Web ↔ back:** application service (signatures), commands and views (Immutables) and errors.
  - **REST:** the fragment of `docs/api/openapi.yaml`.
- The lead creates the contract's types without logic. The `backend-dev` and `web-dev` agents review it and agree on it, and it is **frozen** in a commit.
- Each agent implements its side in parallel in its worktree. The web tests against the mocked service (`@MockitoBean`) until integration.
- Contract changes are renegotiated through the lead and recorded in the contract itself. Nobody changes it in their own branch.
- The `implement-plan-team` skill orchestrates it. `plan-feature` adds `T0 · Contract` and the `Side` field to the tasks.

## Alternatives

- **The web consumes the REST API** (`RestClient`, BFF pattern): a single OpenAPI contract, but the application would call itself over HTTP and the web would stop being one more adapter of the same use cases in phase 2. Discarded.
- **Subagents instead of a team:** cheaper, but they do not talk to each other, so they cannot negotiate the contract. They are still used for single-side tasks.

## Consequences

- Back and web move forward at the same time; integration only joins two branches that already compile against the same types.
- The contract documents each feature and can be reviewed in the PR.
- Each teammate is a full Claude instance: a team issue uses 2 to 3 times more quota. It is only used when the issue touches both sides.
- In phase 2, the web ↔ back contract becomes the input ports' (`*Commands` / `*Queries`), without changing the flow.
