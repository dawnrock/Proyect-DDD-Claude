# Architecture decision records (ADR)

| No. | Decision | Status |
| --- | -------- | ------ |
| [0001](0001-arquitectura-ia-claude-code.md) | AI architecture with Claude Code and Telegram notifications | Accepted |
| [0002](0002-ramas-por-fase.md) | One branch per snapshot: `master`, `Monolithic`, `Hexagonal` | Accepted |
| [0003](0003-lombok-immutables.md) | Where Lombok and Immutables are used | Accepted (the domain part, superseded by 0009; the Immutables part, by 0028) |
| [0004](0004-arquitectura-en-capas.md) | Layered architecture for phase 1 | Accepted |
| [0005](0005-thymeleaf.md) | Web interface with Thymeleaf | Accepted |
| [0006](0006-hexagonal-architecture.md) | Hexagonal architecture for phase 2 | Accepted |
| [0007](0007-equipo-back-web-contrato-primero.md) | Back/web team with the contract first | Accepted |
| [0008](0008-spring-security.md) | Authentication and roles with Spring Security | Accepted (the API part, amended by 0015; login attempts, by 0017; the web login with Auth0, by 0024) |
| [0009](0009-domain-without-lombok.md) | No Lombok in the domain | Accepted |
| [0010](0010-graphify-token-savings.md) | Graphify for dependency questions only, and a short project status | Graphify part superseded by 0027 |
| [0012](0012-customer-self-sign-up.md) | Customer self sign-up at Mon-A Bank (extends 0008) | Accepted |
| [0013](0013-contract-tests-schemathesis.md) | Contract tests with Schemathesis, at hexagon level | Accepted |
| [0014](0014-contract-versioning.md) | Semantic versioning of the OpenAPI contract | Accepted |
| [0015](0015-jwt-bearer-api.md) | JWT Bearer tokens for the REST API (amends 0008) | Accepted (amended by 0024) |
| [0016](0016-web-frontend-libraries.md) | Frontend libraries for the Thymeleaf web (complements 0005) | Accepted |
| [0017](0017-login-attempt-limit.md) | Limit on login attempts (amends 0008 and 0015) | Accepted |
| [0018](0018-sign-up-limit.md) | Limit on customer sign-ups per IP and in total (amends 0012) | Accepted |
| [0019](0019-real-persistence.md) | Real persistence: PostgreSQL (Neon) in production, H2 locally | Accepted |
| [0020](0020-static-analysis.md) | Static analysis in the build: Checkstyle, PMD/CPD and SpotBugs | Accepted |
| [0021](0021-domain-event-outbox.md) | Domain event outbox with a scheduled relay | Accepted |
| [0022](0022-brand-identity.md) | monA Bank brand identity: logo, intro and typeface | Accepted |
| [0023](0023-brand-ui.md) | The brand's UI language: the A's slant, logo glyphs and Sora (complements 0016 and 0022) | Accepted |
| [0024](0024-oauth2-oidc-auth0.md) | Login with Auth0 (OAuth2/OIDC) and identity linking: the web (#196), the API (#197) and the sign-up (#198) (amends 0008 and 0015) | Accepted |
| [0025](0025-security-headers.md) | Content-Security-Policy and security headers, without Alpine.js (amends 0016) | Accepted |
| [0026](0026-domain-rules-in-archunit.md) | Domain rules only in ArchUnit, and default Lombok accessors (amends 0003 and 0009) | Accepted |
| [0027](0027-remove-graphify.md) | Remove Graphify: Grep + LSP for code searches (supersedes the Graphify part of 0010) | Accepted |
| [0028](0028-records-for-data-carriers.md) | `record` as the only data carrier; Immutables removed (supersedes the Immutables part of 0003) | Accepted |

ADR 0011 (the `integration` layer) only exists on `Monolithic`: it applies to phase 1.

Format: context, decision, alternatives and consequences.
