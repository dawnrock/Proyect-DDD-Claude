# 0002 · One branch per snapshot

- **Status:** accepted · 2026-10-23

## Context

We want to keep two complete versions of the application apart (layered monolith and hexagonal) so they can be compared, besides the course's starting point.

## Decision

| Branch | Content | Rule |
| ------ | ------- | ---- |
| `master` | Copy of DDD-Lab-Java (Lab 22) | Frozen: receives no commits or PRs |
| `Monolithic` | AI architecture + phase 1 | Base of the PRs of the *Phase 1* milestone. Default branch while phase 1 lasts |
| `Hexagonal` | Phase 2 | Created from `Monolithic` when phase 1 closes (tag `v1-monolito`). Becomes the default branch |

Each issue's PRs go against its phase's branch (the skills infer it from the `fase:*` label). Each phase ends with a tag (`v1-monolito`, `v2-hexagonal`).

## Alternatives

- Two repositories: duplicates the AI configuration, the issues and the secrets.
- A single `main` with tags: the snapshots would not be visible as browsable branches.

## Consequences

- `master` is not the repository's default branch (GitHub runs the issue and comment workflows from the default branch).
- Comparing the phases is a plain diff: `git diff v1-monolito v2-hexagonal`.
