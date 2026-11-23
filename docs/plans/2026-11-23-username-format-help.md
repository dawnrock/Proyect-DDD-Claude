# Plan: Explain the username format before it fails

- **Issue:** [#152](https://github.com/dawnrock/Proyect-DDD-Claude/issues/152)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-23
- **Status:** in progress
- **Total estimate:** ~45 min (5 tasks)

## Goal

The user could not create a sign-up username: the format (`[a-z0-9._-]{3,30}`) is only learnt from the error, and the error forgets the underscore. After this plan the web shows the format under the field (announced by screen readers), lower-cases what is typed (decided by the user on 2026-11-23: **web only**, the API keeps requiring the exact format), and every error message describes all the accepted characters.

### Out of scope

- Normalising the username in `application` or the REST API (the user chose web only; the contract does not change).
- Accepting accents, `ñ` or spaces (the format stays).
- `Monolithic` (frozen).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `application` | `OpenAccountCommandDef.java`, `SignUpCommandDef.java` | one shared message constant `USERNAME_MESSAGE` (like `TAX_ID_MESSAGE`) naming every accepted character |
| `adapter.in.rest` | `SignUpRequest.java`, `OpenAccountRequest.java` | use the constant (the error text changes, the contract's `pattern` does not) |
| `adapter.in.web` | `SignUpForm.java`, `OpenAccountForm.java` | use the constant; their setters strip and lower-case the username before validation |
| `adapter.in.web` (templates) | `signup/access.html`, `accounts/new.html` | help text under the field, in `aria-describedby` together with the error id; `maxlength="30"` |

**New message:** "El usuario debe tener de 3 a 30 caracteres: minúsculas sin tildes ni ñ, dígitos, puntos, guiones bajos o guiones" (the admin form keeps its "del titular" variant through the same text with a prefix, or a second constant).

**Normalisation in the web form** (decided here, reversible): the Lombok `@Setter` of the username is replaced by a hand-written setter `setUsername(String)` that does `strip()` + `toLowerCase(Locale.ROOT)` (null stays null; an empty `ownerUsername` stays empty, since it is optional). Spring binds through the setter, so validation and the command already see `margaret`. Not in an `@InitBinder`: the rule belongs to the field.

**No `pattern`/`minlength`/`maxlength` attribute** (`maxlength` removed in T5): with lower-casing, `Manu` is valid input but would match `:invalid` against a lower-case `pattern`, and Firefox/Safari expose `:invalid` to screen readers; `minlength` has the same effect while typing. Only `maxlength="30"`, which cannot produce an invalid state.

**`aria-describedby` with help and error:** `th:aria-describedby="${#fields.hasErrors('username')} ? 'username-help username-error' : 'username-help'"`. The template guard of #145 keeps passing (the attribute is there).

## Tasks

### [x] T1 — One username message that names every accepted character (~10 min)

- **Layer:** application
- **Files:** `OpenAccountCommandDef.java`, `SignUpCommandDef.java`, `SignUpRequest.java`, `OpenAccountRequest.java`, `SignUpForm.java`, `OpenAccountForm.java`, tests that assert the old text
- **Depends on:** —
- **What to do:**
  1. Test (red): a `SignUpCommand` with username `Ab` is rejected with a message that mentions "guiones bajos"; same for the admin opening's owner username.
  2. Constants `USERNAME_MESSAGE` and `OWNER_USERNAME_MESSAGE` in `OpenAccountCommandDef`; use them in the six places; update the web test that checks the old text.
- **Acceptance criteria:**
  - [x] The new tests pass; no copy of the old text is left (`grep "puntos o guiones"` finds nothing).
  - [x] `./mvnw verify` is green and `oasdiff` sees no contract change.

### [x] T2 — The web lower-cases the username (~10 min)

- **Layer:** adapter.in
- **Files:** `SignUpForm.java`, `OpenAccountForm.java`, `SignUpControllerTests.java`, `AccountWebControllerTests.java`
- **Depends on:** T1
- **What to do:**
  1. Test (red): sign-up step 2 with ` Margaret ` goes on to step 3 and the final `SignUpCommand` carries `margaret`; the admin opening with `ownerUsername` `Ana.Perez` sends `ana.perez`; `Íñigo` is still rejected with the new message; an empty `ownerUsername` stays optional.
  2. Hand-written setters with `strip()` + `toLowerCase(Locale.ROOT)`.
- **Acceptance criteria:**
  - [x] The new tests pass and `./mvnw verify` is green.

### [x] T3 — Help text under the username fields (~10 min)

- **Layer:** adapter.in
- **Files:** `templates/signup/access.html`, `templates/accounts/new.html`, `SignUpControllerTests.java`, `AccountWebControllerTests.java`
- **Depends on:** T2
- **What to do:**
  1. Test (red): step 2 renders `<p class="form-text" id="username-help">` with the format and "se guarda en minúsculas", and the field has `aria-describedby="username-help"` and `maxlength="30"`; with an error, `aria-describedby="username-help username-error"`. Same for `ownerUsername` in `accounts/new`.
  2. Add the help text and the attributes.
- **Acceptance criteria:**
  - [x] The new tests pass; `AccessibleFieldErrorsTemplatesTests` still passes.
  - [x] Checked by hand in Chromium: typing `Margaret` signs up as `margaret`.

### [x] T4 — Record the decision (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`
- **Depends on:** T3
- **What to do:**
  1. Note that the web lower-cases usernames and the API does not (#152, user decision), so the shared validation of the web and REST differs on purpose in this one field.
- **Acceptance criteria:**
  - [x] Decision recorded.

### [x] T5 — Review fixes: no `maxlength` (~5 min)

- **Layer:** adapter.in
- **Files:** `templates/signup/access.html`, `templates/accounts/new.html`, their tests
- **Depends on:** T3
- **What to do:**
  1. `revisor-ddd`: `maxlength="30"` silently cuts a longer paste, which could still match the pattern and sign up a different username than the one pasted. Remove it and let the server's message explain the length; the tests now require no `maxlength`.
- **Acceptance criteria:**
  - [x] The tests pass and `./mvnw verify` is green.

## Global acceptance criteria

**Specific:**

- [x] The username format is visible before submitting and announced by screen readers (sign-up and admin opening).
- [x] The web accepts `Margaret` as `margaret`; the API still rejects upper case.
- [x] Every username error message names every accepted character.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR).
- [x] If the API changes: n/a (only the text of an error message changes; the contract's `pattern` and responses do not, so no version bump).
- [x] Any new business term is in `docs/dominio/glosario.md` (n/a: no new term).
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- Signing in after a lower-cased sign-up: checked before planning, no risk. Spring Security's `InMemoryUserDetailsManager` looks users up case-insensitively and the principal is the stored name (`POST /api/auth/token` with `ADA`/`demo` returns a token with `sub: ada` and the account list works), so `Margaret` can sign in as typed.
