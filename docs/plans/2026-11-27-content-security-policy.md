# Plan: Content-Security-Policy and security headers, without Alpine.js

- **Issue:** [#206](https://github.com/dawnrock/Proyect-DDD-Claude/issues/206)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-27
- **Status:** in progress
- **Total estimate:** ~95 min (11 tasks)

## Goal

Close OWASP A05 (security misconfiguration) for the browser: the web has no Content-Security-Policy (pending since #111 and #124) and sends no `Referrer-Policy` or `Permissions-Policy`. Decided with the user on 2026-11-27: **Alpine.js is removed** (it only drives the "show password" button and the account actions accordion, and forces `'unsafe-eval'`), its two behaviours become plain JavaScript in `app.js`, and the policy is **enforced from day one** (`Content-Security-Policy`, not `-Report-Only`).

Every screen must behave exactly as today, with and without JavaScript.

### Out of scope

- A CSP reporting endpoint (`report-to`).
- `Cross-Origin-Opener-Policy` / `-Embedder-Policy` (COOP would cut Swagger UI's OAuth popup off from its opener).
- Serving Swagger UI from a WebJar instead of jsDelivr (option B of #111).
- The H2 console's own scripts (no CSP on `/h2-console/**`; it is off in `prod`).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in.web` | `ContentSecurityPolicies.java` (new) | Builds the three policies as strings (web with nonce, Swagger UI, API); pure, unit-tested |
| `adapter.in.web` | `CspNonce.java` (new) | Per-request nonce, created on first use and kept as a request attribute; a `@ControllerAdvice` exposes it as `cspNonce` |
| `adapter.in.web` | `WebSecurityConfig.java` | Headers per path (`DelegatingRequestMatcherHeaderWriter`), `Referrer-Policy`, `Permissions-Policy` |
| `adapter.in.rest` | `ApiSecurityConfig.java` | `default-src 'none'; frame-ancestors 'none'`, `Referrer-Policy`, `Permissions-Policy` |
| web resources | `static/js/app.js`, `fragments/password-toggle.html`, `login.html`, `login-link.html`, `signup/access.html`, `accounts/detail.html`, `fragments/layout.html`, `signup/progress.html`, CSS | Alpine out, no inline `style`/`<script>`, nonce on the import map, htmx hardening |
| web resources | `static/api-docs/index.html`, `static/api-docs/init.js` (new) | Swagger UI's inline script moves to a file |
| build | `pom.xml` | `alpinejs` WebJar and its version property removed |
| docs | `docs/adr/0025-security-headers.md` (new), `docs/adr/0016-web-frontend-libraries.md` | Policy and why; Alpine removed |

Policies (the `auth0` origin is `Auth0Properties.baseUrl()` and only appears when Auth0 is configured):

- **Web:** `default-src 'self'; script-src 'self' 'nonce-…'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'; form-action 'self' <auth0>`. `data:` for images because Bootstrap's CSS draws form icons as `data:` SVGs. `form-action` needs Auth0 because Chromium also applies it to the redirect after a form post (logout and the Auth0 sign-up end on the tenant). `frame-ancestors 'self'` matches today's `X-Frame-Options: SAMEORIGIN`.
- **Swagger UI (`/api-docs/**`):** `default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; img-src 'self' data:; connect-src 'self' <auth0>; object-src 'none'; base-uri 'none'; frame-ancestors 'none'` (Swagger UI sets inline styles; PKCE calls the tenant's token endpoint).
- **API (`/api/**`):** `default-src 'none'; frame-ancestors 'none'` (OWASP REST cheat sheet).
- **Both chains:** `Referrer-Policy: strict-origin-when-cross-origin`; `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()`. HSTS stays Spring Security's default (only on secure requests).

Facts from the code:

- The only inline scripts are the login splash (`login.html`, line 12) and the import map (`login.html`, line 86, its URL carries a content hash, so a fixed hash in the policy would break on every change: hence the nonce). The splash moves to `static/js/splash.js`, loaded synchronously at the same place, so `document.readyState === 'loading'` still holds.
- The only `style` attributes are `style="display: none"` in `fragments/password-toggle.html` (becomes `hidden`) and `th:style` on the sign-up progress bar (becomes one class per step).
- Motion and htmx set styles through the CSSOM (`element.style`), which `style-src` does not block; htmx's injected indicator `<style>` is, so `includeIndicatorStyles` goes to `false` (no template uses `htmx-indicator`). `allowEval: false` and `allowScriptTags: false` harden htmx at no cost (nothing uses `hx-on` or `js:`).
- The sign-up steps are swapped with `hx-boost`, so the password toggle must work on content htmx inserts: delegated listeners on `document`, and the reveal of the button on `htmx:load`.
- Tests that assert Alpine markup: `AccountDetailControllerTests` (lines 352, 362), `FieldTags` (lines 52-54), `WebSecurityTests` (line 133), `AccountListControllerTests` (line 53).

## Tasks

### [x] T1 — Password toggle without Alpine (~10 min)

- **Layer:** adapter.in (web resources)
- **Files:** `fragments/password-toggle.html`, `login.html`, `login-link.html`, `signup/access.html`, `static/js/app.js`, `FieldTags.java` and the tests that use it
- **Depends on:** —
- **What to do:**
  1. Red: `FieldTags` expects `data-password-toggle` on the button (still `hidden` by default, `aria-controls`, `aria-pressed="false"`) and no `x-` attributes on the button or the input group.
  2. Green: markup with `data-password-toggle`; the eye-off icon `<span>` uses `hidden` instead of `style="display: none"`. In `app.js`, a delegated `click` on `document` toggles the controlled input's `type`, `aria-pressed`, the `active` class and both icons; on `htmx:load` (it also fires for the initial page) reveal every `[data-password-toggle][hidden]` inside the loaded element.
- **Acceptance criteria:**
  - [x] The tests of login, link and sign-up access pass with the new markup.
  - [x] Without JavaScript the button stays hidden (it keeps the `hidden` attribute in the HTML).

### [x] T2 — Account actions accordion without Alpine (~5 min)

- **Layer:** adapter.in (web resources)
- **Files:** `accounts/detail.html`, `static/js/app.js`, `AccountDetailControllerTests.java`
- **Depends on:** —
- **What to do:**
  1. Red: the detail tests expect `<div data-action-panels>` instead of the `x-data`/`x-on` wrapper.
  2. Green: a `toggle` listener on `document` in the capture phase (`toggle` does not bubble): when a `<details>` inside `[data-action-panels]` opens, close the other open ones in the same container (same logic as today's `closeOthers`).
- **Acceptance criteria:**
  - [x] `AccountDetailControllerTests` passes; the server still opens the panel whose form came back with errors.

### [x] T3 — Alpine out of the build and no inline styles (~10 min)

- **Layer:** adapter.in (web resources) · build
- **Files:** `pom.xml`, `fragments/layout.html`, `signup/progress.html`, CSS of the sign-up, `WebSecurityTests.java`, `AccountListControllerTests.java`, new `TemplatesCspTests.java`
- **Depends on:** T1, T2
- **What to do:**
  1. Remove the `alpinejs` WebJar, its version property and its `<script>` in the layout (with the "Alpine before app.js" comment).
  2. Progress bar: class `signup-progress-step-1..3` with its width in CSS instead of `th:style`.
  3. Red/green: `TemplatesCspTests` scans `src/main/resources/templates/**/*.html` and `static/**/*.html` (except `static/api-docs/oauth2-redirect.html`, copied unchanged from swagger-ui-dist) and fails on `style=`, `th:style`, `on*=` handlers, `x-` attributes and any `<script>` without `src` that is not the nonce-carrying import map.
  4. Update the two tests that list the Alpine WebJar.
- **Acceptance criteria:**
  - [x] `TemplatesCspTests` passes, and fails if `style="…"` is added back to any template (checked by breaking it once).
  - [x] `./mvnw verify` green with no Alpine in the jar.
- **Deviation:** in T3 `TemplatesCspTests` covers inline styles, `on*=` handlers and Alpine attributes in `templates/`; the rule on inline scripts lands with T6 (login) and the scan of `static/**/*.html` with T8 (Swagger UI), because those inline scripts only disappear in those tasks and every task must leave `./mvnw verify` green. The sign-up progress test now checks the `signup-progress-step-N` class instead of `style="width: 33%"`.

### [x] T4 — htmx hardened for the CSP (~5 min)

- **Layer:** adapter.in (web resources)
- **Files:** `fragments/layout.html`, `AccountListControllerTests.java` (or the test that already checks `htmx-config`)
- **Depends on:** —
- **What to do:**
  1. Add `"includeIndicatorStyles":false,"allowEval":false,"allowScriptTags":false` to the existing `htmx-config` meta, with a comment on why (CSP, #206).
- **Acceptance criteria:**
  - [x] A test checks the three settings in the rendered layout (`LoginControllerTests`).

### [x] T5 — The three policies as values (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/ContentSecurityPolicies.java`, `ContentSecurityPoliciesTests.java`
- **Deviation:** the class lives in the root of `adapter.in` (with `SecuritySupport`), not in `adapter.in.web`: the onion rule forbids `adapter.in.rest` → `adapter.in.web`, and both chains use it.
- **Depends on:** —
- **What to do:**
  1. Red: unit tests for `web(nonce, auth0Origin)`, `apiDocs(auth0Origin)` and `api()`: exact directives above, the Auth0 origin only when present, no `'unsafe-eval'` anywhere and no `'unsafe-inline'` outside `apiDocs`'s `style-src`.
  2. Green: a final class with static methods; the Auth0 origin is an `Optional<String>` (scheme + host, from `Auth0Properties.baseUrl()`).
- **Acceptance criteria:**
  - [x] The unit tests pass; `HexagonalArchitectureTests` green (the class lives in `adapter.in.web`; `ApiSecurityConfig` may use only `api()`, or the API policy stays a constant in `adapter.in.rest` if the ArchUnit rules forbid `rest` → `web`).

### [ ] T6 — Per-request nonce and the login page (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/web/CspNonce.java`, `adapter/in/web/CspNonceAdvice.java`, `login.html`, `static/js/splash.js` (new), `LoginControllerTests.java`
- **Depends on:** T5
- **What to do:**
  1. Red: the login page has no inline `<script>` without `src` except the import map, and the import map carries `nonce="…"` with 22+ base64url characters.
  2. Green: `CspNonce.of(HttpServletRequest)` creates 16 random bytes (`SecureRandom`, base64url) on first call and stores them as a request attribute; `@ControllerAdvice` with `@ModelAttribute("cspNonce")`. `th:attr="nonce=${cspNonce}"` on the import map. The splash code moves unchanged to `splash.js`, loaded with a plain `<script th:src>` at the same position.
- **Acceptance criteria:**
  - [ ] `LoginControllerTests` passes; the splash still only shows while the page is loading and without reduced motion (checked in T10).

### [ ] T7 — Web chain headers (~10 min)

- **Layer:** adapter.in
- **Files:** `WebSecurityConfig.java`, `WebSecurityTests.java`, `WebSecurityConfigOAuth2Tests.java`
- **Depends on:** T5, T6
- **What to do:**
  1. Red: `/login` sends the web policy and its nonce equals the import map's; two requests get different nonces; with Auth0 configured, `form-action` lists its origin; `/api-docs` sends the Swagger UI policy; `/h2-console/` sends no CSP; every response has `Referrer-Policy` and `Permissions-Policy`; a `.secure(true)` request gets `Strict-Transport-Security`.
  2. Green: `headers(...)` with `frameOptions` unchanged, `referrerPolicy(STRICT_ORIGIN_WHEN_CROSS_ORIGIN)`, a static `Permissions-Policy` writer, and a `HeaderWriter` that picks the policy by path (`/h2-console/**` none, `/api-docs/**` Swagger UI, everything else web with `CspNonce.of(request)`; it must use the same nonce the template rendered, which `CspNonce` guarantees by storing it in the request).
- **Acceptance criteria:**
  - [ ] The new tests pass and the existing web security tests stay green.

### [ ] T8 — Swagger UI without inline script (~5 min)

- **Layer:** adapter.in (web resources)
- **Files:** `static/api-docs/index.html`, `static/api-docs/init.js` (new), `ApiDocsControllerTests.java`
- **Depends on:** —
- **What to do:**
  1. Move the inline `fetch('/api-docs/oauth-config')…` block unchanged into `init.js`, loaded with `<script src="init.js">` after the bundle.
  2. Test: `/api-docs/init.js` is served and public; `index.html` has no inline script.
- **Acceptance criteria:**
  - [ ] `ApiDocsControllerTests` and `ApiDocsAuth0Tests` pass.

### [ ] T9 — API chain headers (~5 min)

- **Layer:** adapter.in
- **Files:** `ApiSecurityConfig.java`, `ApiSecurityTests.java`
- **Depends on:** T5
- **What to do:**
  1. Red: any `/api/**` response (200, 401, 403) has `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Referrer-Policy` and `Permissions-Policy`.
  2. Green: the same `headers(...)` customisation in the API chain.
- **Acceptance criteria:**
  - [ ] The tests pass; `node scripts/contract-test.mjs` still passes (headers are not part of the contract).

### [ ] T10 — Browser check with Playwright (~15 min)

- **Layer:** tests
- **Files:** none committed (a script in the session's scratchpad)
- **Depends on:** T1-T9
- **What to do:**
  1. `./mvnw spring-boot:run` (`demo` profile) and, with the pre-installed Playwright (Chromium), go through: login (splash and intro animation), wrong password, show password, sign-up wizard (three steps with htmx, show password on the swapped step), account list, account detail with a deposit, a withdrawal and a transfer through htmx (accordion closing the other panels), logout, `/api-docs` ("Try it out" on one endpoint), `/h2-console` as admin. Collect `securitypolicyviolation` events and console errors.
  2. Repeat login and detail with JavaScript disabled.
  3. Auth0 flows cannot be run locally (no tenant): rely on T7's `form-action` test and note the owner check below.
- **Acceptance criteria:**
  - [ ] Zero CSP violations and zero console errors on every page; the result goes in the PR description.

### [ ] T11 — ADRs, pending items and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0025-security-headers.md` (new), `docs/adr/0016-web-frontend-libraries.md`, `docs/adr/README.md` (index, if it lists ADRs), `docs/ia/pendientes.md`, `docs/ia/decisiones.md`, `docs/ia/estado-del-proyecto.md`, `docs/ia/historial.md`
- **Depends on:** T10
- **What to do:**
  1. ADR 0025: the three policies, nonce instead of hashes, enforced from day one, H2 without CSP, why no COOP; alternatives (Report-Only, `'unsafe-eval'`, Alpine CSP build).
  2. ADR 0016: dated note, Alpine.js removed in #206 and why.
  3. `pendientes.md`: drop the CSP parts of the #111 and #124 items (keep the rest of those lines); add the owner check after the deploy: `curl -sI https://<service>/login` shows the CSP and HSTS through Render and Cloudflare, and logout with Auth0 works.
  4. Status: #206 done, next step; `decisiones.md`; one row in `historial.md`.
- **Acceptance criteria:**
  - [ ] The CI status check passes; no document still says the web has no CSP.

## Global acceptance criteria

**Specific:**

- [ ] The web, Swagger UI and the API send their own CSP; no policy contains `'unsafe-eval'`, and only Swagger UI's `style-src` contains `'unsafe-inline'`.
- [ ] No template has inline styles or inline scripts without a nonce (`TemplatesCspTests`).
- [ ] Alpine.js is gone from `pom.xml`, the templates and `app.js`; the password toggle and the accordion behave as before, also after htmx swaps and without JavaScript.
- [ ] Playwright walk-through with zero CSP violations.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR): one removed (Alpine.js), none added.
- [ ] If the API changes: n/a (only response headers; paths, bodies and status codes do not change).
- [ ] Any new business term is in `docs/dominio/glosario.md` — n/a.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Auth0 redirects and `form-action`:** only testable against a real tenant. If the deployed logout or Auth0 sign-up is blocked, the browser console names the directive; the fix is the origin in `form-action` (custom Auth0 domains included, since it comes from `Auth0Properties`).
- **HSTS behind Render and Cloudflare:** Spring writes it only when the request is secure, which depends on `X-Forwarded-Proto` being trusted (#133). Checked by the owner after the deploy; if missing, a follow-up issue.
- **Motion and the View Transitions API** were checked against the CSSOM rule; T10 confirms it in a real browser.
- **Swagger UI upgrades** may need new sources in its policy; ADR 0025 says where to change it.
- Big task count for one PR (11). It can be split at T4/T5 (frontend without Alpine first, headers second) if the review gets long; the user decides at implementation time.
