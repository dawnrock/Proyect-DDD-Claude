# Architecture decision records (ADR)

| No. | Decision | Status |
| --- | -------- | ------ |
| [0001](0001-arquitectura-ia-claude-code.md) | AI architecture with Claude Code and Telegram notifications | Accepted |
| [0002](0002-ramas-por-fase.md) | One branch per snapshot: `master`, `Monolithic`, `Hexagonal` | Accepted |
| [0003](0003-lombok-immutables.md) | Where Lombok and Immutables are used | Accepted (the domain part, superseded by 0009) |
| [0004](0004-arquitectura-en-capas.md) | Layered architecture for phase 1 | Accepted |
| [0005](0005-thymeleaf.md) | Web interface with Thymeleaf | Accepted |
| [0006](0006-hexagonal-architecture.md) | Hexagonal architecture for phase 2 | Accepted |
| [0007](0007-equipo-back-web-contrato-primero.md) | Back/web team with the contract first | Accepted |
| [0008](0008-spring-security.md) | Authentication and roles with Spring Security | Accepted (the API part, amended by 0015; login attempts, by 0017) |
| [0009](0009-domain-without-lombok.md) | No Lombok in the domain | Accepted |
| [0010](0010-graphify-token-savings.md) | Graphify for dependency questions only, and a short project status | Accepted |
| [0012](0012-customer-self-sign-up.md) | Customer self sign-up at Mon-A Bank (extends 0008) | Accepted |
| [0013](0013-contract-tests-schemathesis.md) | Contract tests with Schemathesis, at hexagon level | Accepted |
| [0014](0014-contract-versioning.md) | Semantic versioning of the OpenAPI contract | Accepted |
| [0015](0015-jwt-bearer-api.md) | JWT Bearer tokens for the REST API (amends 0008) | Accepted |
| [0016](0016-web-frontend-libraries.md) | Frontend libraries for the Thymeleaf web (complements 0005) | Accepted |
| [0017](0017-login-attempt-limit.md) | Limit on login attempts (amends 0008 and 0015) | Accepted |

ADR 0011 (the `integration` layer) only exists on `Monolithic`: it applies to phase 1.

Format: context, decision, alternatives and consequences.
