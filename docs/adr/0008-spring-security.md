# 0008 · Authentication and roles with Spring Security

- **Status:** accepted · 2026-10-25 (issue #50)

## Context

Until now anyone could see every account. The application needs a login with two roles: an **admin** who sees every account and opens accounts, and a **user** (customer) who only sees and operates their own accounts. Both channels need it: the Thymeleaf web and the REST API. `CLAUDE.md` only allows dependencies beyond Lombok, Immutables, Thymeleaf, Mockito and ArchUnit when an ADR justifies them.

## Decision

Add `spring-boot-starter-security` (and `spring-security-test` in test scope):

- **Authentication** by Spring Security: form login with a custom page (`/login`) and session on the web (CSRF on); HTTP Basic, stateless and without CSRF on `/api/**`. Two `SecurityFilterChain` beans, one per channel, so the web and the REST sides own their own rules.
- **Users** in memory with BCrypt-hashed passwords: the `demo` profile defines the demo users; otherwise one admin comes from environment variables.
- **Roles on URLs** (Spring): only an admin opens accounts.
- **Ownership is an application rule, not a Spring rule:** `CheckingAccountService` receives a `Caller` (username + admin flag) and only lets a user reach their own accounts; a foreign account is reported as not found (404) so its existence is not revealed. Identity never enters `domain`.
- Security classes live in `presentation` (shared helpers in its root package, one chain per channel package), so `LayeredArchitectureTests` keeps passing.

## Alternatives

- **A hand-made session filter:** no dependency, but it would reimplement password hashing, CSRF, session fixation protection and HTTP Basic — easy to get wrong and not what a real project would do.
- **Authorization with method security (`@PreAuthorize`) on the application service:** it would put Spring annotations on the use cases and make ownership depend on the framework; a plain `Caller` keeps the rule testable without Spring and portable to phase 2.
- **OAuth2/OIDC with an external identity provider:** realistic, but needs extra infrastructure for a local practice project.

## Consequences

- Every `@WebMvcTest` imports its channel's security chain and runs with a mock user (`@WithMockUser`); web form tests send a CSRF token.
- No `thymeleaf-extras-springsecurity`: the web header gets the username from a `@ControllerAdvice`, so no further dependency is needed.
- End-to-end tests log in (cookie session) or send Basic credentials.
- The API now requires credentials (401 without them, 403 without the role), documented in `openapi.yaml` with a `basicAuth` scheme.
- In phase 2 the chains become part of the `adapter.in.web` / `adapter.in.rest` adapters and `Caller` travels through the input ports.
