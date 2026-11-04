# 0001 · AI architecture with Claude Code

- **Status:** accepted · 2026-10-23

## Context

The project is a way to practise DDD in Java and working with agents at the same time. [curso-claude](https://github.com/dawnrock/curso-claude) already has a proven flow: GitHub issues → plan → implementation in a worktree with TDD → PR, backed by skills, hooks and GitHub Actions. That project sent its notices to Slack, which is not used in this one.

## Decision

- Reuse that flow adapted to Java/Maven: `CLAUDE.md`, `/commit`, the `plan-feature` and `implement-plan` skills, the hook that blocks the backlog and the `claude.yml` and `claude-code-review.yml` workflows.
- Add pieces specific to the domain: the `ddd-building-block` skill (with `references/`, `templates/` and `scripts/`), the `hexagonal-adapter` skill, the `revisor-ddd` and `buscador-referencias` agents, and a deterministic `ci.yml`.
- Write hooks and scripts in **Node** instead of bash + `jq`, so they work the same on Windows, Linux and in CI.
- Replace Slack with **Telegram** (Bot API): immediate push to the phone, free, no mail server. Email (SMTP) was discarded because it needs mail credentials and arrives later.

## Consequences

- Tasks always start from an issue with a phase milestone; the plan is versioned in `docs/plans/` and published on the issue.
- Notifications are optional: without a token, nothing fails.
- The Claude workflows need the `CLAUDE_CODE_OAUTH_TOKEN` secret (`claude setup-token`) and, since GitHub runs the issue workflows from the default branch, that branch must be the active phase's (ADR 0002).

Details of each piece: [docs/ia/arquitectura-ia.md](../ia/arquitectura-ia.md).
