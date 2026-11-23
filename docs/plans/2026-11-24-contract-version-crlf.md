# Plan: Contract version rule reads CRLF contracts

- **Issue:** [#159](https://github.com/dawnrock/Proyect-DDD-Claude/issues/159)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-24
- **Status:** in progress
- **Total estimate:** ~20 min (3 tasks)

## Goal

`scripts/check-contract-version.mjs` (#112, ADR 0014) cannot find `info.version` when the contract has CRLF line endings: in JavaScript `.` does not match `\r`, so the `info:` block regex fails. On Windows with `core.autocrlf=true` the working copy of `docs/api/openapi.yaml` has CRLF while the base contract from `git show` has LF, so the local run exits with 2. The CI (Linux, LF) is not affected.

- **Current:** with a CRLF contract the script exits with 2 (`No encuentro info.version`). With the version regex fixed alone, a CRLF copy of an unchanged contract would still differ from the LF base and be reported as "the contract changes but the version does not go up".
- **Expected:** both contracts are normalised to LF right after reading, so CRLF vs LF gives the same result as LF vs LF in every case (unchanged, not bumped, bumped).

### Out of scope

- A `.gitattributes` rule for `docs/api/openapi.yaml` (`text eol=lf`): not added. Normalising in the script covers every case, including a file edited with CRLF before the commit, which the rule would not; oasdiff and Redocly already read CRLF YAML. Decided by Claude under `/advance-issues` (reversible).
- Other scripts: reviewed, none needs a change. `check-project-status.mjs`, `check-cloudflare-ips.mjs` and `clean-merged-worktrees.mjs` already split with `\r?\n`; `measure-graphify.mjs` normalises its Markdown and `JSON.parse` ignores a trailing `\r`; `contract-test.mjs` and `graphify.mjs` do not parse text by lines; `seed-issues.mjs` only prints its own help text.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| scripts | `scripts/check-contract-version.mjs` | Normalise `\r\n` and `\r` to `\n` after reading both contracts |
| scripts (tests) | `scripts/check-contract-version.test.mjs` | New, with Node's built-in runner (`node:test`, no dependency): the two-file mode with CRLF vs LF gives the same exit code and message as LF vs LF |
| ci | `.github/workflows/ci.yml` | New step `node --test "scripts/*.test.mjs"` |
| docs | `scripts/README.md`, `docs/ia/*` | How to run the script tests; project status |

## Tasks

### [x] T1 — CRLF contracts give the same result as LF ones (~10 min)

- **Layer:** scripts
- **Files:** `scripts/check-contract-version.test.mjs`, `scripts/check-contract-version.mjs`
- **Depends on:** —
- **What to do:**
  1. Red: tests that run the script in two-file mode (`spawnSync(process.execPath, …)`) on copies of `docs/api/openapi.yaml` in a temporary folder, the base in LF and the new one in CRLF, and compare exit code and message with the LF-LF run: unchanged contract (0), changed without bump (1), bumped (reaches oasdiff; with `OASDIFF` pointing to a missing binary it exits with 2 and `No se pudo ejecutar`, so no oasdiff is needed).
  2. Green: `readFileSync(…).replace(/\r\n?/g, '\n')` for both contracts, with a comment on why.
- **Acceptance criteria:**
  - [x] The CRLF tests fail before the fix and pass after it; `node --test "scripts/*.test.mjs"` green (7 tests; without the fix the 6 CRLF ones fail). The test runs the script with `NODE_NO_WARNINGS=1`, because Node's warnings carry the PID and would make two equal outputs differ.

### [x] T2 — Run the script tests in the CI (~5 min)

- **Layer:** ci
- **Files:** `.github/workflows/ci.yml`, `scripts/README.md`
- **Depends on:** T1
- **What to do:**
  1. A step `Tests de los scripts (node:test)` after the project status check: `node --test "scripts/*.test.mjs"` (Node 22 expands the glob itself, also on Windows).
  2. `scripts/README.md`: the script reads LF and CRLF contracts, and how to run the tests.
- **Acceptance criteria:**
  - [x] The step passes on the PR (checked locally; the PR's `verify` run confirms it).

### [ ] T3 — Check the Windows scenario by hand (~5 min)

- **Layer:** tests
- **Files:** —
- **Depends on:** T1
- **What to do:**
  1. Convert the working copy of `docs/api/openapi.yaml` to CRLF (as `core.autocrlf=true` leaves it on Windows), run `BASE_REF=Hexagonal node scripts/check-contract-version.mjs` and check it says the contract does not change; revert the file.
- **Acceptance criteria:**
  - [ ] The git mode with a CRLF working copy reads `info.version` (no exit 2).

## Global acceptance criteria

**Specific:**

- [ ] With a CRLF working copy, `BASE_REF=Hexagonal node scripts/check-contract-version.mjs` reads `info.version` (T3, simulated on Linux; the user can confirm on Windows).
- [ ] With one contract in CRLF and the other in LF, the two-file mode gives the same result as with both in LF (T1).
- [ ] The CI stays green and runs the script tests; `scripts/README.md` mentions them.
- [ ] `pendientes.md` and `estado-del-proyecto.md` updated.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit: no Java change.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish): n/a for JUnit; the `node:test` names are Spanish sentences, like the `@DisplayName`s.
- [ ] No dependencies added beyond the planned ones (or justified in an ADR): `node:test` is part of Node.
- [ ] If the API changes: n/a (no API change).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English (`scripts/README.md` follows its current language).
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- The real Windows run can only be confirmed by the user; T3 reproduces it with a CRLF working copy on Linux.
