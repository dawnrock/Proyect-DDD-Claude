# 0012 · Customer self sign-up at Mon-A Bank

- **Status:** accepted · 2026-11-15
- **Issue:** #95 · **Extends:** [ADR 0008](0008-spring-security.md)

## Context

Until #95 only the administrator opened accounts, users came from configuration (ADR 0008) and the administrator typed every IBAN. The user asked for a bank-like digital sign-up: a visitor becomes a customer of **Mon-A Bank** from the login page, with immediate opening, a bank-assigned IBAN and no administrator approval. A customer is identified by their document and can hold several accounts.

Two constraints shape the design:

- `application` must not depend on Spring Security (`la_aplicacion_no_depende_de_spring_security`, ADR 0008), but signing up creates a user with a password.
- Phase 1 uses concrete classes without ports (ADR 0004).

## Decision

- **Public sign-up** on the web (`/login` with two doors, then `/signup` in three steps, CSRF on) and in the API (`POST /api/customers`, no credentials). Immediate opening; the customer ends signed in on the web.
- **Customer aggregate** (`Customer`: document, name, e-mail) in `domain.model`, stored by `InMemoryCustomerRepository`, which also keeps the customer → username relation outside the aggregate. One customer per document (R29), checked atomically.
- **Users created at runtime in `presentation`:** `CustomerRegistration` (shared by web and REST) checks that the username is free, calls `CheckingAccountService.signUp` and then creates the user with role `USER` and a BCrypt hash in the `InMemoryUserDetailsManager`. The four steps run under one lock. `application` never sees credentials.
- **The bank assigns every IBAN** (entity `9999`, branch `0001`, valid check digits) with `InMemoryAccountNumberSequence`, a concrete class in `persistence` with the same algorithm as `Hexagonal`. The administrator can only replace an IBAN with another generated one (R31). The administrator's opening takes the document: an existing customer gets another account; a new one is registered with name, e-mail and username.
- **Document:** DNI or NIE with its control letter checked (`TaxId`, R27).

## Alternatives

- **User management in `application`:** it would need Spring Security types (or a port, which phase 1 does not use) in the use cases. Discarded.
- **Sign-up pending administrator approval:** safer for a real bank, but the user chose immediate opening.
- **Identity document unique per account:** it would prevent a customer from holding several accounts. Discarded by the user in favour of uniqueness per customer.
- **Persisting users in H2:** consistent with a real system, but accounts and customers are still in memory; persisting only users would leave logins without accounts after a restart.

## Consequences

- Phase 1 now has customers, generated IBANs and runtime users; the twin issue brings the same behaviour to `Hexagonal`, where `TaxId` and the generated IBAN already exist.
- `POST /api/accounts` changed incompatibly (no `accountNumber`, conditional customer fields), documented in `openapi.yaml`.
- **Accepted risks** (local practice project):
  - No e-mail verification and no rate limit: anybody can create customers and users.
  - Customers, accounts and users live in memory and disappear on restart (also on Render).
  - Customer, account and user are created in steps: if creating the user failed for a reason other than a taken username, a customer without a usable login would remain.
  - The wizard keeps the password in the server session between steps 2 and 3 (never rendered).
