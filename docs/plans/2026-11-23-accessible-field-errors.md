# Plan: Accessible validation errors and focus on the first error in the web forms

- **Issue:** [#145](https://github.com/dawnrock/Proyect-DDD-Claude/issues/145)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-23
- **Status:** done
- **Total estimate:** ~45 min (5 tasks)

## Goal

Screen reader users must learn that a field is wrong and why. Today a field with an error only gets the visual `is-invalid` class and a sibling `<p class="invalid-feedback">`: no `aria-invalid`, no `aria-describedby`. And since the forms use `novalidate`, after a failed submit the focus stays at the top of the page (full page) or is lost (account panel replaced by htmx). After this plan every field with an error is announced with its message, and the focus lands on the first error. Source: `docs/analysis/front-review-hexagonal.md` (findings 1 and 2), guide `accessible-error-announcement` of `modern-web-guidance`.

### Out of scope

- `required`, mobile keyboard attributes and the show-password button (#146).
- Client-side validation with `:user-invalid` (the errors come from the server; the guide's JS bridge is not needed).
- `Monolithic` (frozen).

## Context

Only `adapter.in.web` templates and `static/js/app.js`; no change in Java code, `application` or the API.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in` (templates) | `templates/accounts/detail.html` | deposit, withdrawal and transfer fields: `aria-invalid` + `aria-describedby` + error `id` |
| `adapter.in` (templates) | `templates/accounts/new.html`, `templates/accounts/another.html` | same for every field |
| `adapter.in` (templates) | `templates/signup/data.html`, `access.html`, `account.html` | same for every field |
| `adapter.in` (static) | `static/js/app.js` | focus the first `[aria-invalid="true"]` (or the first `.alert[role=alert]` inside a form) on page load and after an htmx swap |
| tests | `AccountWebControllerTests`, `AnotherAccountWebTests`, `SignUpControllerTests`, new `AccessibleFieldErrorsTemplatesTests` | rendered attributes and a template-wide guard |

**Pattern** (decided here, reversible): the error message keeps its `<p class="invalid-feedback">` and gets `id="<field id>-error"`; the field gets `th:aria-invalid="${#fields.hasErrors('x')} ? 'true'"` and `th:aria-describedby="${#fields.hasErrors('x')} ? '<field id>-error'"`. A ternary without an else gives `null` and Thymeleaf leaves the attribute out, so a field without error carries neither attribute (the guide's `aria-invalid="false"` default is for client validation of `required` fields, which these forms do not do). Attributes inline rather than a new `fragments/form :: field(...)` fragment: the fields differ (`th:field` vs. explicit `name` for passwords, `select`, checkbox) and a fragment would change every template's structure for little gain.

**Focus:** a full page load and an htmx swap both end in `app.js`: on `DOMContentLoaded` and on `htmx:afterSettle`, if the page (or the swapped target) has a `[aria-invalid="true"]`, focus the first one; otherwise, if it has an `.alert[role=alert]` inside a `<form>` (global error), give it `tabindex="-1"` and focus it. `autofocus` stays on fields without errors; the script runs after it and wins only when there is an error. Without JavaScript, nothing changes.

## Tasks

### [x] T1 — Account actions announce their field errors (~10 min)

- **Layer:** adapter.in
- **Files:** `templates/accounts/detail.html`, `AccountWebControllerTests.java`
- **Depends on:** —
- **What to do:**
  1. Test (red): an invalid deposit (amount `0`) renders `aria-invalid="true"` and `aria-describedby="deposit-amount-error"` on the amount, and `id="deposit-amount-error"` on its message; the detail page without errors contains no `aria-invalid`. Same check for an invalid transfer IBAN (`transfer-target`), also through htmx.
  2. Add the pattern to the amount, currency and IBAN fields of the three forms.
- **Acceptance criteria:**
  - [x] The new tests pass and `./mvnw verify` is green.
  - [x] A detail page without errors has no `aria-invalid` attribute.

### [x] T2 — Opening forms announce their field errors (~5 min)

- **Layer:** adapter.in
- **Files:** `templates/accounts/new.html`, `templates/accounts/another.html`, `AccountWebControllerTests.java`, `AnotherAccountWebTests.java`
- **Depends on:** T1
- **What to do:**
  1. Test (red): an invalid opening (blank tax id, `0` deposit) and an invalid "another account" (`0` deposit) render the attributes on the wrong fields.
  2. Add the pattern to every field of both templates.
- **Acceptance criteria:**
  - [x] The new tests pass and `./mvnw verify` is green.

### [x] T3 — Sign-up steps announce their field errors (~10 min)

- **Layer:** adapter.in
- **Files:** `templates/signup/data.html`, `templates/signup/access.html`, `templates/signup/account.html`, `SignUpControllerTests.java`
- **Depends on:** T1
- **What to do:**
  1. Test (red): step 1 with a blank name, step 2 with a short password, step 3 without accepting the conditions render the attributes on those fields.
  2. Add the pattern to every field of the three steps (the password fields use `th:classappend` and an explicit `name`; same attributes).
- **Acceptance criteria:**
  - [x] The new tests pass; the existing step-2 tests that compare the literal `<input …>` start still pass unchanged (the new attributes go after it).
  - [x] `./mvnw verify` is green.

### [x] T4 — Template-wide guard (~5 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/adapter/in/web/AccessibleFieldErrorsTemplatesTests.java`
- **Depends on:** T2, T3
- **What to do:**
  1. Like `HtmxBoostTemplatesTests`: walk `templates/**/*.html`; every `<input>`/`<select>` that can be marked invalid (`th:errorclass` or `is-invalid`) must carry `th:aria-invalid` and `th:aria-describedby`, and each `invalid-feedback` must have an `id`.
  2. It passes at once (characterization): validate it by removing the attributes from one field (red) and restoring them.
- **Acceptance criteria:**
  - [x] The test passes; the sabotage made it fail naming the template.

### [x] T5 — Focus the first error after a failed submit (~15 min)

- **Layer:** adapter.in
- **Files:** `static/js/app.js`
- **Depends on:** T1
- **What to do:**
  1. Add `focusFirstError(root)` and call it on `DOMContentLoaded` (root `document`) and on `htmx:afterSettle` (root the swap target).
  2. There is no JavaScript test harness in the project (and adding one would be a new dependency): check it by hand with the preinstalled Chromium/Playwright against `./mvnw spring-boot:run`: a failed opening (full page) and a failed deposit (htmx) leave `document.activeElement` on the first wrong field; a page without errors keeps the `autofocus` field.
- **Acceptance criteria:**
  - [x] Both manual checks pass (reported in the PR).
  - [x] `./mvnw verify` is green.

## Global acceptance criteria

**Specific:**

- [x] Every field with an error has `aria-invalid="true"` and `aria-describedby` pointing to its message; without error, neither attribute.
- [x] After a failed submit (full page or htmx panel) the focus is on the first wrong field.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR).
- [x] If the API changes: n/a (no API change).
- [x] Any new business term is in `docs/dominio/glosario.md` (n/a: no new term).
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- The focus is not covered by an automated test (no JS harness); the template guard and the MockMvc tests cover the markup it relies on. A browser test suite would be a separate decision with its ADR.
- Moving the focus on page load also scrolls to the field; on the sign-up and opening pages the first error is near the top, so it is not disruptive.
