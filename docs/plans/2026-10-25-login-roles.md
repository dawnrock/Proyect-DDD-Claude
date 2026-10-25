# Plan: Login with admin and user roles (Spring Security)

- **Issue:** [#50](https://github.com/dawnrock/Proyect-DDD-Claude/issues/50)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-25
- **Approved:** by the user on 2026-10-25
- **Status:** in progress
- **Total estimate:** ~150 min (16 tasks: T0 and T1 both, 7 back, 5 web, 2 both at the end)

## Goal

Make the application realistic: today anyone sees every account on the home page. Add a **login** with two roles, decided with the user on 2026-10-25:

- **ADMIN** sees every account (today's list), opens accounts and can operate on any of them.
- **USER** (a customer) only sees and operates **the accounts they own** (one or several); with a single account the login lands on its detail page.

**Spring Security** authenticates (form login on the web, **HTTP Basic** on the REST API) and enforces role-only rules on URLs; **ownership** (who may touch which account) is an application rule, checked in `CheckingAccountService` so it holds for both channels and is testable without Spring. A user who asks for an account they do not own gets **404**, as if it did not exist (no information leak); a user on an admin-only page or endpoint gets **403**. It is a team issue (ADR 0007): `/implement-plan-team 50`.

### Out of scope

- Managing users (sign-up, password change, user admin screens): users are configured statically (demo profile and environment variables).
- Persisting users or ownership in a database (in memory, like the accounts).
- Joint accounts (several owners per account), OAuth/OIDC, remember-me, account lock-out.
- Moving identity into the domain: `domain` stays unaware of users and roles.

## Context

- **Where each piece lives (phase 1 layers, `LayeredArchitectureTests`):**

  | Piece | Package | Why |
  | ----- | ------- | --- |
  | `Caller` (username + admin flag) | `application` | Who is asking; plain Immutables view, no Spring types |
  | Ownership rules (list/find/operate only owned accounts unless admin; only admin opens accounts) | `application` (`CheckingAccountService`) | Same rule for web and REST; unit-testable |
  | `InMemoryAccountOwnershipRepository` (account id → owner username) | `persistence` | Stores the relation outside the aggregate |
  | `ForbiddenOperationException` (extends a new `application.AccessDeniedException` base → 403) | `application` | A non-admin opening an account |
  | Users, password encoder, `Authentication` → `Caller` | `presentation` (root package: `presentation.SecuritySupport`, `presentation.CurrentCaller`) | Shared by both channels; the root package is outside the D5 slices, so web and REST may both use it without knowing each other |
  | REST security chain (`/api/**`, Basic, stateless, no CSRF, ProblemDetail 401/403) | `presentation.rest.ApiSecurityConfig` | Owned by back |
  | Web security chain (form login `/login`, logout, CSRF, 403 page) | `presentation.web.WebSecurityConfig` | Owned by web |

  Two `SecurityFilterChain` beans (API one with `@Order(1)` and `securityMatcher("/api/**")`) so back and web never edit the same file.
- **Usernames, not holders:** an account's owner is a **username** given when the admin opens it (`OpenAccountCommand.ownerUsername`). The holder name stays a domain attribute; the username is an identity concept and never enters `domain`.
- **Transfers by IBAN:** listing other customers' accounts in the transfer form would leak them, so the target becomes a typed account number: `TransferCommand.targetAccountNumber` replaces `targetAccountId`, and `TransferRequest` follows (**breaking API change**, documented in OpenAPI). An unknown IBAN is a 404.
- **Migration without breaking the build:** T0 adds the new `Caller`-taking service methods as stubs next to the current ones; back implements them, web and REST switch to them, and E1 removes the old ones.
- **Users:**
  - profile `demo`: `admin`, `ada`, `grace`, `alan` (owners of the three demo accounts) in `application-demo.properties`, passwords as **BCrypt hashes**; the plain demo password is documented in the README (demo fixture for a local practice app, not a secret — see Risks);
  - without `demo`: one admin from `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` (environment), none if unset;
  - `DemoDataLoader` opens the accounts as a system admin `Caller`, with owners `ada`, `grace`, `alan`.
- **Tests:** `spring-security-test` (test scope) for `@WithMockUser` and `csrf()`. Every `@WebMvcTest` imports its channel's chain plus `SecuritySupport`; existing tests run as admin, new tests cover user, anonymous and foreign-account cases. End-to-end tests log in (cookie session) or send Basic credentials.
- **New dependencies** (`spring-boot-starter-security`, `spring-security-test`): justified in **ADR 0008**, as `CLAUDE.md` requires.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build, `docs/adr` | `pom.xml`, `docs/adr/0008-spring-security.md` | Dependencies and ADR |
| `application` | `CallerDef.java`, `CheckingAccountService.java`, `OpenAccountCommandDef.java`, `TransferCommandDef.java`, `AccessDeniedException.java`, `ForbiddenOperationException.java`, `DemoDataLoader.java` | Caller, ownership rules, commands |
| `persistence` | `InMemoryAccountOwnershipRepository.java` | Owner per account |
| `presentation` | `SecuritySupport.java`, `CurrentCaller.java` | Users, encoder, caller |
| `presentation.rest` | `ApiSecurityConfig.java`, `AccountController.java`, `TransferController.java`, `TransferRequest.java`, `OpenAccountRequest.java`, `RestExceptionHandler.java` | API security |
| `presentation.web` | `WebSecurityConfig.java`, `AccountWebController.java`, `LoginController.java`, `TransferForm.java`, `OpenAccountForm.java`, templates (`login.html`, `error/403.html`, layout, list, detail, new) | Web security |
| resources | `application.properties`, `application-demo.properties` | Users |
| `docs` | contract, `openapi.yaml`, glossary, `fase-1-capas.md`, README, project status | Documentation |

## Tasks

### [x] T0 — Contract (~15 min)

- **Side:** both
- **Layer:** application · docs
- **Files:** `docs/contracts/login-roles.md`, contract types in `application`, fragment of `docs/api/openapi.yaml`, `docs/adr/0008-spring-security.md`
- **Depends on:** —
- **What to do:**
  1. Contract (reviewed by `backend-dev` and `web-dev`):
     - **Service:** `listAccounts(Caller)`, `findAccount(Caller, UUID)`, `openAccount(Caller, OpenAccountCommand)`, `deposit(Caller, DepositCommand)`, `withdraw(Caller, WithdrawCommand)`, `transfer(Caller, TransferCommand)`. A foreign account (list excludes it; find/operate) → `AccountNotFoundException` (404); `openAccount` by a non-admin → `ForbiddenOperationException` (403).
     - **Types:** `CallerDef` (`username`, `admin`); `OpenAccountCommand.ownerUsername` (`[a-z0-9._-]{3,30}`); `TransferCommand.targetAccountNumber` (IBAN, normalized like `AccountNumber`) instead of `targetAccountId`.
     - **Errors:** 401 (no credentials; web → redirect to `/login`), 403 (role), 404 (foreign or unknown account), plus the existing 400/422.
     - **REST:** `basicAuth` security scheme on every operation; `POST /api/accounts` admin-only with `ownerUsername`; `TransferRequest.targetAccountNumber`; `Unauthorized` and `Forbidden` responses (`ProblemDetail`).
     - **Screens:** `GET/POST /login` (Spring), `POST /logout`; `/` = all accounts for admin, "Mis cuentas" for a user (redirect to the detail when they own exactly one); `/accounts/new` admin-only with an owner username field; transfer form with a typed IBAN; header with the username and "Cerrar sesión"; 403 page.
  2. Types without logic: `CallerDef`, the six `Caller` methods with `UnsupportedOperationException("pendiente: backend-dev")`, `ForbiddenOperationException`; command fields added (old methods keep working).
  3. ADR 0008 (Spring Security: why, alternatives — hand-made session filter, no auth —, consequences for tests and layers).
- **Acceptance criteria:**
  - [x] Contract agreed and frozen in a `docs(contract): ...` commit; `./mvnw verify` green; Redocly lint without errors.

### [x] T1 — Security foundation (~10 min)

- **Side:** both (lead, before splitting the work)
- **Layer:** build · presentation
- **Files:** `pom.xml`, `presentation/SecuritySupport.java`, `presentation/CurrentCaller.java`, `presentation/rest/ApiSecurityConfig.java`, `presentation/web/WebSecurityConfig.java`, `application.properties`, `application-demo.properties`, the four `@WebMvcTest` classes
- **Depends on:** T0
- **What to do:**
  1. Add `spring-boot-starter-security` and `spring-security-test` (test).
  2. `SecuritySupport`: `PasswordEncoder` (BCrypt) and an in-memory `UserDetailsService` from `app.security.users[*]` (username, BCrypt hash, role) plus the optional env admin. `CurrentCaller.from(Authentication)` → `Caller`.
  3. Both chains created **permitting everything** (and CSRF off for `/api/**`), so behaviour does not change yet; demo users in `application-demo.properties`.
  4. `@WebMvcTest` classes import their chain + `SecuritySupport`, run with `@WithMockUser(roles = "ADMIN")`, and web form POSTs add `.with(csrf())`.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green with the dependency in place; `CurrentCaller` unit test (admin and user).

### [ ] B1 — Ownership repository (~5 min)

- **Side:** back
- **Layer:** persistence
- **Files:** `persistence/InMemoryAccountOwnershipRepository.java` (+ test)
- **Depends on:** T0
- **What to do:**
  1. `assign(UUID accountId, String username)`, `ownerOf(UUID)`, `accountsOf(String)`; one owner per account.
- **Acceptance criteria:**
  - [ ] Tests green.

### [ ] B2 — Opening accounts: admin only, with owner (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `CheckingAccountService.java`, `OpenAccountCommandDef.java`, `CheckingAccountServiceTests.java`
- **Depends on:** B1
- **What to do:**
  1. `openAccount(Caller, command)`: non-admin → `ForbiddenOperationException`; otherwise open as today and record `ownerUsername`.
  2. Command validation of `ownerUsername`.
- **Acceptance criteria:**
  - [ ] Tests green: admin opens with owner; user is rejected; invalid username rejected by the command.

### [ ] B3 — Listing and finding only owned accounts (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `CheckingAccountService.java`, `CheckingAccountServiceTests.java`
- **Depends on:** B2
- **What to do:**
  1. `listAccounts(Caller)`: admin → all; user → owned. `findAccount(Caller, id)`: foreign → `AccountNotFoundException`.
- **Acceptance criteria:**
  - [ ] Tests green, including "a foreign account is not found" (validated by temporarily skipping the check).

### [ ] B4 — Operating only owned accounts; transfer target by IBAN (~10 min)

- **Side:** back
- **Layer:** application
- **Files:** `CheckingAccountService.java`, `TransferCommandDef.java`, `CheckingAccountServiceTests.java`, `MovementCommandsTests.java`
- **Depends on:** B3
- **What to do:**
  1. `deposit` / `withdraw` / `transfer` with `Caller`: the (source) account must be owned unless admin → otherwise `AccountNotFoundException`.
  2. Transfer target resolved by `targetAccountNumber` (any existing account; unknown → `AccountNotFoundException`).
- **Acceptance criteria:**
  - [ ] Tests green: own account OK, foreign 404, transfer to another customer's IBAN OK, unknown IBAN 404.

### [ ] B5 — Demo data with owners (~5 min)

- **Side:** back
- **Layer:** application
- **Files:** `DemoDataLoader.java`, `DemoDataLoaderTests.java`
- **Depends on:** B2
- **What to do:**
  1. Open the demo accounts as a system admin `Caller`, owners `ada`, `grace`, `alan`.
- **Acceptance criteria:**
  - [ ] Test green: each demo account has its owner.

### [ ] B6 — API security rules and 401/403 (~10 min)

- **Side:** back
- **Layer:** presentation
- **Files:** `presentation/rest/ApiSecurityConfig.java`, `RestExceptionHandler.java`, `AccountController.java`, `TransferController.java`, `OpenAccountRequest.java`, REST tests
- **Depends on:** T1, B4
- **What to do:**
  1. Chain: Basic, stateless, every `/api/**` authenticated, `POST /api/accounts` → `ADMIN`; entry point and access-denied handler answer `ProblemDetail` 401/403; `ForbiddenOperationException` → 403 in `RestExceptionHandler`.
  2. Controllers pass `CurrentCaller.from(authentication)` to the new service methods; `OpenAccountRequest.ownerUsername`.
  3. Tests: anonymous 401; user on `POST /api/accounts` 403; user on a foreign account 404 (mocked service throws); admin as before.
- **Acceptance criteria:**
  - [ ] Tests green; responses match the OpenAPI fragment.

### [ ] B7 — API transfer by IBAN (~5 min)

- **Side:** back
- **Layer:** presentation
- **Files:** `TransferRequest.java`, `TransferController.java`, `TransferControllerTests.java`
- **Depends on:** B6
- **What to do:**
  1. `targetAccountNumber` (pattern like `AccountNumber`) replaces `targetAccountId`; tests updated (400 for a malformed IBAN).
- **Acceptance criteria:**
  - [ ] Tests green.

### [ ] W1 — Login page and web security rules (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `presentation/web/WebSecurityConfig.java`, `LoginController.java`, `templates/login.html`, `templates/error/403.html`, `AccountWebControllerTests.java`
- **Depends on:** T1
- **What to do:**
  1. Chain: form login at `/login` (custom page, dark style), logout `POST /logout` → `/login?logout`, CSRF on, `/css/**` and `/login` public, `/accounts/new` → `ADMIN`, everything else authenticated; access denied → 403 page.
  2. Tests: anonymous `GET /` → redirect to `/login`; login page renders with CSRF token and error message on `?error`; user on `/accounts/new` → 403.
- **Acceptance criteria:**
  - [ ] Tests green.

### [ ] W2 — Header with user and logout; home per role (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `templates/fragments/layout.html`, `templates/accounts/list.html`, `AccountWebController.java`, `AccountWebControllerTests.java`
- **Depends on:** W1
- **What to do:**
  1. Header: username and a "Cerrar sesión" form (POST with CSRF).
  2. `/` uses `listAccounts(caller)`: admin title "Cuentas" with "Abrir cuenta"; user title "Mis cuentas", no "Abrir cuenta"; a user with exactly one account is redirected to its detail.
- **Acceptance criteria:**
  - [ ] Tests green for admin, user with several accounts and user with one account.

### [ ] W3 — Detail and operations with the caller (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `AccountWebController.java`, `AccountWebControllerTests.java`
- **Depends on:** W1
- **What to do:**
  1. Detail, deposit, withdrawal and transfer pass the caller; a foreign account (service throws `AccountNotFoundException`) shows the 404 page.
  2. Back link: "← Cuentas" for admin, "← Mis cuentas" for a user (hidden if they own a single account).
- **Acceptance criteria:**
  - [ ] Tests green, including a user on a foreign account → 404.

### [ ] W4 — Transfer form with a typed IBAN (~10 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `TransferForm.java`, `templates/accounts/detail.html`, `AccountWebController.java`, `AccountWebControllerTests.java`
- **Depends on:** W3
- **What to do:**
  1. Replace the target select with an IBAN text field (same validation message style; spaces allowed); the page no longer lists other accounts.
  2. Tests: approved transfer to a typed IBAN; malformed IBAN field error; unknown IBAN → message on the form (not a 404 page).
- **Acceptance criteria:**
  - [ ] Tests green; the detail page contains no other customer's account numbers.

### [ ] W5 — Open account with owner (admin) (~5 min)

- **Side:** web
- **Layer:** presentation
- **Files:** `OpenAccountForm.java`, `templates/accounts/new.html`, `AccountWebController.java`, `AccountWebControllerTests.java`
- **Depends on:** W1
- **What to do:**
  1. "Usuario del titular" field (same rule as the command); `openAccount(caller, command)`.
- **Acceptance criteria:**
  - [ ] Tests green (valid owner; invalid username field error).

### [ ] E1 — Integration: remove old methods, end-to-end tests (~15 min)

- **Side:** both
- **Layer:** application · tests
- **Files:** `CheckingAccountService.java`, `DddLabApplicationTests.java`, `DemoProfileTests.java`
- **Depends on:** B1-B7, W1-W5 (after merging `-back` and `-web`)
- **What to do:**
  1. Remove the old `Caller`-less service methods; `./mvnw verify` (including `LayeredArchitectureTests`).
  2. End-to-end with the real service: anonymous `/` → `/login`; login as admin (form + cookie, CSRF token read from the page) sees all accounts; user `ada` lands on her account and gets 404 on grace's; API with Basic: admin 200, user 403 on `POST /api/accounts`, 401 without credentials; transfer by IBAN between customers.
  3. Manual check in the browser (admin and user, desktop and mobile) and with `curl -u`.
- **Acceptance criteria:**
  - [ ] Tests green; browser and `curl` checks done.

### [ ] E2 — Documentation (~10 min)

- **Side:** both
- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-1-capas.md`, `README.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** E1
- **What to do:**
  1. OpenAPI checked against the implementation and linted.
  2. Glossary ("Aplicación y presentación"): usuario, rol, propietario de la cuenta, `Caller`, `ForbiddenOperationException`.
  3. `fase-1-capas.md`: where security lives and why ownership is an application rule.
  4. README: how to log in with the demo users and how to set the admin without `demo`.
  5. Project status: #50 done, next step `/plan-feature 13`, history row.
- **Acceptance criteria:**
  - [ ] Lint without errors; documents updated.

## Global acceptance criteria

**Specific:**

- [ ] Contract agreed and frozen in `docs/contracts/login-roles.md`; ADR 0008 written.
- [ ] Without a session every page redirects to the login and the API answers 401.
- [ ] Admin sees every account and opens accounts; a user cannot (403).
- [ ] A user only sees and operates their accounts; a foreign account is a 404 on the web and in the API.
- [ ] User transfers go to a typed IBAN; no page lists other customers' accounts to a user.
- [ ] Ownership rules are tested in `application` without Spring.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}` (health stays public).
- [ ] The layered architecture is respected (`LayeredArchitectureTests`: security in `presentation`, ownership in `application`/`persistence`, nothing in `domain`), and reviewed by `revisor-ddd`.
- [ ] The domain does not depend on Spring, `jakarta` or `org.immutables`, and does not use `@Data`, `@Setter`, `@Builder` or `@AllArgsConstructor` (`check-domain.mjs` without errors) — the domain does not change.
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] Dependencies added only as justified in ADR 0008 (`spring-boot-starter-security`, `spring-security-test`).
- [ ] `docs/api/openapi.yaml` reflects the changes (security scheme, 401/403, `ownerUsername`, `targetAccountNumber`) and the Redocly lint has no errors.
- [ ] Any new term is in `docs/dominio/glosario.md`.
- [ ] Documentation written or changed by this plan is in English (additions to documents still in Spanish excepted).
- [ ] `docs/ia/estado-del-proyecto.md` updated: issue status, next step, new pending items and history row.

## Risks and open questions

- **Demo credentials in the repository:** `CLAUDE.md` keeps secrets out of the repo. The plan stores only **BCrypt hashes** of a demo password in `application-demo.properties` (profile `demo`, local practice data) and documents the plain demo password in the README; a real admin comes from environment variables. If you prefer no credentials in the repo at all, the demo users could read their password from an environment variable too (one more step to try the app).
- **404 vs 403 for foreign accounts:** 404 hides whether the account exists (chosen). The issue allowed either.
- **Breaking API change:** `TransferRequest.targetAccountId` → `targetAccountNumber`. Acceptable for a practice project with no external clients; documented in OpenAPI.
- **Admin also operates any account:** the plan lets the admin deposit/withdraw/transfer on any account (back-office role). If the admin should be read-only on movements, that is a one-line rule change in B4.
- **Owner usernames are not checked against existing users:** the admin can assign an account to a username that has no login yet (users are static); the account becomes visible once that user exists.
- **Size:** 16 tasks; the team split (back 7, web 5) keeps each side around an hour.
