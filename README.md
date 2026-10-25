# Proyect-DDD-Claude

Proyecto personal de práctica que combina dos cursos:

- **Domain-Driven Design con Java 21 y Spring Boot 4.1**, sobre un dominio de cuentas corrientes (agregado `CheckingAccount`, entidades, objetos de valor, ArchUnit).
- **Claude Code para desarrollo de software**: el trabajo se organiza en issues de GitHub y se planifica e implementa con skills, subagentes, hooks y GitHub Actions ([arquitectura de IA](docs/ia/arquitectura-ia.md)).

Parte de una copia de [DDD-Lab-Java](https://github.com/dawnrock/DDD-Lab-Java) y la hace evolucionar en dos fotografías que se conservan por separado:

| Rama | Arquitectura | Backlog |
| ---- | ------------ | ------- |
| `master` | Punto de partida (solo dominio), congelada | — |
| `Monolithic` | Monolito en capas: web Thymeleaf + REST → aplicación → dominio / persistencia en memoria | [Fase 1 · Monolito en capas](https://github.com/dawnrock/Proyect-DDD-Claude/milestones) |
| `Hexagonal` | Puertos y adaptadores, eventos de dominio, factoría con puertos, adaptadores simulados | [Fase 2 · Arquitectura hexagonal](https://github.com/dawnrock/Proyect-DDD-Claude/milestones) |

Tecnologías: Java 21, Spring Boot 4.1, JUnit 5, ArchUnit, Lombok, Immutables, Thymeleaf, H2.

## Arrancar

```bash
./mvnw verify            # tests JUnit + ArchUnit
./mvnw spring-boot:run   # http://localhost:8080 · /actuator/health · /h2-console
```

En Windows, `mvnw.cmd`. Requiere Java 21.

`spring-boot:run` arranca con el perfil `demo`, que abre tres cuentas de demostración (dos minoristas y una de alto patrimonio) con los casos de uso. Para arrancar sin datos: `./mvnw spring-boot:run -Dspring-boot.run.profiles=default`. Los tests y `java -jar` no cargan los datos de demostración.

**Login (#50).** Hay que entrar con un usuario. Con el perfil `demo`: `admin` (ve todas las cuentas y abre cuentas) y `ada`, `grace` y `alan` (cada uno solo ve su cuenta), todos con la contraseña `demo` (datos de una práctica en local: en `application-demo.properties` solo está su hash BCrypt). La API usa HTTP Basic: `curl -u admin:demo http://localhost:8080/api/accounts`. Sin el perfil `demo` no hay usuarios; se puede crear un administrador con las variables de entorno `APP_ADMIN_USERNAME` y `APP_ADMIN_PASSWORD`.

## Flujo de trabajo con Claude Code

```text
/plan-feature 7      # issue #7 → plan en docs/plans/ + comentario en la issue (+ aviso Telegram)
/implement-plan 7    # worktree → TDD tarea a tarea → commits → PR contra la rama de la fase
/commit              # Conventional Commits en español
```

## Documentación

- [Arquitectura de IA](docs/ia/arquitectura-ia.md) · [Notificaciones Telegram](docs/ia/notificaciones.md)
- [Fase 1 · capas](docs/arquitectura/fase-1-capas.md) · [Fase 2 · hexagonal](docs/arquitectura/fase-2-hexagonal.md)
- [Glosario](docs/dominio/glosario.md) · [ADR](docs/adr/README.md) · [Planes](docs/plans/)
