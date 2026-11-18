# Proyect-DDD-Claude

Personal practice project that combines two courses:

- **Domain-Driven Design with Java 21 and Spring Boot 4.1**, on a checking-accounts domain (`CheckingAccount` aggregate, entities, value objects, ArchUnit).
- **Claude Code for software development**: the work is organised in GitHub issues and planned and implemented with skills, subagents, hooks and GitHub Actions ([AI architecture](docs/ia/arquitectura-ia.md)).

It starts from a copy of [DDD-Lab-Java](https://github.com/dawnrock/DDD-Lab-Java) and evolves it into two snapshots kept apart:

| Branch | Architecture | Backlog |
| ------ | ------------ | ------- |
| `master` | Starting point (domain only), frozen | — |
| `Monolithic` | Layered monolith: Thymeleaf web + REST → application → domain / in-memory persistence. **Closed and frozen at tag `v1-monolito`** | [Phase 1 · Layered monolith](https://github.com/dawnrock/Proyect-DDD-Claude/milestones) |
| `Hexagonal` | Ports and adapters, domain events, factory with ports, simulated adapters. **Phase closed at tag `v2-hexagonal`; still the default and active branch** | [Phase 2 · Hexagonal architecture](https://github.com/dawnrock/Proyect-DDD-Claude/milestones) |

Technologies: Java 21, Spring Boot 4.1, Spring Security, JUnit 5, ArchUnit, Lombok, Immutables, Thymeleaf, H2.

## Run

```bash
./mvnw verify            # JUnit + ArchUnit tests
./mvnw spring-boot:run   # http://localhost:8080 · /actuator/health · /h2-console
```

On Windows, `mvnw.cmd`. Requires Java 21.

`spring-boot:run` starts with the `demo` profile: the command-line adapter registers three demo customers of Mon-A Bank and opens their accounts through the use cases (two retail and one high net worth by verified income; the bank assigns the IBANs `ES80 9999 0001 45 0000000001`, `…0002`, `…0003`), then deposits, makes two withdrawals (one approved, one rejected) and a transfer, with a `[demo]` line in the log for each. To start without data: `./mvnw spring-boot:run -Dspring-boot.run.profiles=default`. Tests and `java -jar` do not load the demo data.

**Customer sign-up (#103, twin of #95 on `Monolithic`).** On `/login`, "Quiero ser cliente" opens a three-step sign-up (personal data, access, account): the visitor becomes a Mon-A Bank customer with a bank-assigned IBAN and stays signed in. The API offers the same with `POST /api/customers` (public). Customers and users created this way live in memory and disappear on restart.

**Login (#50).** You must sign in. With the `demo` profile: `admin` (sees every account, opens accounts for new or existing customers by their document and can replace an IBAN with a generated one) and `ada`, `grace` and `alan` (each one only sees their own account), all with password `demo` (local practice data: `application-demo.properties` only holds its BCrypt hash). The API uses JWT Bearer tokens (#122, ADR 0015): ask for one with `curl -s -X POST http://localhost:8080/api/auth/token -H 'Content-Type: application/json' -d '{"username":"admin","password":"demo"}'` and send the `accessToken` it returns: `curl -H "Authorization: Bearer <accessToken>" http://localhost:8080/api/accounts`. A token lasts 15 minutes; HTTP Basic is not accepted. Tokens are signed with the `APP_JWT_SECRET` environment variable (at least 32 bytes, e.g. `openssl rand -base64 48`); the `demo` profile has a development secret, and without one the application uses a random key that changes on every restart. Without the `demo` profile there are no users; an administrator can be created with the `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` environment variables.

**API documentation (#111).** Swagger UI at `/api-docs` (<http://localhost:8080/api-docs>, and on Render <https://proyect-ddd-claude.onrender.com/api-docs>) shows the hand-written contract, served as-is at `/openapi.yaml`. It is public; to use "Try it out", call **POST /api/auth/token** (for example `admin`/`demo` in local), copy the `accessToken` and paste it in **Authorize** (`bearerAuth`). The page loads Swagger UI from jsDelivr, so the browser needs internet access.

## Deploy

Each branch can be published as its own web service on Render's free plan, built with the [`Dockerfile`](Dockerfile): [deployment guide](docs/deploy/render.md). On a public URL, replace the `demo` password with your own through environment variables (the guide explains how); the image turns the H2 console off.

## Workflow with Claude Code

```text
/plan-feature 7          # issue #7 → plan in docs/plans/ + comment on the issue
/implement-plan 7        # worktree → TDD task by task → commits → PR against the phase branch
/implement-plan-team 7   # back + web issue: contract first, backend-dev and web-dev in parallel, one PR
/commit                  # Conventional Commits in Spanish
```

## Documentation

- [AI architecture](docs/ia/arquitectura-ia.md) · [Telegram notifications](docs/ia/notificaciones.md) · [Project status](docs/ia/estado-del-proyecto.md)
- [Phase 1 · layers](docs/arquitectura/fase-1-capas.md) · [Phase 2 · hexagonal](docs/arquitectura/fase-2-hexagonal.md) · [Layers vs hexagonal](docs/arquitectura/comparativa-capas-hexagonal.md)
- [Glossary](docs/dominio/glosario.md) · [ADR](docs/adr/README.md) · [Plans](docs/plans/) · [Contracts](docs/contracts/) · [OpenAPI](docs/api/openapi.yaml) · [Deploy on Render](docs/deploy/render.md)
- [Functional analysis](docs/analysis/functional-analysis.md) · [Technical analysis](docs/analysis/technical-analysis.md) (in Spanish)
