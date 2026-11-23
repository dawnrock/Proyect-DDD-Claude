# Plan: Limit customer sign-ups per IP (web and API)

- **Issue:** [#131](https://github.com/dawnrock/Proyect-DDD-Claude/issues/131)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-23
- **Status:** draft
- **Total estimate:** ~80 min (9 tasks)

## Goal

The public customer sign-up (`/signup` on the web, `POST /api/customers` on the API, #103) has no limit: anyone can create customers and accounts without end, and since everything lives in memory that can also exhaust the 512 MB Render instance. Limit **successful sign-ups per client IP** within a time window (by default 3 per hour) and, so that an attacker with many IPs cannot keep filling the memory, **all successful sign-ups together** in the same window (by default 50 per hour); both configurable, in the web and the API alike, reusing the client IP of #133 (`ClientIpResolver`) and the in-memory sliding-window approach of #130 (ADR 0017). Over either limit the API answers 429 `ProblemDetail` with `Retry-After`, and the web shows a message in the sign-up form, already at step 1 so nobody fills in the three steps for nothing. Limits agreed with the user on 2026-11-23.

### Out of scope

- A limit measured in memory (bytes): the global cap counts sign-ups, which is a good enough proxy.
- CAPTCHA, e-mail verification or real persistence (ADR 0012 risks that stay).
- Grouping IPv6 addresses by /64 (pending item of #133, shared with the login limit).
- Any change to the domain or to the application layer: the limit is an input-adapter concern, like the login limit.

## Context

The limit lives in the root of `adapter.in`, next to `CustomerRegistration` (shared by the web and REST adapters) and the login-limit classes. `CustomerRegistration.register` is already `synchronized`, so reserving a sign-up slot there is atomic with the sign-up itself: a burst of simultaneous requests cannot exceed the limit. The slot is **reserved** before calling the use case and **released** if the sign-up fails (duplicate document, username taken, short password, business rule), so only sign-ups that created a customer count: failed ones use no memory, and a user fixing a typo is not punished.

`LoginAttempts` is not reused as is: its semantics (failures per user + IP, cleared on success, lockout counted from the last failure) differ. `SignUpLimit` is a smaller sibling with the same structure (map IP → deque of instants, `Clock`, periodic prune) and its window counts from the **oldest** sign-up that still counts (a real sliding window: "3 per hour"). It keeps two windows: one per IP and one global (a single deque of at most `max-total` instants). A reservation needs room in both, checked and recorded in the same synchronized operation; when one is full, the time left is that window's (the longer of the two if both are). The global cap bounds the memory the sign-ups can take per window whatever the number of IPs; its cost is that, while it is full, legitimate users cannot sign up either (accepted: it only happens under an attack, and it frees up as the window slides).

The web checks the limit (read-only, `lockedFor(ip)`) at step 1 too: the wizard state is only a small `SignUpForm` in a session that expires, so the early check does not save memory, but it spares the user filling in three steps that would be rejected. The final step still reserves the slot, since the limit may fill up meanwhile.

The client IP: today only `ClientIpAuthenticationDetailsSource` turns a request into an IP (joining the `X-Forwarded-For` headers). That logic moves to `ClientIpResolver.resolve(HttpServletRequest)` and the resolver becomes a bean, so the sign-up controllers use exactly the same IP as the login limit.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in` | `ClientIpResolver.java`, `ClientIpAuthenticationDetailsSource.java`, `SecuritySupport.java` | `resolve(HttpServletRequest)`; resolver bean; details source delegates |
| `adapter.in` | `SignUpLimitProperties.java` (new), `SignUpLimit.java` (new), `SecuritySupport.java` | `app.security.sign-ups.{max-per-ip,max-total,window}`; in-memory counters (per IP and global); bean |
| `adapter.in` | `TooManySignUpsException.java` (new), `CustomerRegistration.java` | `register(command, password, ip)` reserves and releases the slot |
| `adapter.in.rest` | `CustomerController.java`, `RestExceptionHandler.java` | passes the IP; 429 with `Retry-After` |
| `adapter.in.web` | `SignUpController.java`, `templates/signup/*.html` | passes the IP; message in the form at step 1 and at the last step |
| `resources` | `application.properties` | default values |
| docs | `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `scripts/contract-test.mjs`, `docs/adr/0018-sign-up-limit.md`, `docs/adr/0012-*.md`, `docs/adr/README.md`, `docs/ia/*` | contract 2.2.0, ADR, project status |

## Tasks

### [ ] T1 — Client IP from a request in `ClientIpResolver` (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/ClientIpResolver.java`, `adapter/in/ClientIpAuthenticationDetailsSource.java`, `adapter/in/SecuritySupport.java`, `ClientIpResolverTests.java`
- **Depends on:** —
- **What to do:**
  1. Test first: `resolve(HttpServletRequest)` with `MockHttpServletRequest` joins several `X-Forwarded-For` headers in order (the client's first, the trusted proxy's after) and gives the same IP as `resolve(remoteAddr, header)`; without the header, the connection address.
  2. Move the header joining from `ClientIpAuthenticationDetailsSource.buildDetails` to the new method; the details source calls it.
  3. `SecuritySupport` exposes `ClientIpResolver` as a bean and builds the details source from it.
- **Acceptance criteria:**
  - [ ] New `ClientIpResolverTests` cases pass; `ClientIpAuthenticationDetailsSourceTests`, `TrustedProxiesConfigurationTests` and `TokenControllerTests` (#133 case) still pass unchanged.

### [ ] T2 — `SignUpLimit`: sign-ups per IP and in total, in a sliding window (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/SignUpLimitProperties.java` (new), `adapter/in/SignUpLimit.java` (new), `adapter/in/SecuritySupport.java`, `SignUpLimitTests.java` (new)
- **Depends on:** —
- **What to do:**
  1. Tests first, with a mutable `Clock`: `tryReserve(ip)` gives empty up to `maxPerIp` reservations and then the remaining time (oldest reservation + window − now); after the window passes it allows again; `release(ip)` gives back the last reservation of the IP **and** of the global window; IPs are independent **until `maxTotal` reservations from different IPs fill the global window, which then rejects any IP** with the global time left (the longer one if both are full); a rejected reservation records nothing; `lockedFor(ip)` answers the same without reserving; properties default to 3, 50 and `PT1H`, reject non-positive values and require `maxTotal >= maxPerIp`.
  2. `SignUpLimitProperties` (`@ConfigurationProperties("app.security.sign-ups")`, record like `LoginAttemptsProperties`).
  3. `SignUpLimit`: `synchronized`, map IP → `Deque<Instant>` plus one global `Deque<Instant>`, drop expired on access and prune every N reservations (same pattern as `LoginAttempts`).
  4. Bean in `SecuritySupport` (with the core `Clock` if available, like `loginAttempts`) and register the properties in `@EnableConfigurationProperties`.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=SignUpLimitTests` green.

### [ ] T3 — `CustomerRegistration` reserves a slot per IP (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/TooManySignUpsException.java` (new), `adapter/in/CustomerRegistration.java`, `CustomerRegistrationTests.java`
- **Depends on:** T2
- **What to do:**
  1. Tests first: with the per-IP or the global limit reached, `register(command, password, ip)` throws `TooManySignUpsException` with `retryAfter()` and calls neither `signUp` nor `createUser`; a failed sign-up (username taken, duplicate document, short password) does not consume the slot; a successful one does.
  2. `TooManySignUpsException(Duration retryAfter)` (`RuntimeException`, message in Spanish: "Demasiadas altas desde tu conexión…").
  3. `register` takes the IP, calls `tryReserve` first and `release` in a `catch` that rethrows; update the two callers (they still compile by passing the IP in T4/T5; in this task pass a placeholder only if needed to keep `verify` green, removed in T4/T5).
- **Acceptance criteria:**
  - [ ] `CustomerRegistrationTests` green, including the three new cases.

### [ ] T4 — API: IP to the sign-up and 429 with `Retry-After` (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/rest/CustomerController.java`, `adapter/in/rest/RestExceptionHandler.java`, `CustomerControllerTests.java`
- **Depends on:** T1, T3
- **What to do:**
  1. Tests first (`@WebMvcTest`): over the limit `POST /api/customers` answers 429 `application/problem+json` with `Retry-After` in seconds (rounded up, ≥ 1); behind Render's proxy and Cloudflare the limit is for the client IP of `X-Forwarded-For`, not Cloudflare's (as the #133 case of `TokenControllerTests`).
  2. `CustomerController` receives the `HttpServletRequest` and passes `clientIpResolver.resolve(request)`.
  3. `RestExceptionHandler` handles `TooManySignUpsException` like `TooManyLoginAttemptsException` (shared helper for the seconds).
- **Acceptance criteria:**
  - [ ] `CustomerControllerTests` green; existing sign-up cases unchanged.

### [ ] T5 — Web: IP to the sign-up and message at the last step (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/web/SignUpController.java`, `templates/signup/account.html` (if the global error is not already shown), `SignUpControllerTests.java`
- **Depends on:** T1, T3
- **What to do:**
  1. Tests first: over the limit, `POST /signup/account` stays on the account step with a global error that says when it can be tried again (minutes, rounded up), keeps the form in the session, does not sign in; with htmx the same fragment is returned.
  2. `SignUpController.submitAccount` passes `clientIpResolver.resolve(request)` and catches `TooManySignUpsException` → `result.reject("tooManySignUps", message)`.
- **Acceptance criteria:**
  - [ ] `SignUpControllerTests` green; the message is visible in `signup/account` (check the template renders global errors).

### [ ] T5b — Web: check the limit at step 1 (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/web/SignUpController.java`, `templates/signup/data.html`, `SignUpControllerTests.java`
- **Depends on:** T5
- **What to do:**
  1. Tests first: over the per-IP or the global limit, `POST /signup` stays on step 1 with the same message (minutes left) and does not complete the personal data, so `/signup/access` still redirects to `/signup`; `GET /signup` shows the notice too; under the limit nothing changes and nothing is reserved (`lockedFor`, never `tryReserve`).
  2. `submitData` (and `data`) ask `signUpLimit.lockedFor(clientIpResolver.resolve(request))`; when locked, a global error / model attribute shown by `signup/data.html`.
- **Acceptance criteria:**
  - [ ] `SignUpControllerTests` green; step 1 cannot be passed over the limit, and the final step still rejects if the limit filled up meanwhile (T5 test).

### [ ] T6 — Contract 2.2.0 and contract tests (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `scripts/contract-test.mjs`
- **Depends on:** T4
- **What to do:**
  1. `signUp` gets `'429': $ref: '#/components/responses/TooManyRequests'` and a sentence in its description (3 per hour per IP and 50 per hour in total by default, ADR 0018); check the shared response's text/example fits both operations (generalise it if it only talks about login).
  2. `info.version` 2.1.0 → **2.2.0** (compatible addition, ADR 0014); CHANGELOG entry.
  3. `scripts/contract-test.mjs` starts the jar also with `--app.security.sign-ups.max-per-ip=1000000` and `--app.security.sign-ups.max-total=1000000`, so Schemathesis tests the contract and not the limit.
  4. `npx @redocly/cli lint docs/api/openapi.yaml`, `oasdiff breaking` (no breaking change) and `node scripts/contract-test.mjs` (if Schemathesis is available).
- **Acceptance criteria:**
  - [ ] Lint without errors; `node scripts/check-contract-version.mjs` accepts the bump.

### [ ] T7 — Defaults and ADR 0018 (~5 min)

- **Layer:** config · docs
- **Files:** `src/main/resources/application.properties`, `docs/adr/0018-sign-up-limit.md` (new), `docs/adr/0012-customer-self-sign-up.md`, `docs/adr/README.md`
- **Depends on:** T2
- **What to do:**
  1. `app.security.sign-ups.max-per-ip=3`, `app.security.sign-ups.max-total=50` and `app.security.sign-ups.window=1h`, with a comment (#131, ADR 0018).
  2. ADR 0018: context (memory on Render, #103 risk), decision (successful sign-ups per client IP and in total, sliding window, reserve/release, check at step 1 of the web, input adapter), alternatives (reuse `LoginAttempts`, a library like Bucket4j, CAPTCHA, a memory-based cap) and consequences (shared IPs/NAT, in memory, a full global cap also stops legitimate users until the window slides, IPv6 /128).
  3. ADR 0012: the "no rate limit" risk now points to ADR 0018. Index row in `docs/adr/README.md`.
- **Acceptance criteria:**
  - [ ] `./mvnw verify` green; `DddLabApplicationTests` loads the context with the new properties.

### [ ] T8 — Project status, including the verified Render check of #133 (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `docs/ia/pendientes.md`, `docs/ia/decisiones.md`, `docs/ia/historial.md`
- **Depends on:** T1-T7 (and T5b)
- **What to do:**
  1. **Render check of #133 done (user, 2026-11-23):** the lock warning in Render's logs showed the client's own IP (`46.6.116.139`), not Cloudflare's. Remove step 1 of "Next step" in `estado-del-proyecto.md` and the bullet "Check on Render after deploying #133" in `pendientes.md` (keep `node scripts/check-cloudflare-ips.mjs` now and then as a short note under the #130 items); record the verification in one line in `historial.md` (row of #133 or of this PR).
  2. `estado-del-proyecto.md`: #131 to done (PR number), next step updated, "Last updated".
  3. `pendientes.md`: remove "or rate limit (#131)" from the #103 risks; add the new pending items of #131 (in memory, NAT, a full global cap blocks legitimate users, IPv6) under a "Sign-up limit (#131, ADR 0018)" bullet. `decisiones.md`: one line with the decision; `historial.md`: a row for the PR.
- **Acceptance criteria:**
  - [ ] No mention of the pending Render check remains in `docs/ia/`; the CI project-status check passes.

## Global acceptance criteria

**Specific:**

- [ ] Over the limit, an IP cannot sign up more customers until the window passes, neither on the web (message in the form, from step 1) nor on the API (429 `ProblemDetail` with `Retry-After`).
- [ ] Over the global cap (50 per hour by default), no IP can sign up until the window frees room, whatever the number of IPs used.
- [ ] Failed sign-ups do not count towards the limit; the IP is the client's behind Render and Cloudflare (same `ClientIpResolver` as the login limit).
- [ ] `pendientes.md` no longer lists the sign-up rate limit as an accepted risk, nor the Render check of #133.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it with `info.version` bumped per ADR 0014 and an entry in `docs/api/CHANGELOG.md`, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes.
- [ ] Any new business term is in `docs/dominio/glosario.md` (no new term expected: "sign-up" already exists).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Defaults agreed with the user (2026-11-23):** 3 successful sign-ups per IP per hour, a global cap and the check at step 1. The global value, 50 per hour, is a proposal: configurable through `app.security.sign-ups.max-total`.
- **Global cap as a denial of service:** someone with many IPs can fill it and stop legitimate sign-ups for up to an hour. Accepted: it is what keeps the memory bounded, and nothing else is affected (login and operations keep working).
- **NAT:** a family or office behind one IP shares the per-IP quota.
