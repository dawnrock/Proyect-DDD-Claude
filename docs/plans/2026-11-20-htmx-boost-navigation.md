# Plan: htmx navigation for sign-up, login, new account, another account and the list

- **Issue:** [#126](https://github.com/dawnrock/Proyect-DDD-Claude/issues/126)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-20
- **Status:** in progress
- **Total estimate:** ~55 min (7 tasks)

## Goal

Every screen of the web moves between pages without a full reload: links and forms go through htmx (`hx-boost`), which swaps the `<body>` inside a view transition and updates the title and the URL. The server does not change: controllers keep returning full pages and redirects, so the three-step sign-up, the new account, another account, the list and Spring Security's login and logout work as they are, and everything keeps working without JavaScript. The account detail keeps the fragment of #125, which takes precedence over the boost. Form errors appear without reloading the page.

**Approach chosen by the user (2026-11-20):** hybrid. `hx-boost` is the base for navigation and plain forms (it scales with the number of screens: a new screen needs no htmx code), and fragments only where an interaction needs them (the detail since #125). Validation happens on submit, without reloading; per-field validation on blur is out of scope and can be added later to a single field if needed.

### Out of scope

- Fragments per screen (`HX-Request` branches in `SignUpController` or the list): the boost covers it without touching the controllers.
- Per-field validation on blur (`hx-trigger="blur"` with validation endpoints).
- Domain, `application`, the REST API and the OpenAPI contract.
- `Monolithic`.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| templates | `fragments/layout.html` | `<meta name="htmx-config">` with `historyCacheSize: 0` and `refreshOnHistoryMiss: true`: htmx would otherwise keep copies of the pages (balances, IBANs) in `sessionStorage` for the back button; with the cache off, going back reloads the page from the server |
| templates | every full page (`accounts/*`, `signup/*`, `login.html`, `error/*`) | `<body hx-boost="true" hx-swap="innerHTML transition:true">` |
| templates | `accounts/detail.html` | The "Abrir otra cuenta" link sits inside `#account-panel` and would inherit its `hx-target`/`hx-swap`: it gets `hx-target="body"` and `hx-swap="innerHTML transition:true"`. The panel's forms keep their explicit `hx-post`, which htmx processes before the boost |
| templates + static | `signup/progress.html`, `static/css/app.css` | `view-transition-name` on the progress bar, so the view transition morphs its width from one step to the next (the "animated progress bar" of the issue), only without reduced motion |
| static | `static/js/app.js` | The Motion entrance animation also runs after a boosted swap (`htmx:afterSettle` on `body`), not only on `DOMContentLoaded` |
| tests | `AccountWebControllerTests`, `SignUpControllerTests`, `LoginControllerTests`, `WebSecurityTests` | Template attributes; a boosted GET without a session gets `HX-Redirect` |
| docs | `docs/adr/0016-web-frontend-libraries.md`, `docs/ia/*` | Boost as the base, fragments where needed; history cache off |

**Technical decisions (checked in the htmx 2.0.11 source):**

- **Explicit verbs win over the boost** (`processVerbs` runs before `boostElement`), so the detail's `hx-post` forms keep answering the panel.
- **A boosted element inherits `hx-target`** from its ancestors (`getTarget` uses `getClosestAttributeValue`), hence the explicit target on the link inside the panel.
- **Redirects:** after a boosted POST, htmx follows the redirect and pushes the final URL (`responsePath` from the response), so `/signup` → `/signup/access` and the login → `/` leave the right address in the bar.
- **Security unchanged:** an expired session on a boosted request already gets 401 + `HX-Redirect: /login` from `HtmxLoginRedirect` (#125), and the login, logout and CSRF tokens travel as in a normal form (the new page after logging in carries the new tokens).
- **Error pages:** 403 and 404 already replace the body (`app.js`, #125).

## Tasks

### [x] T1 — htmx configuration and boost on every page (~10 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `fragments/layout.html`, the full-page templates, `AccountWebControllerTests`, `SignUpControllerTests`, `LoginControllerTests`
- **Depends on:** —
- **What to do:**
  1. Tests first: the list, the sign-up and the login carry `<body … hx-boost="true" hx-swap="innerHTML transition:true">` and the `htmx-config` meta with `"historyCacheSize":0` and `"refreshOnHistoryMiss":true`.
  2. Add the meta to the layout head and the attributes to every page's `<body>`.
- **Acceptance criteria:**
  - [x] New tests green; every full page has the boost (a test walks the templates and fails if one lacks it).

### [ ] T2 — "Abrir otra cuenta" inside the account panel (~5 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `accounts/detail.html`, `AnotherAccountWebTests`
- **Depends on:** T1
- **What to do:**
  1. Test first: the link carries `hx-target="body"` and `hx-swap="innerHTML transition:true"`.
  2. Add them.
- **Acceptance criteria:**
  - [ ] Test green; the panel's `hx-post` forms unchanged (their #125 tests green).

### [ ] T3 — Animated sign-up progress (~5 min)

- **Layer:** adapter.in.web (templates and static)
- **Files:** `signup/progress.html`, `static/css/app.css`, `SignUpControllerTests`
- **Depends on:** T1
- **What to do:**
  1. Test first: the progress bar carries the class `signup-progress-bar`.
  2. `.signup-progress-bar { view-transition-name: signup-progress; }` inside `@media (prefers-reduced-motion: no-preference)`.
- **Acceptance criteria:**
  - [ ] Test green.

### [ ] T4 — Expired session on a boosted navigation (~5 min)

- **Layer:** adapter.in.web (security tests)
- **Files:** `WebSecurityTests`
- **Depends on:** —
- **What to do:**
  1. Test: a boosted GET of `/` without a session (`HX-Request` and `HX-Boosted` headers) answers 401 with `HX-Redirect: /login`. It should pass already with `HtmxLoginRedirect`; check it can fail by removing the entry point temporarily.
- **Acceptance criteria:**
  - [ ] Test green and seen red when sabotaged.

### [ ] T5 — Entrance animation after boosted navigations (~5 min)

- **Layer:** adapter.in.web (static)
- **Files:** `static/js/app.js`
- **Depends on:** T1
- **What to do:**
  1. Extract the entrance animation into a function; call it on `DOMContentLoaded` and on `htmx:afterSettle` when the swapped target is `body`. Nothing under reduced motion.
- **Acceptance criteria:**
  - [ ] Checked in T6.

### [ ] T6 — Browser check (~15 min)

- **Layer:** tests (manual, Playwright)
- **Files:** —
- **Depends on:** T1-T5
- **What to do:**
  1. With JavaScript, on the same `window` (a marker set on the first page survives) and with the right URL after each step:
     - login with an error, then a correct login;
     - list → detail → back to the list;
     - new account with a field error, then a correct one;
     - another account;
     - the three sign-up steps with a field error, including a duplicate customer;
     - logout.
  2. The back button reloads the previous page from the server and `sessionStorage` has no `htmx-history-cache`.
  3. The detail's operations still swap only the panel (#125).
  4. An expired session on a boosted link lands on `/login`.
  5. No console errors.
  6. The same flows with JavaScript disabled and with reduced motion (no animations).
- **Acceptance criteria:**
  - [ ] Every flow behaves as described.

### [ ] T7 — Project status and decisions (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0016-web-frontend-libraries.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/historial.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T1-T6
- **What to do:**
  1. ADR 0016: boost as the base, fragments where an interaction needs them, history cache off.
  2. `decisiones.md`: the rule for new screens (nothing to do; a link inside a fragment with its own `hx-target` needs an explicit target).
  3. `pendientes.md`: per-field validation as a possible later step.
  4. Status: the htmx series (#124-#126) closed; next step chosen by the user.
- **Acceptance criteria:**
  - [ ] The CI status-file check passes.

## Global acceptance criteria

**Specific:**

- [ ] With JavaScript, every screen navigates and submits without a full reload, with the right title and URL.
- [ ] Without JavaScript, every screen works exactly as before.
- [ ] The back button never restores a page from a client-side copy (no `htmx-history-cache` in `sessionStorage`).
- [ ] The detail's operations of #125 keep swapping only the panel.
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

- **`autofocus` after a swap:** htmx focuses an `[autofocus]` element of the new content; T6 checks it on the login and the sign-up.
- **Scripts in `<head>`:** a boosted swap only replaces the body, so the head's scripts keep running; Alpine.js initialises the new body by itself. If a future page needed its own script, it should either live in `app.js` or opt out with `hx-boost="false"`.
- **Full page on every navigation:** the HTML of the whole page travels each time (the CSS, scripts and sprite stay cached since #124); with pages of this size it is not a concern, and a screen can move to a fragment like the detail if it ever is.
