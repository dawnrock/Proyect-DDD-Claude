# Plan: Semantic versioning of the OpenAPI contract and breaking-change detection in the CI

- **Issue:** [#112](https://github.com/dawnrock/Proyect-DDD-Claude/issues/112)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-17
- **Status:** draft
- **Total estimate:** ~45 min (5 tasks)

## Goal

`info.version` of `docs/api/openapi.yaml` is still `0.1.0` after several incompatible changes (#18, #103, #108), which only appear as notes in operation descriptions. Treat the contract as a product (OpenAPI course 1.6, 3.4 phase 7, 7.3, 8.9): a versioning policy, a stable baseline, a changelog, and a CI check that fails when the contract changes without the right version bump.

### Out of scope

- Publishing several versions side by side (`/v1`, `/v2`): there is one API and one contract.
- Versioning the Java artifact (`pom.xml` `version`).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| docs | `docs/adr/0014-contract-versioning.md`, `docs/adr/README.md` | SemVer policy for `info.version`, what counts as major/minor/patch, how the CI enforces it |
| docs | `docs/api/openapi.yaml` | `info.version` `1.0.0`; the "Incompatible change" notes leave the descriptions for the changelog |
| docs | `docs/api/CHANGELOG.md` (new), `docs/api/style-guide.md` | History of the contract (1.0.0 summarises #5-#111); the guide points to the policy |
| scripts | `scripts/check-contract-version.mjs` (new), `scripts/README.md` | Compares the PR's contract with the base branch's |
| CI | `.github/workflows/ci.yml` | `setup-go` + `go install github.com/oasdiff/oasdiff@v1.35.0` + the check, on pull requests |
| Claude Code (approved by the user, 2026-11-17) | `.claude/skills/plan-feature/SKILL.md`, `.claude/skills/plan-feature/assets/template.md` | The API task bumps `info.version` and adds a changelog entry |

Policy (ADR 0014):

- **Major:** an incompatible change, as `oasdiff breaking` reports it at `ERR` level (an operation or field removed, a field made required, values restricted, a status code changed…).
- **Minor:** a compatible addition (new operation, new optional field, new response).
- **Patch:** documentation only (descriptions, examples, servers).

The check (`scripts/check-contract-version.mjs`): if the PR does not touch `docs/api/openapi.yaml`, it passes; if it does, `info.version` must be higher than the base's, and if `oasdiff breaking --fail-on ERR` finds incompatible changes, the major number must be higher too.

Decisions taken while planning (clear and reversible, so not asked):

- **Baseline `1.0.0`:** after the OpenAPI course review (#107-#113) the contract is complete and checked against the implementation; the earlier incompatible changes are recorded in the changelog, not renumbered.
- **oasdiff `v1.35.0`** (2026-10-26, more than two weeks old), installed with `go install` (Go 1.26) in the CI; tried locally on the real history: from the contract before #108 to today it reports 15 incompatible changes (the amount limits and the `currency` enum), and none between a file and itself.
- **Every change bumps the version**, patch included, so the version always identifies one exact contract.

## Tasks

### [ ] T1 — Policy, baseline and changelog (~10 min)

- **Layer:** docs
- **Files:** ADR 0014, `docs/adr/README.md`, `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `docs/api/style-guide.md`
- **Depends on:** —
- **What to do:**
  1. ADR 0014 with the policy above, alternatives (no version check; version in the URL; Redocly or another diff tool) and consequences.
  2. `info.version: 1.0.0`; move the two "Incompatible change" notes of `openAccount` to the changelog; the changelog lists 1.0.0 with what the contract holds and the incompatible changes that led to it (#18, #103, #108); the style guide's "incompatible changes" line points to ADR 0014.
- **Acceptance criteria:**
  - [ ] Redocly clean; ADR listed in the index.

### [ ] T2 — `check-contract-version.mjs` (~15 min)

- **Layer:** build
- **Files:** `scripts/check-contract-version.mjs`, `scripts/README.md`
- **Depends on:** T1
- **What to do:**
  1. Node script without npm dependencies: reads the base contract with `git show origin/<BASE_REF>:docs/api/openapi.yaml` and the head's from disk, compares `info.version` (SemVer), runs `${OASDIFF:-oasdiff} breaking <base> <head> --fail-on ERR`, and exits non-zero with a clear message in Spanish when a rule is broken.
  2. Validate the four cases with temporary contracts: unchanged contract → pass; changed contract, same version → fail; incompatible change with a minor bump → fail; incompatible change with a major bump → pass; documentation change with a patch bump → pass.
- **Acceptance criteria:**
  - [ ] Each case gives the expected exit code; this PR's own change (0.1.0 → 1.0.0) passes.

### [ ] T3 — Run it in the CI (~5 min)

- **Layer:** build
- **Files:** `.github/workflows/ci.yml`
- **Depends on:** T2
- **What to do:**
  1. On pull requests, after the Redocly lint: `actions/setup-go@v5` (Go 1.26), `go install github.com/oasdiff/oasdiff@v1.35.0`, and `node scripts/check-contract-version.mjs` with `BASE_REF`.
- **Acceptance criteria:**
  - [ ] The PR's `verify` job runs the step and passes (checked on the PR).

### [ ] T4 — `plan-feature` asks for the bump (~5 min)

- **Layer:** docs
- **Files:** `.claude/skills/plan-feature/SKILL.md`, `.claude/skills/plan-feature/assets/template.md`
- **Depends on:** T1
- **What to do:**
  1. The API task of a plan also bumps `info.version` following ADR 0014 and adds a `docs/api/CHANGELOG.md` entry; the template's fixed API criterion says so.
- **Acceptance criteria:**
  - [ ] Both files consistent with ADR 0014.

### [ ] T5 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`, `docs/ia/arquitectura-ia.md`
- **Depends on:** T4
- **What to do:**
  1. `decisiones.md`: the policy and the check; `arquitectura-ia.md`: the CI row mentions it.
- **Acceptance criteria:**
  - [ ] Annexes updated.

## Global acceptance criteria

**Specific:**

- [ ] A PR that changes the contract without the right version bump fails in the CI.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish) (n/a: no JUnit tests; the script is validated case by case).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR): `pom.xml` unchanged; oasdiff is a CI tool justified in ADR 0014.
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes (the API does not change; the contract document does: both are run).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **oasdiff's view of "breaking"** can be stricter than the project's (for example, adding a `minimum` that only documents what was already rejected, as in #108). Such a change still needs a major bump; the ADR says the tool decides, so the rule stays mechanical.
- **CI time:** `go install` compiles oasdiff (about a minute); a cached binary could save it later.
