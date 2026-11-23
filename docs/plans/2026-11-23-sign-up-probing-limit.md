# Plan: Limit probing of usernames and documents in the customer sign-up

- **Issue:** [#139](https://github.com/dawnrock/Proyect-DDD-Claude/issues/139)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-23
- **Status:** in progress
- **Total estimate:** ~45 min (6 tasks)

## Goal

The sign-up limit of #131 (ADR 0018) only counts successful sign-ups: a failed one gives its slot back. So anyone can keep trying usernames or documents, on `/signup` and on `POST /api/customers`, and learn whether they exist from the 422 "El usuario X ya existe" (`UsernameTakenException`) or "Ya eres cliente de Mon-A Bank…" (`DuplicateCustomerException`). Add a second per-IP counter for **failed sign-ups that reveal an existing username or document**: after **10 in the last hour** (configurable) the IP cannot try more sign-ups until the window passes, with the same answers as the #131 limit (API 429 `TooManySignUps` with `Retry-After`, web notice from step 1). Agreed with the user on 2026-11-23: only those two failures count, with 10 per hour.

### Out of scope

- Other failures (short password, minimum deposit, wrong DNI letter, validation): they reveal nothing, so a user filling the form in wrong is never blocked.
- The administrator's account opening (not public).
- A global cap on failures, IPv6 /64 grouping and shared storage (pending items of #131 and #133).

## Context

Everything stays in the root of `adapter.in`, like #131; domain and application do not change.

- `SignUpLimit` gets a third sliding window: failures per IP (`Map<String, Deque<Instant>>`, at most `maxFailuresPerIp` instants, same `window`). `lockedFor` and `tryReserve` also answer "locked" when that window is full (the time left is the longest of the full windows). A new `recordFailure(ip)` notes a failure.
- `CustomerRegistration.register` already releases the slot in a `catch` when no customer was created. For `UsernameTakenException` and `DuplicateCustomerException` it also calls `recordFailure`, in the same synchronized call, so a burst of probes cannot exceed the limit. A `UsernameTakenException` thrown **after** the customer was created (race on the user creation) does not count: it is not a probe.
- The web and REST adapters need no change: they already translate `TooManySignUpsException` and the web already checks `lockedFor` at step 1. Their existing tests cover the answers, and the unit tests of `SignUpLimit` and `CustomerRegistration` cover the new behaviour.
- API: no new response or status, only the description of `signUp` and of `TooManySignUps` change → patch version (2.2.1 → 2.2.2, ADR 0014).

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in` | `SignUpLimitProperties.java`, `SignUpLimit.java` | `maxFailuresPerIp` (default 10); failures window; `recordFailure` |
| `adapter.in` | `CustomerRegistration.java` | records the revealing failures |
| `resources` | `application.properties` | `app.security.sign-ups.max-failures-per-ip=10` |
| test | `DddLabApplicationTests.java`, `scripts/contract-test.mjs` | raise the new limit too (all their sign-ups come from one IP) |
| docs | `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `docs/adr/0018-sign-up-limit.md`, `docs/ia/*` | contract 2.2.2, ADR amended, project status |

## Tasks

### [x] T1 — Failures window in `SignUpLimit` (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/SignUpLimitProperties.java`, `adapter/in/SignUpLimit.java`, `SignUpLimitTests.java`
- **Depends on:** —
- **What to do:**
  1. Tests first: `maxFailuresPerIp` defaults to 10 and must be positive; after `maxFailuresPerIp` calls to `recordFailure(ip)` both `lockedFor(ip)` and `tryReserve(ip)` give the time left until the oldest failure expires, and `tryReserve` reserves nothing; other IPs are not affected; after the window it allows again; with the per-IP sign-up window also full, the time left is the longest.
  2. Add the property (record component, default, validation) and the window, pruned like the others.
- **Acceptance criteria:**
  - [x] `./mvnw test -Dtest=SignUpLimitTests` green.

### [x] T2 — `CustomerRegistration` records the revealing failures (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/CustomerRegistration.java`, `CustomerRegistrationTests.java`
- **Depends on:** T1
- **What to do:**
  1. Tests first (with `maxFailuresPerIp` = 2): two sign-ups failing with a taken username or a duplicate document from one IP → the next valid sign-up from that IP throws `TooManySignUpsException` and creates nothing; a short password or an insufficient deposit does not count; a valid sign-up from another IP still works; a username taken only when creating the user (after the customer was saved) does not count.
  2. In the `catch` that releases the slot, call `signUpLimit.recordFailure(clientIp)` for `UsernameTakenException` and `DuplicateCustomerException`.
- **Acceptance criteria:**
  - [x] `CustomerRegistrationTests` green, including the new cases.

### [x] T3 — Defaults and tests that sign up many times from one IP (~5 min)

- **Layer:** config
- **Files:** `src/main/resources/application.properties`, `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java`, `scripts/contract-test.mjs`
- **Depends on:** T1
- **What to do:**
  1. `app.security.sign-ups.max-failures-per-ip=10` with its comment (#139, ADR 0018).
  2. Raise it in `DddLabApplicationTests` and in `contract-test.mjs` (Schemathesis sends many duplicate documents and usernames).
- **Acceptance criteria:**
  - [x] `./mvnw verify` green; `node scripts/contract-test.mjs` passes.

### [x] T4 — Contract 2.2.2 (~5 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`
- **Depends on:** T2
- **What to do:**
  1. `signUp` description: 10 failed sign-ups per hour from one IP because of a taken username or an existing document also give 429. `TooManySignUps` description: also after too many of those failures.
  2. `info.version` 2.2.1 → **2.2.2** (documentation only, ADR 0014); CHANGELOG entry; Redocly lint and `check-contract-version.mjs`.
- **Acceptance criteria:**
  - [x] Lint without errors; the contract version check accepts the bump.

### [x] T5 — ADR 0018 amended (~5 min)

- **Layer:** docs
- **Files:** `docs/adr/0018-sign-up-limit.md`
- **Depends on:** T2
- **What to do:**
  1. Add the failures limit to the decision (only revealing failures, 10 per hour per IP, `max-failures-per-ip`), with the issue and the date agreed with the user; move "probing is not limited" from consequences to the decision; new consequence: a NAT shares the failures quota too.
- **Acceptance criteria:**
  - [x] The ADR describes the three limits consistently with the code.

### [ ] T6 — Project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `docs/ia/pendientes.md`, `docs/ia/decisiones.md`, `docs/ia/historial.md`
- **Depends on:** T1-T5
- **What to do:**
  1. `estado-del-proyecto.md`: #139 done with its PR, next step, "Last updated".
  2. `pendientes.md`: remove "Probing is not limited" from the #131 items; add what the review leaves.
  3. `decisiones.md`: extend the #131 line with the failures limit; `historial.md`: a row for the PR.
- **Acceptance criteria:**
  - [ ] The CI project-status check passes.

## Global acceptance criteria

**Specific:**

- [ ] After 10 sign-ups failing with a taken username or an existing document in the last hour, an IP cannot sign up, even with valid data, on the web (from step 1) or on the API (429 with `Retry-After`).
- [ ] Other failures do not count: a user who makes a couple of mistakes can still sign up.
- [ ] `pendientes.md` no longer lists probing as unlimited.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it with `info.version` bumped per ADR 0014 and an entry in `docs/api/CHANGELOG.md`, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes.
- [ ] Any new business term is in `docs/dominio/glosario.md` (none expected).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Decisions agreed with the user (2026-11-23):** only taken-username and duplicate-document failures count; 10 per IP per hour.
- **Locking out by probing yourself:** someone who tries 10 taken usernames in an hour is blocked for up to an hour; the web already suggests another username after the first, so it is unlikely.
- **NAT:** a network behind one IP shares the failures quota.
