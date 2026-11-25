# Plan: monA Bank brand identity on the web (intro, logo, favicon, no phase footer)

- **Issue:** [#178](https://github.com/dawnrock/Proyect-DDD-Claude/issues/178)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-25
- **Status:** in progress
- **Total estimate:** ~120 min (14 tasks)

## Goal

Give the web the monA Bank identity designed with the user on 2026-11-25: the logo («monA» with a market chart painted inside the letters and an arrow rising from the A) in the header, the welcome screens and the favicon; a 3-second animated intro before the login doors; the visible name written «monA Bank» everywhere the user reads it; and no «Fase 2 · arquitectura hexagonal» footer. Only on `Hexagonal`.

### Out of scope

- Code names (`MonABankIbans`, `isMonABank()`, packages, classes): domain language, they do not change.
- `Monolithic` (frozen) and the REST API behaviour (only the contract's descriptions and examples change).
- A light theme (the web is dark only, ADR 0016) and a Content-Security-Policy (pending since #111).
- Automated browser tests of the intro (no JS harness; a browser suite needs its own ADR).

## Context

The design lives in two prototypes made outside the repository; this plan stores them as reference in `docs/brand/prototypes/` (`intro.html`, the full animation; `logo.html`, the static logo). They draw on a `<canvas>` and find where to paint the chart by analysing the rasterised glyphs (`topLine`, the n's right stem, the A's left leg fitted by least squares). That analysis becomes a shared module so that the intro and the static SVG logo are the same drawing.

**Logo spec** (from the issue): Tektur (OFL, variable `wght`), «monA» 700 with «mon» at 75 % of the A, «Bank» 400 at 30 % of the A in `#b9c0c7`, right-aligned below. Inside the letters: top band of m and o in `#8b949c`; top bar and right stem of n down to the baseline in `#f0545c`; left leg of the A from the base to the apex in `#1fc8b0`, continued past the apex by a shaft 18 % of the A size with the leg's width and slant and a head about 2.6 × the stroke width. Letters `#eef0f2`. Painted strokes are 1.4 × the stroke thickness, clipped by the glyph.

**Intro spec:** see the issue's table (words, timing, once per session, Esc or click to skip, no intro with reduced motion, without JavaScript or if the font does not load).

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| docs | `docs/brand/prototypes/{intro,logo}.html`, `docs/brand/README.md` | Reference prototypes (this plan's commit) |
| build | `src/main/resources/static/vendor/tektur/` | Tektur variable woff2 (latin) + OFL + README with version and sha512 (from `@fontsource-variable/tektur` 5.3.0; no WebJar exists) |
| adapter.in.web (static) | `src/main/resources/static/js/brand-geometry.js` | Shared geometry (ES module, pure functions over an `ink(x, y)` function) |
| tests | `scripts/brand-geometry.test.mjs` | `node:test` over synthetic glyphs (run by the CI with `scripts/*.test.mjs`) |
| build | `scripts/build-brand.mjs` | Dev-time generator of the SVGs from Tektur's outlines (opentype.js, downloaded to a temporary folder, not a project dependency) |
| adapter.in.web (static) | `src/main/resources/static/brand/` | `monabank-horizontal.svg`, `monabank-stacked.svg`, `favicon.svg` (generated, committed) |
| adapter.in.web | `WebSecurityConfig` | `/brand/**` public |
| adapter.in.web (templates) | `fragments/layout.html`, every page using `footer` | Favicon, title «… · monA Bank», header logo; no footer |
| adapter.in.web (templates) | `login.html`, `signup/data.html` | Stacked logo on the welcome; intro markup on the login doors |
| adapter.in.web (static) | `src/main/resources/static/js/intro.js` | The intro (ES module), ported from the prototype |
| domain.model / application / adapter.in.web | `DuplicateCustomerException`, `CheckingAccount`, `NotACustomerException`, `SignUpController` | «Mon-A Bank» → «monA Bank» in user-facing messages |
| docs | `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `docs/dominio/glosario.md`, ADR 0022, ADR 0016 | Name in descriptions and examples (patch bump), brand ADR |

Decisions taken in this plan:

- **SVG files, not inline text with CSS:** CSS cannot clip a colour to part of a glyph and add the arrow. The SVGs hold the glyphs as paths (no font needed), so they work in `<img>` and as favicon. They are used with `<img alt="monA Bank">`.
- **One geometry, two renderers:** `brand-geometry.js` takes an `ink(x, y)` predicate. The intro builds it from canvas pixels (as the prototype); the generator builds it from the glyph outline (point in polygon, even-odd) and writes `<clipPath>` + polylines/polygons. Node 22 loads the `.js` ES module by syntax detection, so the browser and the generator import the same file.
- **Tektur is vendored**, like Motion: no WebJar is published (`org.webjars.npm` has none). The generator needs static instances (opentype.js does not apply variations): it downloads `@fontsource/tektur` 400 and 700 `.woff` at run time, pinned to 5.3.0.
- **The intro is a separate ES module** loaded only by `login.html`; it runs on a full page load only, so htmx-boosted navigations never replay it (ADR 0016's rule for pages with their own script).

## Tasks

### [x] T1 — Brand prototypes as reference (~5 min)

- **Layer:** docs
- **Files:** `docs/brand/prototypes/intro.html`, `docs/brand/prototypes/logo.html`, `docs/brand/README.md`
- **Depends on:** —
- **What to do:**
  1. Copy the two prototypes (done in this plan's commit; `logo.html` with the approved arrow, 18 %).
  2. `README.md`: what each one is, the spec above, and that they load Tektur from Google Fonts (reference only, never served).
- **Acceptance criteria:**
  - [x] Opening `docs/brand/prototypes/intro.html` in a browser plays the approved intro.

### [x] T2 — Vendor Tektur (~10 min)

- **Layer:** build
- **Files:** `src/main/resources/static/vendor/tektur/` (`tektur-latin-wght-normal.woff2`, `OFL.txt`, `README.md`), `src/main/resources/static/css/app.css`, test in `WebSecurityTests` or a new `StaticAssetsTests`
- **Depends on:** —
- **What to do:**
  1. Download `@fontsource-variable/tektur@5.3.0`, check the tarball against the registry's `dist.integrity`, copy the latin woff2 and the licence; README like Motion's.
  2. `@font-face { font-family: "Tektur"; src: url(/vendor/tektur/...) format("woff2"); font-weight: 400 900; font-display: block; }` in `app.css` (downloaded only when used).
  3. Test: `GET /vendor/tektur/tektur-latin-wght-normal.woff2` without a session answers 200 with `font/woff2`.
- **Acceptance criteria:**
  - [x] The font is served publicly with its MIME type; `./mvnw verify` green.

### [x] T3 — Shared brand geometry (~10 min)

- **Layer:** adapter.in.web (static) · tests
- **Files:** `src/main/resources/static/js/brand-geometry.js`, `scripts/brand-geometry.test.mjs`
- **Depends on:** —
- **What to do:**
  1. Test first (`node:test`): synthetic glyphs as `ink` functions (a rectangle «o», an «n» as an arch with two stems, an «A» as two slanted legs and a bar) check `strokeThickness`, `topLine` (centre of the top band, capped at the stroke), `rightStem` (down to the baseline), `leftLeg` (two fitted edges, crossbar rows ignored), `partial(path, f)` and `arrow(leg, length)` (shaft with the leg's width, head 2.6 × the width).
  2. Move those functions out of the prototype into the module, with no DOM access.
- **Acceptance criteria:**
  - [x] `node --test "scripts/*.test.mjs"` passes, the new file included.

### [x] T4 — SVG generator and the three logos (~10 min)

- **Layer:** build
- **Files:** `scripts/build-brand.mjs`, `src/main/resources/static/brand/{monabank-horizontal,monabank-stacked,favicon}.svg`, `scripts/README.md`
- **Depends on:** T3
- **What to do:**
  1. The script downloads opentype.js 2.0.0 and `@fontsource/tektur` 5.3.0 (400, 700 `.woff`) into a temporary folder (`npm pack`, integrity checked), reads the outlines, builds `ink` by point-in-polygon and uses `brand-geometry.js`.
  2. It writes each SVG: glyph paths in `#eef0f2`, one `<clipPath>` per painted letter, the grey and red polylines, the green leg polygon (clipped) and the arrow (not clipped), «Bank» in `#b9c0c7`; a tight `viewBox`; `role="img"` and `<title>monA Bank</title>`. The favicon is the A, its green leg and the arrow on a square `viewBox`.
  3. Run it, commit the SVGs, and document the command in `scripts/README.md` (needs Node 22 and internet; not part of the build).
- **Acceptance criteria:**
  - [x] The SVGs open in a browser and match `docs/brand/prototypes/logo.html` (screenshot side by side shown to the user).
  - [x] Re-running the script produces the same files (deterministic).

### [x] T5 — `/brand/**` public (~5 min)

- **Layer:** adapter.in.web
- **Files:** `WebSecurityConfig`, `WebSecurityTests`
- **Depends on:** T4
- **What to do:**
  1. Test: `GET /brand/favicon.svg` and `/brand/monabank-horizontal.svg` without a session answer 200 `image/svg+xml`.
  2. Add `/brand/**` to the public matchers and to the class Javadoc.
- **Acceptance criteria:**
  - [x] The test passes; other public/private rules unchanged.

### [ ] T6 — Header logo, favicon and page title (~10 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `fragments/layout.html`, `AccountListControllerTests`, `DddLabApplicationTests`
- **Depends on:** T5
- **What to do:**
  1. Tests first: the home page has `<img ... alt="monA Bank">` pointing at `monabank-horizontal.svg` inside the navbar brand, `<link rel="icon" ... favicon.svg>`, and a title ending in «· monA Bank»; the 404 page (`DddLabApplicationTests`, today «Cuentas corrientes») shows «monA Bank».
  2. `head`: `<link rel="icon" type="image/svg+xml" th:href="@{/brand/favicon.svg}">` and `th:text="${title} + ' · monA Bank'"`. `header`: the logo `<img>` (height ~1.75 rem) replaces the `landmark` icon and «Cuentas corrientes».
- **Acceptance criteria:**
  - [ ] Every page shows the logo and the favicon; no «Cuentas corrientes» left in templates.

### [ ] T7 — Remove the phase footer (~5 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `fragments/layout.html` and every template with `fragments/layout :: footer` (`login.html`, `signup/*`, `accounts/*`, `error/*`), `AccountListControllerTests`
- **Depends on:** T6
- **What to do:**
  1. Replace `theFooterOnlyShowsPhaseTwoHexagonalArchitecture` with a test that the page has no `<footer>` and no «arquitectura hexagonal».
  2. Delete the `footer` fragment and its uses; keep the pages' layout (`min-vh-100` body without `mt-auto` footer).
- **Acceptance criteria:**
  - [ ] No template references `:: footer`; the test passes.

### [ ] T8 — Stacked logo on the welcome screens (~5 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `login.html`, `signup/data.html`, `LoginControllerTests`, `SignUpControllerTests`
- **Depends on:** T5
- **What to do:**
  1. Tests: the login doors (`/login` without `form`) and the sign-up's first step show `monabank-stacked.svg` with `alt=""` (decorative: the `h1` already names the bank) and the `h1` reads «Bienvenido a monA Bank» / «Hazte cliente de monA Bank».
  2. The `<img>` above the `h1`, centred, max width ~18 rem.
- **Acceptance criteria:**
  - [ ] Both tests pass; the login form (`/login?form`) has no stacked logo.

### [ ] T9 — Name in domain messages (~5 min)

- **Layer:** domain
- **Files:** `DuplicateCustomerException`, `CheckingAccount` (R32 message), `CustomerTests`, `CheckingAccountTests`
- **Depends on:** —
- **What to do:**
  1. Change the expected messages in the tests to «monA Bank», see them fail, change the two messages. Update `@DisplayName`s that name the bank.
- **Acceptance criteria:**
  - [ ] Domain tests green; `check-domain.mjs` without errors.

### [ ] T10 — Name in application and web messages (~10 min)

- **Layer:** application · adapter.in
- **Files:** `NotACustomerException`, `SignUpController`, their tests, `CustomerControllerTests`, `DddLabApplicationTests`, and Javadocs naming the bank (`CustomerDirectory`, `AccountOpeningCommands`)
- **Depends on:** T9
- **What to do:**
  1. Tests first (welcome flash «Te damos la bienvenida a monA Bank…», REST `detail` «Ya eres cliente de monA Bank…», end-to-end sign-up), then the messages.
  2. Remaining `@DisplayName`s and Javadocs with «Mon-A Bank» → «monA Bank» (text only, no code names).
- **Acceptance criteria:**
  - [ ] `grep -rn "Mon-A Bank" src` returns nothing; `./mvnw verify` green.

### [ ] T11 — Name in the remaining templates (~5 min)

- **Layer:** adapter.in.web (templates)
- **Files:** `signup/account.html`, `accounts/another.html`, `accounts/detail.html` and the web tests that read those texts
- **Depends on:** T10
- **What to do:**
  1. «Acepto las condiciones de monA Bank», «un IBAN de monA Bank», «generado por monA Bank»; adjust any assertion.
- **Acceptance criteria:**
  - [ ] No «Mon-A» in `src/main/resources/templates`.

### [ ] T12 — Intro markup and script (~10 min)

- **Layer:** adapter.in.web (templates + static)
- **Files:** `login.html`, `src/main/resources/static/js/intro.js`, `app.css`, `LoginControllerTests`
- **Depends on:** T2, T3, T8
- **What to do:**
  1. Test: `/login` (doors) has `<div class="splash" id="splash" role="img" aria-label="monA Bank" hidden>` with a `<canvas>` and loads `intro.js` as a module; `/login?form` has neither.
  2. `intro.js`: port the prototype (`docs/brand/prototypes/intro.html`): words layout, timeline, flyers and logo layer, importing `brand-geometry.js`. The splash starts `hidden` and the script shows it only when it will run: not seen in this session (`sessionStorage`, guarded with try/catch), no `prefers-reduced-motion: reduce`, and Tektur loads (`document.fonts.load` + `check`); otherwise the page shows as today. Esc or a click removes it.
  3. `app.css`: `.splash` full-screen over the page with the body background; nothing for reduced motion beyond the script check.
- **Acceptance criteria:**
  - [ ] The test passes; manual check in the browser (`./mvnw spring-boot:run`): first visit plays the intro, «Volver» from the form does not, reduced motion and JavaScript off show the doors directly.

### [ ] T13 — Contract and glossary texts (~5 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `docs/dominio/glosario.md`
- **Depends on:** T10
- **What to do:**
  1. «Mon-A Bank» → «monA Bank» in descriptions, summaries and the `DuplicateCustomer` example; patch bump of `info.version` (documentation only, ADR 0014) and a CHANGELOG entry.
  2. Glossary: same rename in the definitions; add «monA Bank» as the bank's name if a row fits.
  3. `npx @redocly/cli lint docs/api/openapi.yaml`.
- **Acceptance criteria:**
  - [ ] Lint without errors; `check-contract-version.mjs` passes against `Hexagonal`.

### [ ] T14 — ADR 0022 and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0022-brand-identity.md`, `docs/adr/0016-web-frontend-libraries.md`, `docs/adr/README.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1-T13
- **What to do:**
  1. ADR 0022: the logo and its meaning, SVG generated from the outlines (and why not CSS), shared geometry, vendored Tektur (OFL), dev-time opentype.js (not a dependency), the intro's rules; alternatives (inline text + font, canvas logo, Lottie/video).
  2. ADR 0016: Tektur in the libraries table and the vendored-files paragraph.
  3. Status files: issue done, decision row, pending items (no browser test of the intro; `/favicon.ico` still unanswered by a file), history row.
- **Acceptance criteria:**
  - [ ] `node scripts/check-project-status.mjs` passes on the PR's diff.

## Global acceptance criteria

**Specific:**

- [ ] The first visit to `/login` in a browser session plays the 3-second intro and then shows the doors; later visits in the same session do not.
- [ ] With reduced motion, without JavaScript or if Tektur does not load there is no intro and the page works as today.
- [ ] Every page shows the header logo (`alt="monA Bank"`), the SVG favicon and a title ending in «· monA Bank».
- [ ] No «Mon-A Bank», «Cuentas corrientes» or «Fase 2 · arquitectura hexagonal» left in what the user sees (templates, messages, contract texts).
- [ ] The generated SVGs match the approved logo (shown to the user before the PR).

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR): Tektur vendored and opentype.js at dev time, both in ADR 0022.
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it with `info.version` bumped per ADR 0014 and an entry in `docs/api/CHANGELOG.md`, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes (texts only: patch bump).
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Outline analysis vs. pixels:** the prototype analyses canvas pixels; the generator analyses the outline with point-in-polygon. Sampling steps must match so the SVG and the intro's final frame look the same; T4 compares them visually.
- **opentype.js and the variable font:** opentype.js does not apply variations, hence the static `@fontsource/tektur` instances for the generator. The intro keeps the variable font to animate the weight.
- **ES module served by Spring:** `.js` is served as `text/javascript`, valid for `<script type="module">`; checked in T12's test.
- **hx-boost:** `login.html` is a boosted page; its module script only runs on a full load, which is what the once-per-session rule wants. A boosted navigation to the doors (logout, «Volver») never shows the intro.
- **No automated test of the animation itself:** only the markup, the geometry (`node:test`) and a manual check; recorded in `pendientes.md`.
- **Jar size:** the font adds about 30 KB and the SVGs a few KB.
