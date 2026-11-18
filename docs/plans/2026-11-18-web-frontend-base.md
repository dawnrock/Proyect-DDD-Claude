# Plan: Web frontend base with Bootstrap, Lucide, htmx, Alpine.js, Motion and View Transitions

- **Issue:** [#124](https://github.com/dawnrock/Proyect-DDD-Claude/issues/124)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-18
- **Status:** in progress
- **Total estimate:** ~75 min (9 tasks)

## Goal

Give the Thymeleaf web (`adapter.in.web`, ADR 0005) a modern frontend base without leaving server-side rendering: Bootstrap 5.3 with its dark theme, Lucide icons, page transitions with the View Transitions API, entrance animations with Motion, and htmx and Alpine.js loaded and ready for #125 and #126. Every screen is migrated to plain Bootstrap (user's decision, 2026-11-18), and the web keeps working without JavaScript. Every library is served from the application itself, with no Node build and no CDN.

### Out of scope

- htmx requests and fragment responses: #125 (account operations) and #126 (sign-up, login, new account, list). This plan only loads the library.
- CSRF header for htmx requests: #125, the first plan that sends them.
- Content-Security-Policy (pending since #111).
- `Monolithic`: it stays frozen.
- Domain, `application`, REST and the OpenAPI contract: they do not change.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build | `pom.xml` | WebJars `org.webjars:bootstrap` 5.3.8, `org.webjars.npm:htmx.org` 2.0.11, `org.webjars.npm:alpinejs` 3.17.5, `org.webjars.npm:lucide-static` 1.67.0 and `org.webjars:webjars-locator-lite` (version-agnostic `/webjars/...` URLs) |
| static | `src/main/resources/static/vendor/motion/` | `motion.js` 14.14.0 (UMD build of the npm package `motion`, global `Motion`) and its `LICENSE`. Motion has no published WebJar, so it is copied into the repository with its version in `README.md` |
| static | `src/main/resources/static/js/app.js` | New: entrance animation with Motion, skipped under `prefers-reduced-motion`; Alpine.js components of the account actions |
| static | `src/main/resources/static/css/app.css` | Reduced to what Bootstrap does not cover: `@view-transition { navigation: auto; }`, the balance hero, the transaction rows and the `.icon` size |
| adapter.in.web | `WebSecurityConfig` | `/webjars/**`, `/vendor/**` and `/js/**` become public, like `/css/**` |
| templates | `fragments/layout.html` | Loads Bootstrap (CSS and the JS bundle), htmx, Alpine.js, Motion and `app.js` (`defer`); `data-bs-theme="dark"` on `<html>`; header as a Bootstrap navbar |
| templates | `fragments/icons.html` | New: `icon(name)` fragment that renders `<svg class="icon" aria-hidden="true"><use href="/webjars/lucide-static/sprite.svg#name"/></svg>`: no JavaScript, works inside htmx swaps |
| templates | `accounts/*`, `signup/*`, `login.html`, `error/*` | Bootstrap components (cards, list groups, forms with `form-control`/`is-invalid`/`invalid-feedback`, buttons, alerts, progress bar); the Unicode arrows (↓ ↑ ⇄ ⟳ + ←) become Lucide icons |
| tests | `AccountWebControllerTests` and the other web tests | Adjust the assertions that compare literal HTML (`class="back"`, `← Cuentas`) |
| docs | `docs/adr/0016-web-frontend-libraries.md` | New ADR, complements ADR 0005 |

**Technical decisions:**

- **Progressive enhancement.** The `<details>` elements of the account actions stay: they open and close without JavaScript, and the tests check `<details id="…-action" open="open">` when a form comes back with errors. Alpine.js adds the accordion behaviour, so opening one closes the others. Bootstrap's collapse is not used because it needs JavaScript to open at all.
- **Icons as an SVG sprite** (`lucide-static`), not the `lucide` JS package: the server renders them, they do not flash on load, and they need no `createIcons()` call again after each htmx swap. The sprite is downloaded once and cached.
- **htmx 2.0.11, not 4.x:** htmx 4 has been out since 2026-08 but changes events and attribute inheritance. 2.x is maintained, its documentation is stable, and moving to 4 can be a later issue.
- **View Transitions:** cross-document transitions only need CSS (`@view-transition`). Browsers without support ignore it, so there is no polyfill.
- **No architecture change:** everything stays in `adapter.in.web` and static resources; ArchUnit has no new rule.

## Tasks

### [x] T1 — Add the WebJars and Motion, and make the static paths public (~10 min)

- **Layer:** build · adapter.in.web
- **Files:** `pom.xml`, `src/main/resources/static/vendor/motion/{motion.js,LICENSE,README.md}`, `WebSecurityConfig`, `WebSecurityTests`
- **Depends on:** —
- **What to do:**
  1. Test first (`WebSecurityTests`): without a session, `GET /webjars/bootstrap/css/bootstrap.min.css`, `/webjars/htmx.org/dist/htmx.min.js`, `/webjars/alpinejs/dist/cdn.min.js`, `/webjars/lucide-static/sprite.svg` and `/vendor/motion/motion.js` answer 200, with versionless paths.
  2. Add the five dependencies to `pom.xml` with versions in `<properties>`.
  3. Download `motion@14.14.0` from the npm registry, check its `integrity` (sha512), and copy `dist/motion.js` and `LICENSE`.
  4. Add `/webjars/**`, `/vendor/**` and `/js/**` to the public matchers.
- **Acceptance criteria:**
  - [x] The new tests pass, and an authenticated page still needs a login (existing tests green).
  - [x] `./mvnw verify` green.

### [x] T2 — ADR 0016: frontend libraries for the web (~5 min)

- **Layer:** docs
- **Files:** `docs/adr/0016-web-frontend-libraries.md`, `docs/adr/README.md`
- **Depends on:** T1
- **What to do:**
  1. Context, decision (libraries, versions, WebJars, Motion copied into the repo, progressive enhancement, sprite), alternatives (CDN with SRI as in #111, SPA, Tailwind, copying every library into `static/vendor`, the `lucide` JS package, htmx 4) and consequences (the jar grows by ~14.5 MB, mostly from `lucide-static`; Motion is upgraded by hand).
- **Acceptance criteria:**
  - [x] The ADR is listed in the index and written in English.

### [x] T3 — Layout, icon fragment and `app.js` (~10 min)

- **Layer:** adapter.in.web (templates and static)
- **Files:** `fragments/layout.html`, `fragments/icons.html`, `static/js/app.js`, `static/css/app.css`, `AccountWebControllerTests`
- **Depends on:** T1
- **What to do:**
  1. Test first: the list page carries `data-bs-theme="dark"`, the Bootstrap stylesheet and the `htmx`, `alpinejs`, `motion` and `app.js` scripts.
  2. Head with Bootstrap CSS, `app.css`, the scripts with `defer`, and Alpine.js last. Header as a `navbar`, footer with Bootstrap utilities.
  3. `icons.html` with the `icon(name)` fragment.
  4. `app.js`: `Motion.animate` fades `main` in and moves it up slightly on load; nothing happens when `matchMedia('(prefers-reduced-motion: reduce)')` matches.
  5. `@view-transition { navigation: auto; }` in `app.css`, disabled under reduced motion.
- **Acceptance criteria:**
  - [x] Test green. `./mvnw spring-boot:run`: the browser console shows no 404s and no script errors.

### [ ] T4 — Login, entry doors and error pages (~5 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `login.html`, `error/403.html`, `error/404.html`, `LoginControllerTests`
- **Depends on:** T3
- **What to do:**
  1. Doors as Bootstrap cards with a Lucide icon; the login form uses `form-control` and `alert-danger`; error pages use the display headings.
- **Acceptance criteria:**
  - [ ] Login, logout and error tests green.

### [ ] T5 — Account list, new account and another account (~10 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `accounts/list.html`, `accounts/new.html`, `accounts/another.html`, `AccountWebControllerTests`, `AnotherAccountWebTests`
- **Depends on:** T3
- **What to do:**
  1. List as a `list-group` with an avatar, and the total balance in a card.
  2. Forms with `form-control`/`form-select`, `is-invalid` + `invalid-feedback` on errors, and an `alert-danger` for global errors.
  3. Add a test: a field error is rendered with `is-invalid`.
- **Acceptance criteria:**
  - [ ] Existing and new tests green.

### [ ] T6 — Account detail with icons and the Alpine.js accordion (~10 min)

- **Layer:** adapter.in.web (templates and static)
- **Files:** `accounts/detail.html`, `static/js/app.js`, `AccountWebControllerTests`, `RegenerateAccountNumberWebTests`
- **Depends on:** T3, T5
- **What to do:**
  1. Bootstrap buttons as the `<summary>` of each action, keeping the `<details id="…-action">` and their `th:open`. Lucide icons: `arrow-down-to-line`, `arrow-up-from-line`, `arrow-left-right`, `refresh-cw`, `plus`, `arrow-left`.
  2. Alpine.js (`x-data` on `.actions`, `@toggle` on each `details`) closes the other panels when one opens.
  3. Transactions as a `list-group`; rejected ones in `text-body-secondary` with a strikethrough.
  4. Update the "back" link assertions (`← Cuentas` becomes an icon followed by `Cuentas`).
- **Acceptance criteria:**
  - [ ] The `<details … open="open">` tests stay green without changes.
  - [ ] Without JavaScript, every panel still opens and submits.

### [ ] T7 — Customer sign-up in three steps (~10 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `signup/data.html`, `signup/access.html`, `signup/account.html`, `signup/progress.html`, `SignUpControllerTests`
- **Depends on:** T3
- **What to do:**
  1. Progress as a Bootstrap `progress` bar (`aria-valuenow`) with "Paso n de 3"; summary as a `dl` in a card; forms as in T5.
- **Acceptance criteria:**
  - [ ] Sign-up tests green; the duplicate-customer flow still shows its link.

### [ ] T8 — Trim `app.css` and check no old classes are left (~5 min)

- **Layer:** adapter.in.web (static)
- **Files:** `static/css/app.css`
- **Depends on:** T4-T7
- **What to do:**
  1. Remove the rules that no template uses any more (`grep` every class of the old file against `templates/`).
  2. Keep the hero, the transaction rows, `.icon`, the theme tweaks through `--bs-*` variables, and the View Transitions.
- **Acceptance criteria:**
  - [ ] No rule of `app.css` points to a class that no template uses, and no template uses a class that no stylesheet defines.

### [ ] T9 — Project status and decisions (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/historial.md`, `docs/ia/estado-del-proyecto.md`, `docs/arquitectura/fase-2-hexagonal.md` (only if it describes the web's static resources)
- **Depends on:** T1-T8
- **What to do:**
  1. Decision: frontend libraries (ADR 0016) and progressive enhancement. Pending items: htmx 4, CSP, a WebJar for Motion if one is published, jar size. Next step: #125.
- **Acceptance criteria:**
  - [ ] The CI status-file check passes.

## Global acceptance criteria

**Specific:**

- [ ] Every library is served by the application (`/webjars/**`, `/vendor/**`); no page requests an external domain.
- [ ] Every screen (list, detail, new account, another account, sign-up in three steps, login, 403, 404) uses Bootstrap with `data-bs-theme="dark"`; no rule of the old stylesheet is left unused.
- [ ] The web works without JavaScript exactly as today (forms, panels, PRG).
- [ ] Navigating between pages uses View Transitions in Chromium, and Motion animates the entrance; with `prefers-reduced-motion` neither runs.
- [ ] Security rules unchanged apart from the three static paths.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR): ADR 0016.
- [ ] If the API changes: n/a (the REST API does not change).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Jar size:** `lucide-static` weighs ~11 MB because it ships fonts and loose SVGs. The image on Render grows; the alternative is to copy only the sprite into `static/vendor` (option in the ADR).
- **Version-agnostic WebJar URLs:** they depend on Spring Boot 4.1 enabling `LiteWebJarsResourceResolver` when `webjars-locator-lite` is on the classpath. T1 checks it with a test; if not, set `spring.web.resources.chain.enabled=true`.
- **Visual change:** migrating to plain Bootstrap changes the look of the current theme (#48). Screenshots of the main screens go in the PR.
- **Render deploy:** nothing to configure, since the dependencies come in through Maven, but the first deploy should be checked by opening the web.
