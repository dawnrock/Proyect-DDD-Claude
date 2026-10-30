# Proyect-DDD-Claude

Personal practice project that combines two courses:

- **Domain-Driven Design with Java 21 and Spring Boot 4.1**, on a checking-accounts domain (`CheckingAccount` aggregate, entities, value objects, ArchUnit).
- **Claude Code for software development**: the work is organised in GitHub issues and planned and implemented with skills, subagents, hooks and GitHub Actions ([AI architecture](docs/ia/arquitectura-ia.md)).

It starts from a copy of [DDD-Lab-Java](https://github.com/dawnrock/DDD-Lab-Java) and evolves it into two snapshots kept apart:

| Branch | Architecture | Backlog |
| ------ | ------------ | ------- |
| `master` | Starting point (domain only), frozen | — |
| `Monolithic` | Layered monolith: Thymeleaf web + REST → application → domain / in-memory persistence. **Closed and frozen at tag `v1-monolito`** | [Phase 1 · Layered monolith](https://github.com/dawnrock/Proyect-DDD-Claude/milestones) |
| `Hexagonal` | Ports and adapters, domain events, factory with ports, simulated adapters. **Default and active branch** | [Phase 2 · Hexagonal architecture](https://github.com/dawnrock/Proyect-DDD-Claude/milestones) |

Technologies: Java 21, Spring Boot 4.1, Spring Security, JUnit 5, ArchUnit, Lombok, Immutables, Thymeleaf, H2.

## Run

```bash
./mvnw verify            # JUnit + ArchUnit tests
./mvnw spring-boot:run   # http://localhost:8080 · /actuator/health · /h2-console
```

On Windows, `mvnw.cmd`. Requires Java 21.

`spring-boot:run` starts with the `demo` profile, which opens three demo accounts (two retail and one high net worth) through the use cases. To start without data: `./mvnw spring-boot:run -Dspring-boot.run.profiles=default`. Tests and `java -jar` do not load the demo data.

**Login (#50).** You must sign in. With the `demo` profile: `admin` (sees every account and opens accounts) and `ada`, `grace` and `alan` (each one only sees their own account), all with password `demo` (local practice data: `application-demo.properties` only holds its BCrypt hash). The API uses HTTP Basic: `curl -u admin:demo http://localhost:8080/api/accounts`. Without the `demo` profile there are no users; an administrator can be created with the `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` environment variables.

## Workflow with Claude Code

```text
/plan-feature 7          # issue #7 → plan in docs/plans/ + comment on the issue
/implement-plan 7        # worktree → TDD task by task → commits → PR against the phase branch
/implement-plan-team 7   # back + web issue: contract first, backend-dev and web-dev in parallel, one PR
/commit                  # Conventional Commits in Spanish
```

## Documentation

- [AI architecture](docs/ia/arquitectura-ia.md) · [Telegram notifications](docs/ia/notificaciones.md) · [Project status](docs/ia/estado-del-proyecto.md)
- [Phase 1 · layers](docs/arquitectura/fase-1-capas.md) · [Phase 2 · hexagonal](docs/arquitectura/fase-2-hexagonal.md)
- [Glossary](docs/dominio/glosario.md) · [ADR](docs/adr/README.md) · [Plans](docs/plans/) · [Contracts](docs/contracts/) · [OpenAPI](docs/api/openapi.yaml)
- [Functional analysis](docs/analysis/functional-analysis.md) · [Technical analysis](docs/analysis/technical-analysis.md) (in Spanish)
