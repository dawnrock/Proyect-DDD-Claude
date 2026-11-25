# Plan: Web redesign with the visual language of monA Bank's A

- **Issue:** [#185](https://github.com/dawnrock/Proyect-DDD-Claude/issues/185)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-25
- **Status:** in progress
- **Total estimate:** ~125 min (16 tasks)

## Goal

Give the web its own look, built from the monA Bank logo (#178), in place of Bootstrap's generic rectangular buttons. The user chose direction **C · The A** from three directions sketched on a design canvas. The slant of the A's legs (about 12.4°) becomes the shape of buttons and cards, and the logo's symbols become icons: the A's green leg with its arrow means money in, and the n's red drop means money out. On the welcome screen, a darkened A bigger than the screen sits behind the logo, the greeting and the two doors. It is a presentation change: routes, forms, validation, htmx and Alpine.js behave as today.

### Out of scope

- The intro animation, the logo and its SVGs (`static/brand/`, `brand-geometry.js`, `intro.js`, `scripts/build-brand.mjs`).
- Any change in controllers, application services, domain or REST API.
- `Monolithic` (frozen) and a light theme.

## Context

The web is Thymeleaf + Bootstrap 5.3 in dark mode (ADR 0016), with a small `app.css`. Tektur is vendored for the intro (ADR 0022). Every page uses the `fragments/layout :: head` and `:: header` fragments, icons come from the Lucide sprite (`fragments/icons :: icon`). The sketches use these tokens:

| Token | Value | Use |
| ----- | ----- | --- |
| ground | `#212529` | Page background (already the theme's) |
| surface | `#2b3035` | Cards, inactive actions, forms |
| line | `#343a40` | Dividers |
| ink | `#eef0f2` | Text, light buttons |
| ink-2 | `#b9c0c7` | Secondary text |
| flat | `#8b949c` | Neutral (the logo's flat market) |
| drop | `#f0545c` | Withdrawals, the n's drop |
| rise | `#1fc8b0` | Deposits, primary action, the A's arrow |
| slant | `13px` per `60px` of height (tan 12.4° ≈ 0.22) | Parallelogram offset |

Typefaces: **Tektur** for headings, balances, amounts and IBANs; **Sora** for the rest (chosen by the user; SIL Open Font License, free, vendored like Tektur).

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| static (build) | `static/vendor/sora/` (new) | Sora variable woff2, `OFL.txt`, `README.md` |
| docs | `docs/adr/0023-brand-ui.md` (new), `docs/adr/README.md`, `0016`, `0022` | Decision: brand layer on Bootstrap, Sora, shapes |
| static | `static/css/app.css` | Tokens, typefaces, slanted buttons, cards, backdrop, focus |
| adapter.in.web (templates) | `fragments/layout.html`, `fragments/brand.html` (new) | Head fonts, header; brand glyphs and backdrop fragments |
| adapter.in.web (templates) | `login.html`, `signup/*.html` | Welcome doors and entry screens over the big A |
| adapter.in.web (templates) | `accounts/list.html`, `accounts/detail.html`, `accounts/new.html`, `accounts/another.html`, `error/*.html` | Account screens and the rest |
| static | `static/js/app.js` | Selectors of the post-operation animation |
| tests | `LoginControllerTests`, `AccountListControllerTests`, `AccountDetailControllerTests`, new `BrandGlyphsTemplatesTests` | Markup of the new screens |
| docs | `docs/brand/README.md`, `docs/ia/*` | UI language, project status |

Markup the tests rely on and that must stay: the `<details id="deposit-action|withdrawal-action|transfer-action">` actions and their `open` attribute, `#account-panel`, `invalid-feedback` and `form-control` classes, the header's `navbar-brand` link with the horizontal logo `<img>`, the hidden intro layer (`#splash`) with its import map and module, `hx-boost` on every page's `<body>` (`HtmxBoostTemplatesTests`), and `<span class="text-nowrap">IBAN</span>` in the detail (`AccountNumberWebControllerTests`).

## Tasks

### [x] T1 — Vendor Sora (~10 min)

- **Layer:** build
- **Files:** `src/main/resources/static/vendor/sora/{sora-latin-wght-normal.woff2,OFL.txt,README.md}`, `LoginControllerTests.java`
- **Depends on:** —
- **What to do:**
  1. `npm pack @fontsource-variable/sora@<current 5.x>` into a temporary folder, check the tarball against the registry's `dist.integrity`, copy `files/sora-latin-wght-normal.woff2` and `LICENSE` (as `OFL.txt`).
  2. `README.md` like Tektur's: version, origin, integrity, how to upgrade.
  3. Test: `GET /vendor/sora/sora-latin-wght-normal.woff2` answers 200 with `font/woff2` and is public (no login).
- **Acceptance criteria:**
  - [x] The font is served without authentication; the test passes.
  - [x] Licence and README next to the file.

### [x] T2 — ADR 0023: the brand's UI language (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0023-brand-ui.md`, `docs/adr/README.md`, `docs/adr/0016-web-frontend-libraries.md`, `docs/adr/0022-brand-identity.md`
- **Depends on:** T1
- **What to do:**
  1. ADR 0023 (status accepted, chosen by the user with sketches on 2026-11-25): direction C, tokens, slanted shapes, brand glyphs, the big A backdrop on entry screens, Sora vendored and why (no WebJar, OFL), Bootstrap kept as the base with a brand layer in `app.css`; alternatives (directions A and B, system font, a new CSS framework); consequences (Tektur and Sora on every page, about 12 + 25 KB, cached a year).
  2. Index row in the ADR README; Sora row in ADR 0016's table; in ADR 0022, Tektur is now downloaded on every page, not only by the intro.
- **Acceptance criteria:**
  - [x] ADR in English, linked from the index; 0016 and 0022 point to it.

### [x] T3 — Tokens, typefaces and header (~10 min)

- **Layer:** adapter.in (static + templates)
- **Files:** `static/css/app.css`, `templates/fragments/layout.html`, `AccountListControllerTests.java`
- **Depends on:** T1
- **What to do:**
  1. `:root[data-bs-theme=dark]` custom properties with the tokens above, mapped onto Bootstrap's (`--bs-body-bg`, `--bs-body-color`, `--bs-secondary-color`, `--bs-border-color`, `--bs-body-font-family: Sora`, `--bs-link-color`…); `@font-face` for Sora (`font-display: swap`); headings, `.amount`, `.iban` in Tektur. Keep Tektur's `font-display: block` (the intro depends on it).
  2. `<link rel="preload" as="font" crossorigin>` for both fonts in `head`.
  3. Header: logo, user and "Cerrar sesión" restyled (same markup the header test matches).
  4. Test: the head preloads Tektur and Sora.
- **Acceptance criteria:**
  - [x] The existing header test still passes; the preload test passes.

### [x] T4 — Brand fragments: glyphs and the A (~10 min)

- **Layer:** adapter.in (templates)
- **Files:** `templates/fragments/brand.html` (new), `BrandGlyphsTemplatesTests.java` (new)
- **Depends on:** T3
- **What to do:**
  1. Fragments with inline SVG taken from the logo outlines (same coordinates as `static/brand/`), `aria-hidden="true"`, coloured with `currentColor`: `rise` (A leg + arrow), `drop` (the n's bar and stem), `watermark` (the A in outline with a faint green leg) and `backdrop` (the A bigger than the viewport, darkened, `position: fixed`, behind the content).
  2. Test that renders each fragment through a tiny template (or reads the file) and checks it is decorative (`aria-hidden`) and has no fixed colour.
- **Acceptance criteria:**
  - [x] Four fragments; the test passes.

### [x] T5 — Slanted buttons, fields and focus (~10 min)

- **Layer:** adapter.in (static)
- **Files:** `static/css/app.css`
- **Depends on:** T3
- **What to do:**
  1. `.btn-slant` (parallelogram with `clip-path`, Tektur, min height 48 px) and variants `.btn-slant-rise`, `.btn-slant-light`, `.btn-slant-drop`, `.btn-slant-surface`; `.card-slant` (right edge slanted like the A's right leg); form controls on `surface` with a 1.5 px line, no radius.
  2. Visible focus: `clip-path` cuts `outline`, so the focus ring goes on a non-clipped wrapper or with `filter: drop-shadow` over `:focus-visible`; checked with the keyboard.
  3. Hover transitions only with `prefers-reduced-motion: no-preference`.
- **Acceptance criteria:**
  - [x] A scratch page or the running app shows the four variants with a visible focus at 390 px and 1280 px (Playwright screenshot).

### [x] T6 — Welcome doors over the big A (~10 min)

- **Layer:** adapter.in (templates)
- **Files:** `templates/login.html`, `LoginControllerTests.java`
- **Depends on:** T4, T5
- **What to do:**
  1. Doors view (`showForm` false): no header; `brand :: backdrop`; centred column with the horizontal logo (decorative, `alt=""`), «Bienvenido a monA Bank» and the two doors as full-width `.btn-slant` links without icons: «Quiero ser cliente» (`rise`, «Abre tu cuenta en tres pasos.») and «Ya soy cliente» (`light`, «Entra con tu usuario y tu contraseña.»). The logout notice stays above the doors.
  2. The intro layer, import map and module stay untouched.
  3. Update the welcome test (it expects the stacked logo) and add one: the doors page has the backdrop, both doors link to `/signup` and `/login?form`, and no header.
- **Acceptance criteria:**
  - [x] Login tests pass, intro tests included.

### [x] T7 — Login form over the big A (~5 min)

- **Layer:** adapter.in (templates)
- **Files:** `templates/login.html`, `LoginControllerTests.java`
- **Depends on:** T6
- **What to do:**
  1. Form view: backdrop, form on `surface`, «Entrar» as `.btn-slant-rise`, «Volver» as a secondary link; password toggle restyled (same fragment).
  2. Test: the form view has the backdrop.
- **Acceptance criteria:**
  - [x] Login tests pass; the error and blocked notices still show.

### [x] T8 — Sign-up steps over the big A (~10 min)

- **Layer:** adapter.in (templates)
- **Files:** `templates/signup/{data,account,access,progress}.html`, `SignUpControllerTests.java`
- **Depends on:** T5
- **What to do:**
  1. The three steps: backdrop, the stacked logo removed (deviation: the header already shows the logo, so a second one in the page would repeat it), buttons `.btn-slant`, progress bar as a thin `rise` bar (keep `signup-progress-bar` and its view transition).
  2. Test: each step has the backdrop.
- **Acceptance criteria:**
  - [x] Sign-up tests pass, field and validation tests included.

### [x] T9 — Account list (~10 min)

- **Layer:** adapter.in (templates)
- **Files:** `templates/accounts/list.html`, `AccountListControllerTests.java`
- **Depends on:** T4, T5
- **What to do:**
  1. Total balance in Tektur (class `amount`); each account a `.card-slant` link with the `watermark`, masked IBAN, segment and balance; «Abrir cuenta» / «Abrir otra cuenta» as `.btn-slant-light`. The admin list (with holder names and initials) keeps its information.
  2. Test: each account card links to its detail and carries the watermark.
- **Acceptance criteria:**
  - [x] List tests pass for admin and customer.

### [x] T10 — Account detail: summary (~5 min)

- **Layer:** adapter.in (templates)
- **Files:** `templates/accounts/detail.html`
- **Depends on:** T4
- **What to do:**
  1. Holder · segment, IBAN (`<span class="text-nowrap">` kept), balance in Tektur with class `balance`, watermark behind; notices restyled.
- **Acceptance criteria:**
  - [x] Detail and IBAN regeneration tests pass unchanged.

### [x] T11 — Account detail: action track (~10 min)

- **Layer:** adapter.in (templates + static)
- **Files:** `templates/accounts/detail.html`, `static/css/app.css`, `AccountDetailControllerTests.java`
- **Depends on:** T10
- **What to do:**
  1. The three `<details>` keep their ids; each `<summary>` is a slanted button with its glyph (`rise`, `drop`, transfer icon), side by side and interlocked; the open one fills with its colour (rise, drop, ink) and its form shows below the track, full width. Try `details { display: contents }` with `::details-content`; if a browser breaks it, keep today's wrap (the open one moves to its own row).
  2. Submit buttons with the action's colour; «Abrir otra cuenta» and the admin's «Generar nuevo IBAN» as secondary actions under the track; the IBAN confirmation button is `.btn-slant-drop`, since it invalidates the current IBAN.
  3. Test: each summary carries its glyph; the `open` tests keep passing.
- **Acceptance criteria:**
  - [x] All operation tests (deposit, withdrawal, transfer, IBAN) pass, htmx fragment included.
  - [x] Works without JavaScript (the details open by themselves).

### [x] T12 — Account detail: transactions with brand glyphs (~10 min)

- **Layer:** adapter.in (templates)
- **Files:** `templates/accounts/detail.html`, `AccountDetailControllerTests.java`
- **Depends on:** T10
- **What to do:**
  1. Each row (class `movement`) has a slanted chip with `rise` for an approved deposit (with or without counterpart), `drop` for an approved withdrawal, `drop` in `flat` grey for a rejected one; amounts in Tektur, deposits in `rise`; «Rechazado» in `drop`, the amount struck through (as today).
  2. Tests: a deposit row has the rise glyph, a withdrawal the drop, a rejected one the grey drop.
- **Acceptance criteria:**
  - [x] The new tests and the existing transaction tests pass.

### [ ] T13 — Post-operation animation selectors (~5 min)

- **Layer:** adapter.in (static)
- **Files:** `static/js/app.js`
- **Depends on:** T10, T12
- **What to do:**
  1. `.display-5` → `.balance`, `.list-group-item` → `.movement`.
  2. Check by hand: after a deposit with htmx, the balance and the new row animate; with reduced motion, nothing moves.
- **Acceptance criteria:**
  - [ ] Checked in the browser (Playwright, `reducedMotion` on and off).

### [ ] T14 — Remaining screens (~10 min)

- **Layer:** adapter.in (templates)
- **Files:** `templates/accounts/new.html`, `templates/accounts/another.html`, `templates/error/403.html`, `templates/error/404.html`
- **Depends on:** T5
- **What to do:**
  1. Headings, forms and buttons with the new classes; error pages with `.btn-slant-light` to go home.
- **Acceptance criteria:**
  - [ ] Opening, another-account and error tests pass.

### [ ] T15 — Visual check against the sketches (~10 min)

- **Layer:** tests
- **Files:** none committed (scratch Playwright script)
- **Depends on:** T6–T14
- **What to do:**
  1. `./mvnw spring-boot:run` (demo profile); screenshots of doors, login form, sign-up step 1, list (customer and admin), detail with each action open, at 360, 390 and 1280 px; again with JavaScript off and with reduced motion.
  2. Keyboard pass: every door, action and button shows its focus.
  3. Show the screenshots to the user before the PR.
- **Acceptance criteria:**
  - [ ] No horizontal scroll at 360 px; text contrast ≥ 4.5:1; focus visible everywhere.

### [ ] T16 — Docs and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/brand/README.md`, `docs/ia/estado-del-proyecto.md`, `docs/ia/historial.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`
- **Depends on:** T15
- **What to do:**
  1. Brand README: a "Web interface" section with the tokens, the slant, the glyphs and where each one is used.
  2. Status: #185 done and next step; decision (direction C, Sora, backdrop on entry screens); pending (the `details` track fallback if used, anything found in T15); history row.
- **Acceptance criteria:**
  - [ ] The CI's project-status check passes.

## Global acceptance criteria

**Specific:**

- [ ] The welcome doors look like the approved sketch: the darkened A bigger than the screen behind, logo, greeting and both doors centred, no icons on the doors.
- [ ] The login form and the three sign-up steps carry the big A; inside the app only the small watermark.
- [ ] Main buttons and the account actions are parallelograms with the A's slant and a visible keyboard focus.
- [ ] Transactions show the brand glyphs (rise, drop, rejected).
- [ ] Without JavaScript and with reduced motion everything works as today; the intro still plays once per session.
- [ ] Good from 360 px to desktop, without horizontal scroll.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit + static analysis).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit. No Java change outside tests is expected.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones: Sora is a vendored font justified in ADR 0023, not a Maven dependency.
- [ ] If the API changes: n/a (no API change).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a (no new business term).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **`<details>` side by side:** keeping the summaries in one track while the open form shows below needs `display: contents` on `<details>`, whose support is recent; T11 has a fallback (today's wrap). Rewriting the actions as buttons with Alpine.js would break the no-JavaScript rule, so it is not an option.
- **Focus with `clip-path`:** the outline is clipped; T5 must prove the replacement with the keyboard.
- **Tektur's `font-display: block`:** needed by the intro; on other pages it can hide headings for a moment on a slow first load (the font is 12 KB and preloaded, then cached a year).
- **Admin list:** the sketches show the customer view; the admin's (initials, holder names, «Abrir cuenta») follows the same cards; worth a look in T15.
- **Sketch reference:** the approved sketches live on a private design canvas; T15's screenshots are shown to the user as the final check.
