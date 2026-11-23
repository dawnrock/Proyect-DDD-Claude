# Plan: Web forms: required fields, mobile keyboard and show password

- **Issue:** [#146](https://github.com/dawnrock/Proyect-DDD-Claude/issues/146)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-23
- **Status:** in progress
- **Total estimate:** ~45 min (4 tasks)

## Goal

Finish the form ergonomics found by the front-end review (`docs/analysis/front-review-hexagonal.md`, findings 3-5; guides `autofill-sign-in-form` and `autofill-sign-up-form` of `modern-web-guidance`): mandatory fields say so (`required`), mobile keyboards behave (`autocapitalize`, `spellcheck`, `enterkeyhint`) and the login and sign-up password fields can be shown. The server-side validation and its Spanish messages do not change: the forms keep `novalidate`.

### Out of scope

- Asking for the password only once in the sign-up (#147, user decision).
- Client-side validation messages (`novalidate` stays; Spring gives the messages).
- `Monolithic` (frozen).

## Context

Only `adapter.in.web` templates, `static/js/app.js` and `static/css/app.css`; no Java change, no API change, no new dependency.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in` (templates) | `accounts/detail.html`, `accounts/new.html`, `accounts/another.html` | `required` on mandatory fields; `autocapitalize="characters"` on tax id and target IBAN |
| `adapter.in` (templates) | `signup/data.html`, `access.html`, `account.html` | `required`; `enterkeyhint="next"` (steps 1-2) and `"done"` (last field of step 3); `autocapitalize="characters"` on the tax id |
| `adapter.in` (templates) | `login.html` | `autocapitalize="none"` + `spellcheck="false"` on the user name |
| `adapter.in` (templates, static) | `login.html`, `signup/access.html`, `app.js`, `app.css` | show-password toggle (Alpine.js) |
| tests | `AccountWebControllerTests`, `AnotherAccountWebTests`, `SignUpControllerTests`, `WebSecurityTests` (login page) | rendered attributes |

**Mandatory fields** (from the form annotations): initial deposit (`OpenAccountForm`, `AnotherAccountForm`, `SignUpForm`), amount (`MovementForm`, `TransferForm`), target IBAN (`TransferForm`), holder tax id (`OpenAccountForm`), name, tax id and e-mail (sign-up step 1), user name and both passwords (step 2), conditions (step 3). Currency selects always have a value: no `required`. Holder name, e-mail and user name in `accounts/new` are optional on purpose (existing customer): no `required`.

**Show password** (decided here, reversible): Bootstrap `input-group` with the password input and a `<button type="button">` (eye icon + visually hidden text "Mostrar contraseña", `aria-pressed`, `aria-controls`). An Alpine component `passwordToggle` in `app.js` switches the input's `type` between `password` and `text`. The button is rendered with `hidden` and Alpine removes it (`x-bind:hidden="false"`), so without JavaScript it never shows. The label stays constant (a toggle button announces its state with `aria-pressed`). On the sign-up step 2, each password field gets its own button. The `input-group` needs `has-validation` so the `invalid-feedback` still shows.

## Tasks

### [x] T1 — Account forms: required fields and IBAN keyboard (~10 min)

- **Layer:** adapter.in
- **Files:** `templates/accounts/detail.html`, `templates/accounts/new.html`, `templates/accounts/another.html`, `AccountWebControllerTests.java`, `AnotherAccountWebTests.java`
- **Depends on:** —
- **What to do:**
  1. Test (red): the detail page renders `required` on the three amounts and the target IBAN, and `autocapitalize="characters"` on the IBAN; `accounts/new` renders `required` on tax id and deposit, not on holder name, e-mail and user name, and `autocapitalize="characters"` on the tax id; `accounts/another` renders `required` on the deposit.
  2. Add the attributes.
- **Acceptance criteria:**
  - [x] The new tests pass and `./mvnw verify` is green.

### [x] T2 — Sign-up steps: required fields and mobile keyboard (~10 min)

- **Layer:** adapter.in
- **Files:** `templates/signup/data.html`, `templates/signup/access.html`, `templates/signup/account.html`, `SignUpControllerTests.java`
- **Depends on:** —
- **What to do:**
  1. Test (red): step 1 renders `required` + `enterkeyhint="next"` on name, tax id and e-mail, and `autocapitalize="characters"` on the tax id; step 2 `required` + `enterkeyhint="next"` on user name and passwords; step 3 `required` on deposit and conditions and `enterkeyhint="done"` on the deposit.
  2. Add the attributes.
- **Acceptance criteria:**
  - [x] The new tests pass and `./mvnw verify` is green.

### [x] T3 — Login user name keyboard (~5 min)

- **Layer:** adapter.in
- **Files:** `templates/login.html`, `LoginControllerTests.java`
- **Depends on:** —
- **What to do:**
  1. Test (red): the login form's user name has `autocapitalize="none"` and `spellcheck="false"`, and keeps `autocomplete="username"` and `required`.
  2. Add the attributes.
- **Acceptance criteria:**
  - [x] The new test passes and `./mvnw verify` is green.

### [ ] T4 — Show-password toggle (~20 min)

- **Layer:** adapter.in
- **Files:** `templates/login.html`, `templates/signup/access.html`, `static/js/app.js`, `static/css/app.css` (if needed), tests of both pages
- **Depends on:** T2, T3
- **What to do:**
  1. Test (red): the login and step-2 pages render, for each password field, a `<button type="button">` with `hidden`, `x-bind:hidden="false"`, `aria-pressed="false"`, `aria-controls="<field id>"` and the text "Mostrar contraseña"; the password is never written back (no `value`).
  2. Markup with `input-group has-validation`, Alpine component `passwordToggle` in `app.js` (toggles `type` and `aria-pressed`).
  3. Check by hand in Chromium (Playwright): with JavaScript the button shows and toggles `type`/`aria-pressed` with the keyboard; without JavaScript it does not show; the field's error message still shows inside the input group.
- **Acceptance criteria:**
  - [ ] The new tests pass and `./mvnw verify` is green.
  - [ ] The manual checks pass (reported in the PR).

## Global acceptance criteria

**Specific:**

- [ ] Mandatory fields carry `required` and the optional ones of `accounts/new` do not.
- [ ] The mobile keyboard attributes are on the fields listed above.
- [ ] The show-password button works with the keyboard, announces its state (`aria-pressed`) and does not show without JavaScript.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no API change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: no new term).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- `required` with `novalidate`: the browser does not block the submit, but screen readers announce "required"; nothing changes for sighted users.
- Switching a `new-password` input to `type="text"` may let some password managers offer to save while visible; it goes back to `password` on the next toggle.
