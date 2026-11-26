# OpenAPI contract changelog

Changes to `docs/api/openapi.yaml`, newest first. Versioning policy: [ADR 0014](../adr/0014-contract-versioning.md) (major for an incompatible change as `oasdiff breaking` reports it, minor for a compatible addition, patch for documentation only). The CI checks the bump on every pull request.

## 2.3.0 · 2026-11-26 (#197)

Compatible addition ([ADR 0024](../adr/0024-oauth2-oidc-auth0.md)).

- **New security scheme `auth0`** (`openIdConnect`, discovery at `/api-docs/openid-configuration`): every protected operation accepts either the API's own token (`bearerAuth`, unchanged) or an Auth0 access token for the API's audience. The token's `sub` must be linked to a monA Bank user on the web; roles come from monA Bank.
- **401:** also for an Auth0 token for another audience or from another issuer, and for an Auth0 identity that is not linked (new example `unlinkedAuth0Identity`, with its own `detail`).
- **`POST /api/auth/token`:** documented as available only while local authentication is on. No change in the default configuration.

## 2.2.4 · 2026-11-25 (#178)

Documentation only.

- **Bank name:** descriptions, summaries and the `DuplicateCustomer` example write the bank's name as «monA Bank» instead of «Mon-A Bank», like the web and the error messages ([ADR 0022](../adr/0022-brand-identity.md)). No field, value format or status changes.

## 2.2.3 · 2026-11-24 (#157)

Documentation only.

- **`TokenRequest.username`:** case-insensitive, and surrounding spaces are ignored: `" ADA "` gets a token for `ada`. Since #150 the users are in PostgreSQL, which compares the username case-sensitively, so `Ada` was answered with 401; now the login normalises it first (#157).

## 2.2.2 · 2026-11-23 (#139)

Documentation only ([ADR 0018](../adr/0018-sign-up-limit.md)).

- **`signUp` and `TooManySignUps`:** the 429 also comes after 10 failed sign-ups in the last hour from one IP because the username was taken or the document already belongs to a customer. Same response, status and headers.

## 2.2.1 · 2026-11-23

Documentation only.

- **Error table:** the summary of status codes in `info.description` gets the 429 row (`Demasiados intentos` when asking for a token, ADR 0017; `Demasiadas altas` when signing up, ADR 0018). The operations already declared it since 2.1.0 and 2.2.0.

## 2.2.0 · 2026-11-23 (#131)

Limit on customer sign-ups ([ADR 0018](../adr/0018-sign-up-limit.md)).

- **New response:** `signUp` answers 429 (`TooManySignUps`, `ProblemDetail` with a `Retry-After` header in seconds) after 3 successful sign-ups from one IP, or 50 from all IPs together, within the last hour. Failed sign-ups do not count. A compatible addition: no request or existing response changes.

## 2.1.0 · 2026-11-20 (#130)

Limit on login attempts ([ADR 0017](../adr/0017-login-attempt-limit.md)).

- **New response:** `issueToken` answers 429 (`TooManyRequests`, `ProblemDetail` with a `Retry-After` header in seconds) after 3 failures for a user from one IP, or 10 from one IP with any user, for 30 minutes and even with the right password. A compatible addition: no request or existing response changes.

## 2.0.0 · 2026-11-18 (#122)

Authentication with JWT Bearer tokens ([ADR 0015](../adr/0015-jwt-bearer-api.md)).

- **Incompatible:** HTTP Basic is no longer accepted. The global security scheme is now `bearerAuth` (`http`, `bearer`, JWT) instead of `basicAuth`. `oasdiff breaking` reports it on the seven protected operations (`api-security-removed`).
- **New operation:** `issueToken` (`POST /api/auth/token`, tag `auth`, public): credentials in a JSON body (`TokenRequest`) in exchange for an `AccessToken` (`accessToken`, `tokenType: Bearer`, `expiresIn` in seconds, 900 by default). Wrong credentials give 401.
- **401 responses:** they now carry `WWW-Authenticate: Bearer` and two examples (no token; invalid or expired token). The 401 of `signUp` only happens with an invalid Bearer token.

## 1.0.0 · 2026-11-17 (#112)

First stable version, after the OpenAPI course review. No change to the API itself; the contract reached this state through the earlier `0.x` versions, all published as `0.1.0`:

- **Operations:** list, open and look up checking accounts; replace an IBAN with a generated one (admin); deposits, withdrawals and transfers; public customer sign-up (`POST /api/customers`).
- **Errors:** `ProblemDetail` (RFC 9457) in Spanish for 400, 401, 403, 404, 415 and 422 (#108, #110).
- **Documentation:** named examples on every success response and on the shared 422, `info.contact`, the Render server and a relative server for Swagger UI (#109, #110, #111).

Incompatible changes made under `0.1.0`, recorded here instead of in the operations' descriptions:

- **#18:** the body of `POST /api/accounts` no longer carries `accountNumber` or `segment` (the bank assigns the IBAN and the segment is computed); it carries `holderTaxId`.
- **#103:** `holderTaxId` accepts a DNI or a NIE and its control letter is checked; `holderName` and `ownerUsername` are only required for a new customer, together with the new `holderEmail`; for an existing customer (same document) they are absent, blank or equal to theirs, and different data fail with 400.
- **#108:** amounts go from 0.005 to 999,999,999.99; `currency` is `EUR` (or `null`); optional strings accept `null`; the public sign-up documents its 401.
