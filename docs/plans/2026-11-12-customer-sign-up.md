# Plan: Customer sign-up from the login page

- **Issue:** [#95](https://github.com/dawnrock/Proyect-DDD-Claude/issues/95)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic` (frozen; `src/` exception authorised by the user on 2026-11-12)
- **Date:** 2026-11-12 (revision 2: entity code 9999, unique document)
- **Status:** draft
- **Total estimate:** ~255 min (28 tasks; back and web in parallel after T0)
- **Requires:** #94 merged (no segment in commands, forms or requests)

## Goal

A visitor can become a customer of **Mon-A Bank** without the administrator. The login page offers two doors ("Ya soy cliente" / "Quiero ser cliente"); the second one opens a three-step sign-up (personal data, access, account) that creates the user and the account at once and leaves the customer signed in on the account detail. The holder now has an identity document and an e-mail, the bank assigns every IBAN, and the administrator can only replace an IBAN with another generated one.

### Out of scope

- E-mail verification, password recovery or change, rate limiting of sign-ups.
- Validating the check digits of IBANs typed by the user (transfer targets): only generated IBANs are guaranteed valid.
- Persisting users: they live in memory, like the accounts.
- The same change on `Hexagonal`: twin issue, created when this plan is approved. Both branches must end with the same behaviour.

## Context

Decisions confirmed by the user (2026-11-12): immediate opening; IBAN always assigned by the bank; the administrator can only swap an IBAN for a generated one; holder with document and e-mail; the identity document is unique (one customer per document, whatever the e-mail); REST follows the existing OpenAPI design.

Decisions taken in this plan:

- **Mon-A Bank IBAN:** `ES` + 2 IBAN check digits (mod 97) + entity `9999` + branch `0001` + 2 Spanish control digits + 10-digit account number. The account number is a sequence kept in `persistence` (`0000000001`, `0000000002`…), so the demo accounts always get the same IBANs. `9999` is invented; it has not been checked against the Banco de España registry.
- **Value objects:** `IdentityDocument` (DNI: 8 digits + control letter; NIE: `X`/`Y`/`Z` + 7 digits + control letter; normalised to upper case) and `EmailAddress` (normalised to lower case). Plain JDK, no Lombok.
- **Uniqueness (R23):** no two customers share an identity document, whatever the e-mail. Checked by the use case against the repository, like R16, with `DuplicateCustomerException` (domain, 422).
- **Users at runtime.** `application` must not depend on Spring Security (ArchUnit), so credentials stay in `presentation`: a shared `CustomerRegistration` component checks that the username is free, calls `CheckingAccountService.signUp` and then creates the user (`USER`, BCrypt) in the `InMemoryUserDetailsManager`. If the use case fails, no user is created.
- **Wizard state:** a session-scoped `SignUpForm` (`@SessionAttributes`), one validation group per step, cleared on completion. The password stays in the server session between steps 2 and 3, never in the page.
- **Routes.** Web: `GET /login` (doors), `GET /login?form` (form; `?error` also shows it), `GET|POST /signup`, `/signup/access`, `/signup/account`. REST: `POST /api/customers` (public, `201` + `Location: /api/accounts/{id}`) and `POST /api/accounts/{id}/account-number` (administrator, `200` with the account summary).
- **Incompatible REST change:** `POST /api/accounts` loses `accountNumber` and requires `holderDocument` and `holderEmail`.
- **New ADR 0011** (customer self sign-up) extending ADR 0008.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | new `IdentityDocument`, `EmailAddress`, `DuplicateCustomerException`; `AccountHolder`, `AccountNumber`, `CheckingAccount` | Holder data, `AccountNumber.monABank(long)`, `reassignAccountNumber` |
| `persistence` | new `InMemoryAccountNumberSequence`; `InMemoryCheckingAccountRepository` | Sequence; query by document; index after an IBAN change |
| `application` | new `SignUpCommandDef`; `CheckingAccountService`, `OpenAccountCommandDef`, `AccountDetailsDef`, `DemoDataLoader` | `signUp`, `regenerateAccountNumber`, generated IBAN, holder data |
| `presentation` | new `CustomerRegistration`; `SecuritySupport` | Runtime users |
| `presentation.rest` | new `CustomerController`, `SignUpRequest`; `AccountController`, `OpenAccountRequest`, `ApiSecurityConfig`, responses | New endpoints, changed request |
| `presentation.web` | new `SignUpController`, `SignUpForm`, `templates/signup/*.html`; `LoginController`, `login.html`, `WebSecurityConfig`, `OpenAccountForm`, `accounts/new.html`, `accounts/detail.html`, `app.css` | Doors, wizard, admin form, detail |
| docs | `contracts/customer-sign-up.md`, `api/openapi.yaml`, `adr/0011-*.md`, `dominio/glosario.md`, `arquitectura/fase-1-capas.md`, `analysis/functional-analysis.md`, `README.md`, `ia/*` | Contract, API, decision, terms |

## Tasks

### [ ] T0 — Contract (~15 min)

- **Side:** both
- **Layer:** application · presentation · docs
- **Files:** `docs/contracts/customer-sign-up.md`; `SignUpCommandDef`; stubs `CheckingAccountService.signUp` / `regenerateAccountNumber` and `CustomerRegistration.register` (throw `UnsupportedOperationException`); fragment of `docs/api/openapi.yaml`
- **Depends on:** —
- **What to do:**
  1. Write the contract: service methods, `SignUpCommand` (fullName, document, email, username, initialDeposit), `CustomerRegistration.register(command, rawPassword)` returning `AccountSummary`, the future shape of `OpenAccountCommand` and `AccountDetails`, shared validation per field, errors (duplicate customer, username taken, insufficient deposit) with what the web shows and the REST status, routes and OpenAPI fragment.
  2. Create only new types and stubs; existing types change in T19. backend-dev and web-dev review and agree.
- **Acceptance criteria:**
  - [ ] Contract agreed and frozen in a commit; `./mvnw verify` green against it.

### Back (parallel with web; each depends only on T0 unless stated)

### [ ] T1 — `IdentityDocument` value object (~10 min)

- **Side:** back · **Layer:** domain · **Files:** `domain/model/IdentityDocument.java` + test · **Depends on:** T0
- **What to do:** tests first (valid DNI and NIE, wrong letter, wrong format, normalisation, equality by value); implement as a record with the control-letter algorithm (R21).
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=IdentityDocumentTests` passes; `check-domain.mjs` without errors.

### [ ] T2 — `EmailAddress` value object (~5 min)

- **Side:** back · **Layer:** domain · **Files:** `domain/model/EmailAddress.java` + test · **Depends on:** T0
- **What to do:** tests first (valid, missing `@` or domain, blank, lower-case normalisation); implement as a record (R22).
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=EmailAddressTests` passes.

### [ ] T3 — The holder has a document and an e-mail (~10 min)

- **Side:** back · **Layer:** domain · **Files:** `AccountHolder.java`, `AccountHolderTests.java` · **Depends on:** T1, T2
- **What to do:** add `register(fullName, document, email)` with both required, and the accessors `document()` and `email()`. Keep `register(fullName)` until T20 so everything compiles.
- **Acceptance criteria:**
  - [ ] Tests for the new factory and its null checks pass; `./mvnw verify` green.

### [ ] T4 — Mon-A Bank account numbers (~10 min)

- **Side:** back · **Layer:** domain · **Files:** `AccountNumber.java`, `AccountNumberTests.java` · **Depends on:** T0
- **What to do:** tests first: `AccountNumber.monABank(1)` starts with the bank prefix, has 24 characters, valid Spanish control digits and a valid mod-97 check; two sequences give two numbers; a negative or 11-digit sequence is rejected. Implement the factory and `isMonABank()`.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=AccountNumberTests` passes.

### [ ] T5 — Account number sequence (~5 min)

- **Side:** back · **Layer:** persistence · **Files:** new `persistence/InMemoryAccountNumberSequence.java` + test · **Depends on:** T0
- **What to do:** `next()` returns 1, 2, 3… (`AtomicLong`); test including concurrent calls without repeats.
- **Acceptance criteria:**
  - [ ] The test passes; `la_persistencia_no_tiene_interfaces` stays green.

### [ ] T6 — Find a holder by document (~10 min)

- **Side:** back · **Layer:** persistence · **Files:** `InMemoryCheckingAccountRepository.java` + test · **Depends on:** T3
- **What to do:** `existsByHolderDocument(IdentityDocument)`; tests: same document with the same and with another e-mail (both found), another document, holder without data.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=InMemoryCheckingAccountRepositoryTests` passes.

### [ ] T7 — Sign-up use case (~10 min)

- **Side:** back · **Layer:** domain · application · **Files:** new `domain/model/DuplicateCustomerException.java`; `CheckingAccountService.java`, `SignUpCommandDef.java` + tests · **Depends on:** T4, T5, T6
- **What to do:** tests first: `signUp` opens an account with a Mon-A Bank IBAN, the holder data and the username as owner; a repeated document throws `DuplicateCustomerException` even with another e-mail (R23); a deposit below 50 € keeps R1; command validation. Implement, skipping sequence values whose IBAN is already used (R16).
- **Acceptance criteria:**
  - [ ] The tests pass; `./mvnw verify` green.

### [ ] T8 — The aggregate can change its account number (~5 min)

- **Side:** back · **Layer:** domain · **Files:** `CheckingAccount.java`, `CheckingAccountTests.java` · **Depends on:** T4
- **What to do:** `reassignAccountNumber(AccountNumber)` accepts only a Mon-A Bank number different from the current one (R24); tests for both rejections.
- **Acceptance criteria:**
  - [ ] The tests pass.

### [ ] T9 — The administrator regenerates an IBAN (~10 min)

- **Side:** back · **Layer:** application · persistence · **Files:** `CheckingAccountService.java`, `InMemoryCheckingAccountRepository.java` + tests · **Depends on:** T5, T8
- **What to do:** `regenerateAccountNumber(caller, id)`: administrators only (`ForbiddenOperationException`), unknown id → `AccountNotFoundException`; the old IBAN stops resolving in `findByAccountNumber` and the new one resolves.
- **Acceptance criteria:**
  - [ ] The four tests pass; `./mvnw verify` green.

### [ ] T10 — Runtime users: `CustomerRegistration` (~10 min)

- **Side:** back · **Layer:** presentation · **Files:** `presentation/CustomerRegistration.java`, `SecuritySupport.java` + tests · **Depends on:** T7
- **What to do:** expose the `InMemoryUserDetailsManager` bean; `register` rejects a taken username before opening anything, calls `signUp`, then creates the user with role `USER` and a BCrypt hash; if `signUp` fails no user exists.
- **Acceptance criteria:**
  - [ ] Tests: happy path can authenticate; taken username; failed sign-up leaves no user. `la_aplicacion_no_depende_de_spring_security` green.

### [ ] T11 — REST: `POST /api/customers` (~10 min)

- **Side:** back · **Layer:** presentation (rest) · **Files:** new `CustomerController.java`, `SignUpRequest.java`; `ApiSecurityConfig.java`, `RestExceptionHandler.java` + tests · **Depends on:** T10
- **What to do:** public endpoint; `201` with `Location` and the account summary; `400` with the map of field errors; `422` for duplicate customer, taken username and insufficient deposit.
- **Acceptance criteria:**
  - [ ] `curl -i -H 'Content-Type: application/json' -d '{"fullName":"Margaret Hamilton","document":"12345678Z","email":"margaret@example.com","username":"margaret","password":"demo-1234","initialDeposit":100}' localhost:8080/api/customers` answers `201`, and `curl -u margaret:demo-1234 localhost:8080/api/accounts` lists one account.

### [ ] T12 — REST: `POST /api/accounts/{id}/account-number` (~5 min)

- **Side:** back · **Layer:** presentation (rest) · **Files:** `AccountController.java`, `ApiSecurityConfig.java` + tests · **Depends on:** T9
- **What to do:** administrator only; `200` with the summary and the new IBAN; `403` for a customer; `404` for an unknown id.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=AccountControllerTests` passes with the three cases.

### Web (parallel with back; each depends only on T0 unless stated)

### [ ] T13 — Login page with two doors (~10 min)

- **Side:** web · **Layer:** presentation (web) · **Files:** `LoginController.java`, `templates/login.html`, `static/css/app.css` + tests · **Depends on:** T0
- **What to do:** `GET /login` shows the two cards (and the "Has cerrado la sesión" notice after logout); `GET /login?form` and `?error` show the current form with a link back. Styles `.doors` / `.door` with the existing tokens.
- **Acceptance criteria:**
  - [ ] Tests: doors without form inputs; form with `?form`; error message with `?error`; both links present.

### [ ] T14 — Sign-up step 1: personal data (~10 min)

- **Side:** web · **Layer:** presentation (web) · **Files:** new `SignUpController.java`, `SignUpForm.java`, `templates/signup/data.html`; `WebSecurityConfig.java` (`/signup/**` public) + tests · **Depends on:** T0
- **What to do:** session-scoped form; group `PersonalData` (name, document format, e-mail); progress bar fragment; a valid POST redirects to `/signup/access`.
- **Acceptance criteria:**
  - [ ] Tests: page is public; field errors stay on step 1; valid data redirects.

### [ ] T15 — Sign-up step 2: access (~10 min)

- **Side:** web · **Layer:** presentation (web) · **Files:** `SignUpController.java`, `SignUpForm.java`, `templates/signup/access.html` + tests · **Depends on:** T14
- **What to do:** group `Access` (username pattern, password of at least 8 characters, repetition equal); "Atrás" keeps step 1 data; entering step 2 without step 1 redirects to `/signup`.
- **Acceptance criteria:**
  - [ ] Tests for the three behaviours pass; the password never appears in the rendered HTML.

### [ ] T16 — Sign-up step 3: account and confirmation (~10 min)

- **Side:** web · **Layer:** presentation (web) · **Files:** `SignUpController.java`, `templates/signup/account.html` + tests · **Depends on:** T15
- **What to do:** initial deposit, summary of the data, acceptance of conditions; on submit call `CustomerRegistration.register` (mocked in the tests); a duplicate customer goes back to step 1 and a taken username to step 2, each with its message; other domain errors stay on step 3.
- **Acceptance criteria:**
  - [ ] Tests for success and the three error routes pass.

### [ ] T17 — Signed in after signing up (~10 min)

- **Side:** web · **Layer:** presentation (web) · **Files:** `SignUpController.java` + tests · **Depends on:** T16
- **What to do:** after a successful registration, authenticate the new user in the session, clear the wizard form (`SessionStatus.setComplete()`) and redirect to `/accounts/{id}` with the flash "Te damos la bienvenida a Mon-A Bank. Tu IBAN es …".
- **Acceptance criteria:**
  - [ ] Test: after the final POST, `GET /accounts/{id}` with the same session answers `200`.

### [ ] T18 — Account detail: new IBAN button (~10 min)

- **Side:** web · **Layer:** presentation (web) · **Files:** `templates/accounts/detail.html`, `AccountWebController.java` (`POST /accounts/{id}/account-number`), `WebSecurityConfig.java` + tests · **Depends on:** T0
- **What to do:** the administrator sees "Generar nuevo IBAN" (with an in-page confirmation step) and the action calls `regenerateAccountNumber`; a customer neither sees it nor can post it (403). The holder's document and e-mail are added in T19, when the view has them.
- **Acceptance criteria:**
  - [ ] Tests: button only for the administrator; POST as customer → 403; success shows the flash with the new IBAN.

### Integration (lead, in order)

### [ ] T19 — Administrator opening: generated IBAN and holder data (~15 min)

- **Side:** both · **Layer:** application · presentation · **Files:** `OpenAccountCommandDef.java`, `AccountDetailsDef.java`, `CheckingAccountService.java`, `DemoDataLoader.java`, `OpenAccountRequest.java`, `AccountController.java`, response DTOs, `OpenAccountForm.java`, `AccountWebController.java`, `accounts/new.html`, `accounts/detail.html` + their tests · **Depends on:** T7, T12, T18
- **What to do:** the command loses `accountNumber` and gains `holderDocument` and `holderEmail`; `openAccount` generates the IBAN and applies R23; the details view and the detail page show document and e-mail; the demo accounts get documents and e-mails; tests that used fixed IBANs read the IBAN from the response.
- **Acceptance criteria:**
  - [ ] `./mvnw verify` green; with the `demo` profile the three accounts have IBANs starting with the Mon-A Bank prefix.

### [ ] T20 — Remove the holder factory without data (~5 min)

- **Side:** both · **Layer:** domain · tests · **Files:** `AccountHolder.java`, `TestAccounts.java`, affected tests · **Depends on:** T19
- **What to do:** delete `register(fullName)`; `TestAccounts` gives every holder a default valid document and e-mail, overridable.
- **Acceptance criteria:**
  - [ ] `./mvnw verify` green.

### [ ] T21 — End-to-end tests (~10 min)

- **Side:** both · **Layer:** tests · **Files:** `DddLabApplicationTests.java` · **Depends on:** T17, T20
- **What to do:** web: doors → three steps → detail with the session open → logout → login with the new credentials. REST: sign up, list the account, transfer to a demo account. A second sign-up with the same document is rejected on both sides, also with a different e-mail.
- **Acceptance criteria:**
  - [ ] The new tests pass with real beans (no mocks).

### [ ] T22 — ArchUnit: credentials stay in presentation (~5 min)

- **Side:** both · **Layer:** tests · **Files:** `LayeredArchitectureTests.java` · **Depends on:** T10
- **What to do:** confirm the existing rules cover `CustomerRegistration` (`toda_clase_pertenece_a_una_capa`, web and rest do not know each other); validate by breaking one (inject the user manager into `CheckingAccountService`) and seeing it fail.
- **Acceptance criteria:**
  - [ ] The rule fails when broken and passes when restored.

### [ ] T23 — OpenAPI (~10 min)

- **Side:** both · **Layer:** docs · **Files:** `docs/api/openapi.yaml` · **Depends on:** T19
- **What to do:** add `/api/customers` (no security) and `/api/accounts/{id}/account-number`; update the open-account request and the details response.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` without errors.

### [ ] T24 — ADR 0011 and architecture (~10 min)

- **Side:** both · **Layer:** docs · **Files:** `docs/adr/0011-customer-self-sign-up.md`, `docs/adr/README.md`, `docs/arquitectura/fase-1-capas.md` · **Depends on:** T21
- **What to do:** record the decision: public sign-up, users created at runtime in `presentation`, accepted risks (no e-mail verification, no rate limit, users lost on restart) and the alternatives discarded.
- **Acceptance criteria:**
  - [ ] ADR listed in the index and linked from ADR 0008.

### [ ] T25 — Glossary and functional analysis (~10 min)

- **Side:** both · **Layer:** docs · **Files:** `docs/dominio/glosario.md`, `docs/analysis/functional-analysis.md` · **Depends on:** T21
- **What to do:** terms (identity document, e-mail address, Mon-A Bank account number, customer sign-up, duplicate customer), updated holder, command and role rows, rules R21-R24 with their tests, demo data table.
- **Acceptance criteria:**
  - [ ] Every rule R21-R24 names an existing test.

### [ ] T26 — README and deployment guide (~5 min)

- **Side:** both · **Layer:** docs · **Files:** `README.md`, `docs/deploy/render.md` · **Depends on:** T21
- **What to do:** mention the sign-up, the new demo IBANs and that users created on the site disappear on restart.
- **Acceptance criteria:**
  - [ ] No document still tells the administrator to type an IBAN.

### [ ] T27 — Project status (~5 min)

- **Side:** both · **Layer:** docs · **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md` · **Depends on:** T23-T26
- **What to do:** status and next step (twin issue on `Hexagonal`); decisions (runtime users in presentation, IBAN sequence, uniqueness); remove the pending item "Sin gestión de usuarios (alta…)" and add the new accepted risks.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] `/login` shows two doors; "Quiero ser cliente" completes in three steps and ends on the account detail with the session open.
- [ ] Every new account, whoever opens it, has a valid Mon-A Bank IBAN; no form or request accepts a typed IBAN for opening.
- [ ] The administrator can replace the IBAN of an account with a generated one, and a customer cannot.
- [ ] A second customer with the same document is rejected on web and REST, whatever the e-mail; a taken username too.
- [ ] `POST /api/customers` works without credentials and the new user can use the API with HTTP Basic.
- [ ] The `v1-monolito` tag is not moved.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (layers: each layer only uses the one below), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones.
- [ ] `docs/api/openapi.yaml` reflects the change and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English (additions to documents still in Spanish follow the language of the document).
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Entity code `9999`:** invented and not checked against the Banco de España registry. Changing it only touches a constant and its tests.
- **Sign-in after sign-up (T17):** depends on how Spring Security 7 stores a programmatic authentication in the session. If it does not hold, fall back to redirecting to `/login?form` with a "ya puedes entrar" notice.
- **Atomicity:** account and user are created in two steps. If creating the user failed after the account was opened, an account without a usable owner would remain; accepted for an in-memory practice project and noted in the ADR.
- **Public endpoint without limits:** anybody can create users and accounts; on Render the data disappears on restart. Accepted risk, recorded in ADR 0011.
- **One account per document:** since the document is unique among holders, a customer cannot open a second account, neither by signing up again nor through the administrator. Confirm that this is intended.
- **Incompatible REST change** in `POST /api/accounts` and many tests with fixed IBANs to rewrite (T19 is the largest task; split it if it exceeds 15 minutes).
- **Password in the session** between steps 2 and 3: never rendered, cleared on completion or session expiry.
