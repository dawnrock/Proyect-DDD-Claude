# Plan: Account operations without reloading the page with htmx

- **Issue:** [#125](https://github.com/dawnrock/Proyect-DDD-Claude/issues/125)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-18
- **Status:** in progress
- **Total estimate:** ~70 min (8 tasks)

## Goal

On the account detail, depositing, withdrawing, transferring and generating a new IBAN update the balance, the notice and the transactions without reloading the page. The controller answers an htmx request (header `HX-Request: true`) with a server-rendered fragment of the detail and keeps the usual Post/Redirect/Get otherwise, so the web works exactly as today without JavaScript. Motion animates the new balance and the newest transaction after each swap, and htmx swaps the fragment inside a view transition. This builds on the base of #124 (ADR 0016).

### Out of scope

- htmx on sign-up, login, new account, another account and the list: #126.
- Domain, `application`, the REST API and the OpenAPI contract: they do not change (the web uses the same input ports).
- Content-Security-Policy and htmx 4 (pending since #124).
- `Monolithic`.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| templates | `accounts/detail.html` | New `panel` fragment (`<div id="account-panel" th:fragment="panel">`) holding the notices, the balance, the actions and the transactions; the "back" link stays outside. The four forms get `th:hx-post` with the same URL as `th:action`; the panel sets `hx-target="#account-panel"` and `hx-swap="outerHTML transition:true"`, inherited by the forms |
| adapter.in.web | `AccountWebController` | `deposit`, `withdraw`, `transfer` and `regenerateAccountNumber` read `@RequestHeader(name = "HX-Request", required = false)`. With htmx: success puts `message`/`rejection` in the model (not a flash attribute) and returns `accounts/detail :: panel`; errors return the same fragment with the form open and its errors. Without htmx: the current redirect and flash attributes, unchanged. A private helper avoids repeating that choice four times |
| adapter.in.web | `HtmxAuthenticationEntryPoint` (new) + `WebSecurityConfig` | An htmx request without a session (expired) gets 401 with `HX-Redirect: /login`, so htmx navigates to the login page instead of swapping it inside the panel. Requests without the header keep the 302 to `/login` |
| static | `static/js/app.js` | After a swap of `#account-panel`: Motion animates the balance and the newest transaction. With reduced motion: no animation and `htmx:beforeTransition` is cancelled. A 4xx/5xx answer to an htmx request (a foreign account's 404, the 403 page, an expired CSRF token) replaces the page body with the error page instead of doing nothing |
| tests | `AccountWebControllerTests`, `RegenerateAccountNumberWebTests`, `WebSecurityTests` | Each operation with and without `HX-Request` |
| docs | `docs/ia/*`, `docs/adr/0016-web-frontend-libraries.md` | Decisions and status; ADR 0016 gains a line on how htmx answers are built (fragment + PRG fallback) |

**Technical decisions:**

- **Fragment, not `hx-select` over the full page.** The issue asks for fragments. A fragment answers in one round trip (no redirect followed by the XHR), keeps the success message in the model instead of the session's flash map, and makes the htmx response explicit in the controller and its tests. The view name `accounts/detail :: panel` reuses the same template, so both responses cannot drift apart.
- **CSRF needs no extra header.** Every form keeps `th:action`, so Thymeleaf still writes the hidden `_csrf` field, and htmx sends the form's fields with `hx-post`. This resolves the pending item of #124 without code: a test checks that an htmx POST without the token gets 403.
- **No `hx-boost`.** It would also boost the links inside the panel ("Abrir otra cuenta") and push history entries; explicit `hx-post` on the four forms is narrower.
- **URL and history unchanged:** the operations stay on `/accounts/{id}`, so no `hx-push-url`.
- **Alpine.js** picks up the new `x-data="actionPanels"` of the swapped panel by itself (it observes the DOM), so the accordion keeps working after a swap. The browser check covers it.
- **Architecture:** everything stays in `adapter.in.web` and static resources; no new ArchUnit rule.

## Tasks

### [x] T1 — Detail panel fragment and htmx attributes on the forms (~10 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `accounts/detail.html`, `AccountWebControllerTests`
- **Depends on:** —
- **What to do:**
  1. Test first: the detail contains `id="account-panel"`, `hx-target="#account-panel"`, `hx-swap="outerHTML transition:true"` and `hx-post="/accounts/{id}/deposits"` (and the other three), and every form still has its `action` and `_csrf`.
  2. Wrap notices, balance, actions and transactions in the `panel` fragment; add `th:hx-post` to the forms.
- **Acceptance criteria:**
  - [x] New test green; the existing detail tests unchanged and green.

### [x] T2 — Deposit answers the panel fragment to htmx (~10 min)

- **Layer:** adapter.in.web
- **Files:** `AccountWebController`, `AccountWebControllerTests`
- **Depends on:** T1
- **What to do:**
  1. Tests first, with `header("HX-Request", "true")`: an approved deposit answers 200 with view `accounts/detail :: panel`, the model's `message` "Ingreso de 50,00 € aprobado" and no flash attribute; an amount of 0 answers the fragment with `<details id="deposit-action" open="open">` and the error; a domain error (currency mismatch) shows its message in the fragment.
  2. Private helper `done(...)`: with htmx, add the notice to the model and render the panel; without it, add the flash attribute and redirect. `renderDetail` returns the panel when the request is htmx.
- **Acceptance criteria:**
  - [x] New tests green; the existing deposit tests (redirect + flash) unchanged and green.

### [x] T3 — Withdrawal: approved and rejected (~5 min)

- **Layer:** adapter.in.web
- **Files:** `AccountWebController`, `AccountWebControllerTests`
- **Depends on:** T2
- **What to do:**
  1. Tests with htmx: an approved withdrawal puts `message` in the model; a rejected one puts `rejection` and the fragment shows `alert alert-warning`; a validation error opens `withdrawal-action`.
  2. Use the helper of T2.
- **Acceptance criteria:**
  - [x] Tests green with and without htmx.

### [x] T4 — Transfer, including an unknown target IBAN (~10 min)

- **Layer:** adapter.in.web
- **Files:** `AccountWebController`, `AccountWebControllerTests`
- **Depends on:** T2
- **What to do:**
  1. Tests with htmx: approved and rejected transfers; `TargetAccountNotFoundException` answers the fragment with the field error on `targetAccountNumber` and `transfer-action` open; a foreign source account still answers 404.
  2. Use the helper of T2.
- **Acceptance criteria:**
  - [x] Tests green with and without htmx.

### [ ] T5 — New IBAN through htmx (~5 min)

- **Layer:** adapter.in.web
- **Files:** `AccountWebController`, `RegenerateAccountNumberWebTests`
- **Depends on:** T2
- **What to do:**
  1. Test with htmx: the administrator gets the panel with "Nuevo IBAN asignado: …" and the new IBAN in the heading; a non-administrator still gets 403.
  2. Use the helper of T2.
- **Acceptance criteria:**
  - [ ] Tests green with and without htmx.

### [ ] T6 — Expired session and CSRF on htmx requests (~10 min)

- **Layer:** adapter.in.web (security)
- **Files:** `HtmxAuthenticationEntryPoint` (new), `WebSecurityConfig`, `WebSecurityTests`
- **Depends on:** —
- **What to do:**
  1. Tests first: an anonymous POST with `HX-Request: true` answers 401 with `HX-Redirect: /login`; the same POST without the header still answers 302 to `/login`; an authenticated htmx POST without the CSRF token answers 403.
  2. Entry point for requests matching `RequestHeaderRequestMatcher("HX-Request", "true")` through `defaultAuthenticationEntryPointFor`; the form-login entry point stays the default.
- **Acceptance criteria:**
  - [ ] Tests green; the existing security tests unchanged and green.

### [ ] T7 — Animations and error answers in `app.js` (~10 min)

- **Layer:** adapter.in.web (static)
- **Files:** `static/js/app.js`
- **Depends on:** T1-T6
- **What to do:**
  1. `htmx:afterSettle` on `#account-panel`: Motion scales the balance in and fades in the first transaction when the panel carries a success or rejection notice.
  2. Reduced motion: no Motion and `htmx:beforeTransition` prevented.
  3. `htmx:beforeSwap` with status 4xx/5xx: swap the error page into `body` (`shouldSwap = true`, `target = document.body`, `swapOverride = 'innerHTML'`) and keep `isError`.
  4. Check it in Chromium (Playwright): deposit, rejected withdrawal, transfer to an unknown IBAN and new IBAN without a reload (the page's `window` keeps a marker), the accordion after a swap, an expired session (cookie deleted) going to `/login`, and the same flows with JavaScript disabled.
- **Acceptance criteria:**
  - [ ] Every flow of step 4 behaves as described, with no console errors.

### [ ] T8 — Project status and decisions (~5 min)

- **Layer:** docs
- **Files:** `docs/adr/0016-web-frontend-libraries.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/historial.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T1-T7
- **What to do:**
  1. ADR 0016: one line on htmx answers (fragment with `HX-Request`, PRG without it). Decision in `decisiones.md` with the pattern #126 reuses. Remove the "CSRF header for htmx" pending item of #124. Next step: #126.
- **Acceptance criteria:**
  - [ ] The CI status-file check passes.

## Global acceptance criteria

**Specific:**

- [ ] With JavaScript, the four operations update the panel without reloading the page and the URL stays `/accounts/{id}`.
- [ ] Without JavaScript, every operation behaves exactly as before (redirect and flash notice, or the detail with the form errors).
- [ ] An expired session during an htmx request ends on `/login`, never inside the panel; 403/404 answers show their error page.
- [ ] No animation or view transition runs with `prefers-reduced-motion`.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR): none added.
- [ ] If the API changes: n/a (the REST API does not change).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Swapping an error page into `body`:** htmx 2 takes the `<body>` of a full document when it swaps one; T7 checks it in the browser. If it misbehaves, the fallback is a full navigation (`location.assign`) to the current URL.
- **Fragment and full page drifting apart:** both come from the same `panel` fragment of `detail.html`, so there is one source.
- **Messages for screen readers:** the notices keep `role="status"`/`role="alert"`, which are announced when they appear in the swapped panel; checked in T7 by reading the accessibility tree.
