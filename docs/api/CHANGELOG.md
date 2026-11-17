# OpenAPI contract changelog

Changes to `docs/api/openapi.yaml`, newest first. Versioning policy: [ADR 0014](../adr/0014-contract-versioning.md) (major for an incompatible change as `oasdiff breaking` reports it, minor for a compatible addition, patch for documentation only). The CI checks the bump on every pull request.

## 1.0.0 · 2026-11-17 (#112)

First stable version, after the OpenAPI course review. No change to the API itself; the contract reached this state through the earlier `0.x` versions, all published as `0.1.0`:

- **Operations:** list, open and look up checking accounts; replace an IBAN with a generated one (admin); deposits, withdrawals and transfers; public customer sign-up (`POST /api/customers`).
- **Errors:** `ProblemDetail` (RFC 9457) in Spanish for 400, 401, 403, 404, 415 and 422 (#108, #110).
- **Documentation:** named examples on every success response and on the shared 422, `info.contact`, the Render server and a relative server for Swagger UI (#109, #110, #111).

Incompatible changes made under `0.1.0`, recorded here instead of in the operations' descriptions:

- **#18:** the body of `POST /api/accounts` no longer carries `accountNumber` or `segment` (the bank assigns the IBAN and the segment is computed); it carries `holderTaxId`.
- **#103:** `holderTaxId` accepts a DNI or a NIE and its control letter is checked; `holderName` and `ownerUsername` are only required for a new customer, together with the new `holderEmail`; for an existing customer (same document) they are absent, blank or equal to theirs, and different data fail with 400.
- **#108:** amounts go from 0.005 to 999,999,999.99; `currency` is `EUR` (or `null`); optional strings accept `null`; the public sign-up documents its 401.
