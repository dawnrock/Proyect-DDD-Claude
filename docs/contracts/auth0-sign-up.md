# Contract: Customer sign-up with an Auth0 identity

- **Issue:** [#198](https://github.com/dawnrock/Proyect-DDD-Claude/issues/198) · **Plan:** `docs/plans/2026-11-26-auth0-sign-up.md`
- **Status:** frozen at `d69ca6f`
- **Agreed by:** backend-dev, web-dev (rounds: 1; both accepted the signature and proposed precisions, merged by the lead, since the teammates cannot message each other)

Builds on `customer-sign-up.md` (the three-step wizard, rules R28-R32, ADR 0012), `auth0-web-login.md` (`PendingIdentity`, `/login/link`, the silent return to Auth0) and the sign-up limit (ADR 0018). ADR 0024 records the decisions.

## No application change

`application` does not change: the use case `AccountOpeningCommands.signUp(SignUpCommand)` already receives no password. Credentials and identities stay in the root of `adapter.in` (ADR 0008).

```java
@Component
public class CustomerRegistration {
    // Unchanged (#103): classic sign-up with a password
    AccountSummary register(SignUpCommand command, String rawPassword, String clientIp);

    // New (#198): sign-up with an Auth0 identity, no local password
    AccountSummary registerWithExternalIdentity(SignUpCommand command, PendingIdentity pending, String clientIp);
}
```

`registerWithExternalIdentity`, in this order:

1. `pending` expired → `PendingIdentityExpiredException`; nothing reserved or created.
2. The `sub` already linked to a user → `IdentityAlreadyLinkedException`; nothing created.

Steps 1 and 2 reserve nothing and do not count for the sign-up limit nor for the probing counter (they reveal nothing about a document or a username).
3. The same steps as `register`: reserve a sign-up slot (ADR 0018), check the username (users and `CheckingAccountQueries.isUsernameTaken`), `AccountOpeningCommands.signUp(command)`; any failure gives the slot back and, for a taken username or document, counts for the probing limit (#139).
4. Create the user with role `USER` and an **unusable password** (BCrypt of 32 random bytes that are never kept): no form login, no `POST /api/auth/token`, with any password.
5. `ExternalIdentities.link(pending.provider(), pending.subject(), command.username())`. If another tab linked the same `sub` between step 2 and here, `link` throws `IdentityAlreadyLinkedException` **after** the customer and the user exist: nothing is undone (same criterion as a failed user creation, #150), the slot is not given back, and the web's silent return signs in with the user already linked. Accepted risk, noted in `pendientes.md`.

The method is `synchronized`, like `register`; the unusable password is encoded with the injected `PasswordEncoder` (no new collaborator for it).

Its constructor (new collaborators such as `ExternalIdentities` and the clock) is backend-dev's choice; the web mocks the class with `@MockitoBean`.

## Errors

| Exception | When | Web (what the user sees) | REST |
| --------- | ---- | ------------------------ | ---- |
| `PendingIdentityExpiredException` | the pending identity expired during the wizard | removes it, `redirect:/login?link-expired` | n/a |
| `IdentityAlreadyLinkedException` | the Auth0 identity got linked meanwhile (another tab) | completes the session form, removes the pending identity and `redirect:/oauth2/authorization/auth0`: the link exists now, so the silent return signs the customer in | n/a |
| `TooManySignUpsException` | sign-up limit | as today (step 3 notice) | n/a |
| `UsernameTakenException` | username taken | as today (back to step 2 with the message) | n/a |
| `DuplicateCustomerException` | document of another customer, or different data | as today (back to step 1) | n/a |
| `IllegalArgumentException`, other `DomainException` | validation, initial deposit… | as today | n/a |

## REST (fragment of `docs/api/openapi.yaml`)

n/a: the REST sign-up (`POST /api/customers`) does not change.

## Screens (web)

| Path | Uses | Shows |
| ---- | ---- | ----- |
| `GET /login/link` | session `PendingIdentity` | a choice under the `h1`: an `h2` «¿Ya eres cliente de monA Bank?» with today's link form (still `hx-boost="false"`), and an `h2` «¿Aún no eres cliente?» with a `btn-slant` link «Date de alta» to `/signup?auth0` (normal same-origin navigation) |
| `GET /signup?auth0` (any value of `auth0`) | session `PendingIdentity` (not expired) | replaces any wizard in the session with a **new** form (`model.addAttribute`, not the `@ModelAttribute` factory) in Auth0 mode, set through `SignUpForm.startExternalIdentity(email)` (`externalIdentity` has no setter, so the browser cannot switch modes), with the e-mail prefilled from `pending.email()` (empty when `null`; editable and only informative); without a valid pending identity → `redirect:/login?link-expired` |
| `GET /signup` (no `auth0`) | session | as today, but a leftover Auth0-mode form whose pending identity is missing or expired is discarded and a clean classic step 1 is shown (so the customer is never trapped) |
| steps 2 and 3 in Auth0 mode | session `PendingIdentity` | every GET and POST checks it again: missing or expired → `redirect:/login?link-expired`; each step completed **renews** it (a new `PendingIdentity` with `createdAt` = now: a sliding 10-minute window while the customer is active) |
| step 2 (`/signup/access`) in Auth0 mode | validation group `SignUpForm.ExternalAccess` (username only; the password constraints move to a group `LocalPassword`, validated by hand only in classic mode) | only the username, with «Entrarás con tu cuenta de Auth0: no necesitas contraseña de monA Bank» (visible, referenced from the username's `aria-describedby`); no password inputs are rendered |
| `POST /signup/account` in Auth0 mode | `registerWithExternalIdentity(command, pending, clientIp)` | success → `status.setComplete()`, removes the pending identity and `redirect:/oauth2/authorization/auth0` (no programmatic sign-in, no welcome flash: the Auth0 round trip would lose it); errors as in the table. The step-3 form carries `hx-boost="false"` only in Auth0 mode |

The classic wizard (`GET /signup` without `auth0`) is unchanged, including its programmatic sign-in and welcome message. `/signup?auth0` is followed by htmx-boosted navigation like the other steps; only the final redirect leaves the origin, and it comes from a normal form POST of step 3, which must carry `hx-boost="false"` in Auth0 mode (the redirect ends on Auth0's domain).

## Who owns which file

| File | Owner |
| ---- | ----- |
| `CustomerRegistration` (and `CustomerRegistrationTests`) | **back** (T1, T2) |
| `login-link.html` (and `IdentityLinkControllerTests`) | **web** (T3) |
| `SignUpController`, `SignUpForm`, `signup/data.html`, `signup/access.html`, `signup/account.html`, `SignUpAuth0Tests` | **web** (T4, T5) |
| `Auth0SignUpTests`, docs and status | lead (T6, T7) |

## Changes

- 2026-11-26, review round 1 (lead merged both reviews; no disagreement): steps 1-2 of the registration do not touch the limits; the race after `signUp` is accepted and documented (backend-dev); a new form for `?auth0`, a leftover Auth0 form discarded by `GET /signup`, no setter for `externalIdentity`, the `LocalPassword` group, `hx-boost="false"` on step 3 in Auth0 mode only (web-dev); the pending identity renewed on each completed step and the e-mail editable and informative (lead, answering web-dev). The plan's T5 follows this contract for `IdentityAlreadyLinkedException`. The web may extract the pending-identity lookup of `IdentityLinkController` into a small web component (its own refactor).
