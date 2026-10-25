# Contract: login with admin and user roles

- **Issue:** [#50](https://github.com/dawnrock/Proyect-DDD-Claude/issues/50) · **Plan:** `docs/plans/2026-10-25-login-roles.md` · **ADR:** `docs/adr/0008-spring-security.md`
- **Status:** frozen at `cc7bc4d`
- **Agreed by:** backend-dev, web-dev (rounds: 1; both asked for minor changes, merged by the lead; the teammates cannot message each other)

Builds on the frozen contracts of #5, #6 and #7 (`open-query-list-accounts.md`, `deposits-withdrawals.md`, `money-transfers.md`).

## Roles and the caller

| Role | Spring authority | Sees | Can |
| ---- | ---------------- | ---- | --- |
| Admin | `ROLE_ADMIN` | every account | open accounts; deposit, withdraw and transfer on any account |
| User (customer) | `ROLE_USER` | only the accounts they own (one or several) | deposit, withdraw and transfer from their own accounts, to any existing IBAN |

The presentation turns the Spring `Authentication` into an application `Caller` (`username`, `admin = has ROLE_ADMIN`) with `presentation.CurrentCaller.from(authentication)` and passes it to every use case. Spring checks **roles on URLs**; the application checks **ownership**. A foreign account is reported as **not found** (`AccountNotFoundException`, 404), never as forbidden, so its existence is not revealed.

## Application service

New methods on `CheckingAccountService` (T0 stubs; they replace the current `Caller`-less ones, which are removed in E1):

```java
AccountSummary       openAccount(Caller caller, OpenAccountCommand command);  // admin only; records command.ownerUsername() as owner
List<AccountSummary> listAccounts(Caller caller);                             // admin: all; user: own; sorted by account number
AccountDetails       findAccount(Caller caller, UUID id);                     // foreign or unknown → AccountNotFoundException
TransactionSummary   deposit(Caller caller, DepositCommand command);          // own account (or admin), else AccountNotFoundException
TransactionSummary   withdraw(Caller caller, WithdrawCommand command);        // idem
TransferSummary      transfer(Caller caller, TransferCommand command);        // source own (or admin); target = any existing account by number
```

- An account with **no registered owner** (opened by the old path or by tests using the repository directly) is visible to admins only.
- `openAccount(Caller, …)` requires `ownerUsername`: empty → `IllegalArgumentException` with a Spanish message, thrown by the **service** (the web catches it around the service call too).
- `transfer(Caller, …)` requires `targetAccountNumber` (`IllegalArgumentException` if only `targetAccountId` comes), normalizes it with `new AccountNumber(...)` and looks it up; none → `TargetAccountNotFoundException`.
- **Transition (until E1):** the old `openAccount(command)` records the owner when `ownerUsername` is not empty; the old `transfer(command)` requires `targetAccountId` (`IllegalArgumentException` if missing).

Order of checks in every operation: load the account → ownership (unless admin) → the existing rules (#6, #7). `openAccount`: role → `ownerUsername` present → the existing rules (#5).

Ownership is stored outside the aggregate, in `persistence.InMemoryAccountOwnershipRepository` (account id → owner username, one owner per account). `domain` does not change.

## Types

| Type (`*Def`) | Field | Type | Rules |
| ------------- | ----- | ---- | ----- |
| `CallerDef` (new) | `username` | `String` | |
| | `admin` | `boolean` | `CallerDef.system()` (Immutables does not copy static methods to `Caller`): internal admin for processes without a user (demo data) |
| `OpenAccountCommandDef` | `ownerUsername` (new) | `String` | `[a-z0-9._-]{3,30}`; defaults to empty during the transition, **required** by `openAccount(Caller, …)` (`IllegalArgumentException` → 400 / field error) |
| `TransferCommandDef` | `targetAccountNumber` (new) | `@Nullable String` | `ES` + 22 digits after removing spaces and upper-casing; otherwise `IllegalArgumentException` when the command is built (Spanish message). `TransferRequest` and `TransferForm` validate first with `@Pattern("^ *[Ee][Ss]( *\d){22} *$")`, like `OpenAccountRequest` and the OpenAPI |
| | `targetAccountId` | `@Nullable UUID` | **transitional**, removed in E1; exactly one of the two must be present |

Views do not change. New exceptions: `OperationNotAllowedException` (abstract, 403; named so it does not clash with Spring Security's `AccessDeniedException`), `ForbiddenOperationException` (extends it), `TargetAccountNotFoundException` (extends `NotFoundException`, 404).

## Errors

| Exception | When | Web | REST |
| --------- | ---- | --- | ---- |
| — (Spring) | no session / no credentials | redirect to `/login` | 401 `No autenticado` (`ProblemDetail`) |
| — (Spring) | authenticated without the role (user on `/accounts/new`, `POST /api/accounts`) | 403 page (`error/403`) | 403 `Acceso denegado` |
| `ForbiddenOperationException` (application, extends `OperationNotAllowedException`) | non-admin `Caller` opening an account (safety net behind the URL rule) | 403 page (`@ExceptionHandler` in `WebExceptionHandler`) | 403 `Acceso denegado` (`RestExceptionHandler`) |
| `AccountNotFoundException` | unknown account, **or an account of another customer** (detail or the source of an operation) | 404 page | 404 `Recurso inexistente` |
| `TargetAccountNotFoundException` | no account with the typed target IBAN | message on the transfer form (caught before `NotFoundException`) | 404 `Recurso inexistente` |
| `IllegalArgumentException` from the command | malformed target IBAN, invalid `ownerUsername` | field / form error (forms validate first with `@Pattern`) | 400 |
| existing | validation, command rejection, domain rules | as before | 400 / 422 as before |

## REST (fragment of `docs/api/openapi.yaml`)

- Global `security: [basicAuth]` (HTTP Basic); new `Unauthorized` (401) on every operation and `Forbidden` (403) on `POST /api/accounts`.
- `GET /api/accounts`: all for an admin, own for a user. `GET /api/accounts/{id}` and the operations: 404 for a foreign account.
- `POST /api/accounts` (admin only): `OpenAccountRequest.ownerUsername` (required).
- `POST /api/transfers`: `TransferRequest.targetAccountNumber` (IBAN) **replaces** `targetAccountId` (breaking change); `sourceAccountId` must be the caller's (404 otherwise).
- Stateless, no CSRF on `/api/**`. `GET /actuator/health` stays public.

## Screens (web)

| Path | Who | Shows |
| ---- | --- | ----- |
| `GET /login` | anyone | dark login page (username, password, "Entrar"); `?error` → "Usuario o contraseña incorrectos"; `?logout` → "Has cerrado la sesión" |
| `POST /login`, `POST /logout` | — | handled by Spring; success → `/`; logout → `/login?logout` |
| `GET /` | admin | "Cuentas": every account, total and "+ Abrir cuenta" (as today) |
| `GET /` | user | "Mis cuentas": own accounts and their total, no "Abrir cuenta"; **exactly one account → redirect to its detail** |
| `GET /accounts/{id}` and its forms | admin, owner | as today; foreign → 404 page; back link "← Cuentas" (admin) / "← Mis cuentas" (user with several accounts; none with one) |
| transfer form | admin, owner | target is a typed IBAN (`targetAccountNumber` text field, spaces allowed) instead of a list of accounts; unknown IBAN → message on the form |
| `GET/POST /accounts/new` | admin | as today plus "Usuario del titular" (`ownerUsername`); a user → 403 page |
| every page | logged in | header with the username and a "Cerrar sesión" button (POST with CSRF). The username comes from a web `@ControllerAdvice` `@ModelAttribute` (no `thymeleaf-extras-springsecurity`); the header tolerates anonymous pages (`/error`, 404) |

## Security wiring (who owns which file)

| File | Owner | Content |
| ---- | ----- | ------- |
| `presentation/SecuritySupport.java` | lead (T1) | `PasswordEncoder` (BCrypt), in-memory `UserDetailsService` from `app.security.users[*]` (username, BCrypt hash, role) and the optional admin from `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`. Binds its properties itself (`@EnableConfigurationProperties`), starts with **no users** (no `demo`, no env), and rejects at start-up a username that does not match `OpenAccountCommandDef.USERNAME_PATTERN` (it could never own an account) |
| `presentation/CurrentCaller.java` | lead (T1) | `Caller from(Authentication)` |
| `presentation/rest/ApiSecurityConfig.java` | lead creates it permissive in T1; **back** fills it in B6 | `@Order(1)`, `securityMatcher("/api/**")`, Basic, stateless, no CSRF, `POST /api/accounts` → `ADMIN`, ProblemDetail 401/403 |
| `presentation/web/WebSecurityConfig.java` | lead creates it permissive in T1; **web** fills it in W1 | the **fallback** chain (no `securityMatcher`, ordered after the API one, `anyRequest().authenticated()`): form login `/login`, logout, CSRF, public `/login`, `/css/**`, `/actuator/health`, `/error`; `/accounts/new` → `ADMIN` (literal path, does not clash with `/accounts/{id}`); 403 page |
| `application-demo.properties` | lead (T1) | demo users `admin`, `ada`, `grace`, `alan` (BCrypt hashes; plain demo password in the README) |

Tests: `@WebMvcTest` classes import their chain and `SecuritySupport`; existing tests run as admin (`@WithMockUser(username = "admin", roles = "ADMIN")`), web form POSTs add `.with(csrf())`; new tests cover user, anonymous and foreign-account cases. The mocked service is stubbed with the `Caller` methods (`any(Caller.class)` or an exact caller).

## Changes

<!-- Each renegotiation: date, who asked, what changed and why. -->

- 2026-10-25, review round 1 (before freezing), changes asked by both teammates and accepted by the lead:
  - **Unknown target IBAN** gets its own `TargetAccountNotFoundException` (both teammates): the web shows it on the form, while a foreign source account is the 404 page.
  - **Malformed target IBAN** is rejected by `TransferCommand` (`IllegalArgumentException`), like every other command format check (backend-dev; web-dev had asked to define it).
  - `AccessDeniedException` renamed `OperationNotAllowedException` so it does not clash with Spring Security's (backend-dev); both handlers translate `ForbiddenOperationException` to 403.
  - Transition behaviour of the old methods, accounts without owner (admin only) and `ownerUsername` required by the service with a Spanish message (backend-dev, web-dev).
  - Header username via a web `@ControllerAdvice` without Thymeleaf extras; web chain is the fallback; `SecuritySupport` starts without users and validates usernames (web-dev, backend-dev).
