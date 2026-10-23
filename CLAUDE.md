# CLAUDE.md

Proyecto de práctica: aplicar **DDD con Java 21 + Spring Boot 4.1** sobre el dominio de cuentas corrientes, y la **arquitectura de IA de Claude Code** (skills, agentes, hooks, issues de GitHub). Parte del estado del Lab 22 del curso de DDD (solo `domain.model`).

El proyecto mantiene dos "fotografías" en ramas separadas:

| Rama | Arquitectura | Milestone / etiqueta |
| ---- | ------------ | -------------------- |
| `master` | Punto de partida (Lab 22). **Congelada: no se toca nunca.** | — |
| `Monolithic` | Monolito en capas | `Fase 1 · Monolito en capas` / `fase:1-monolito` |
| `Hexagonal` | Puertos y adaptadores (se crea desde `Monolithic` al cerrar la fase 1) | `Fase 2 · Arquitectura hexagonal` / `fase:2-hexagonal` |

Antes de tocar código, comprueba en qué rama estás (`git branch --show-current`) y aplica **solo** las reglas de arquitectura de esa fase.

## Comandos

```bash
./mvnw verify                      # compila + todos los tests (JUnit + ArchUnit). Es la comprobación de "terminado"
./mvnw test -Dtest=CheckingAccountTests              # una clase de test
./mvnw test -Dtest='CheckingAccountTests#nombre*'    # un método
./mvnw spring-boot:run             # arranca en :8080; GET /actuator/health → {"status":"UP"}
```

- En Windows usa `mvnw.cmd`. Necesita Java 21 (`JAVA_HOME`).
- H2 en memoria (`jdbc:h2:mem:ddd`), consola en `/h2-console`. La persistencia puede ser simulada (repositorios en memoria): es un proyecto de práctica.

## Arquitectura por fase

**Fase 1 · `Monolithic` (capas):** `presentation` (`web` Thymeleaf, `rest`) → `application` → `domain` (`model`, `service`) y `persistence`; `persistence` solo conoce `domain`, y `domain` no conoce a nadie. Las capas usan clases concretas, sin interfaces de puerto (ADR 0004). Lo verifica `LayeredArchitectureTests` (ArchUnit).

**Fase 2 · `Hexagonal`:** `domain.model` (modelo + puertos de salida), `domain.service`, `application` (servicios + puertos de entrada `*Commands`/`*Queries`, `listener`), `adapter.in.{cli,rest,web}`, `adapter.out.{persistence,event,taxagency}`, `config`. Las dependencias apuntan siempre hacia el hexágono. Lo verifica `HexagonalArchitectureTests`.

En ambas fases el dominio no depende de Spring ni de `jakarta` y su estado es privado (`DomainIsolationTests`).

## Convenciones

- Código (clases, métodos, variables) y **documentación** (`docs/`: planes, contratos, ADR, arquitectura, glosario, OpenAPI; y las plantillas que los generan) en **inglés**. Los documentos que aún están en español se traducen en una issue aparte; los nuevos ya se escriben en inglés.
- La conversación con el usuario, los mensajes de error del dominio, los commits y los `@DisplayName` siguen en español.
- Accesores estilo record (`id()`, `currentBalance()`), nunca `getX()`/`setX()` en el dominio.
- Los agregados se crean con métodos factoría estáticos (`CheckingAccount.open(...)`) y constructor privado; solo cambian de estado mediante métodos de negocio.
- Lenguaje ubicuo: usa los términos de `docs/dominio/glosario.md`. Si aparece un término nuevo, añádelo allí.

### Lombok e Immutables: dónde sí y dónde no

| Librería | Sí | No |
| -------- | -- | -- |
| Lombok | `@RequiredArgsConstructor` (inyección por constructor) y `@Slf4j` fuera del dominio. En el dominio solo `@Getter` (accesores fluidos vía `lombok.config`) y `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` en entidades. `@Builder` solo en builders de test | `@Data`, `@Setter`, `@AllArgsConstructor`, `@Builder`, `@Value` en `domain..` (rompen invariantes) |
| Immutables | Commands, queries y vistas de `application`; DTOs de los adaptadores | Cualquier clase de `domain..` (su código generado deja dependencias `org.immutables` en el bytecode) |

ArchUnit no ve las anotaciones de Lombok (retención `SOURCE`): lo comprueba `node .claude/skills/ddd-building-block/scripts/check-domain.mjs`.

## Flujo de trabajo con issues

1. Las tareas son issues de GitHub (fuente de verdad), agrupadas por milestone de fase.
2. `/plan-feature <n>` genera el plan en `docs/plans/` y lo publica en la issue. No se implementa sin plan aprobado.
3. `/implement-plan <n>` lo implementa en un worktree con TDD, un commit por tarea, y abre la PR contra la rama de la fase (`Monolithic` o `Hexagonal`), nunca contra `master`.
4. Commits con `/commit` (Conventional Commits en español).
5. **Back + web en la misma issue** (etiqueta `equipo`): `/implement-plan-team <n>`. Primero se acuerda el **contrato** (`docs/contracts/<slug>.md`: servicio, commands y vistas de `application`, errores y fragmento de OpenAPI) entre los agentes `backend-dev` y `web-dev`; se congela en un commit y cada uno implementa su lado en paralelo en su worktree. Nadie cambia el contrato por su cuenta (ADR 0007).

## Estado del proyecto

**Al empezar cada sesión, lee `docs/ia/estado-del-proyecto.md`**: punto exacto del trabajo, siguiente paso, acuerdos con el usuario y pendientes. Toda PR que cierra una issue lo actualiza (el CI lo comprueba).

## Documentación (índice; léela solo cuando la tarea lo necesite)

- `docs/ia/arquitectura-ia.md`: qué pieza de Claude Code hace qué en este repo.
- `docs/arquitectura/fase-1-capas.md`, `docs/arquitectura/fase-2-hexagonal.md`: diseño objetivo de cada fase.
- `docs/adr/`: decisiones (ramas por fase, Lombok/Immutables, Thymeleaf, Telegram…).
- `docs/dominio/glosario.md`: lenguaje ubicuo y reglas de negocio.
- `docs/api/openapi.yaml`: contrato REST (cuando exista). Cualquier cambio en la API lo actualiza.
- `docs/contracts/`: contratos back ↔ web acordados por funcionalidad.

## Restricciones

- No hagas push a `master` ni la uses como base de ninguna PR.
- No uses `git push --force`, `--no-verify` ni `git add -A`.
- No añadas dependencias fuera de las previstas (Lombok, Immutables, Thymeleaf, Mockito, ArchUnit) sin justificarlo en un ADR.
- Los secretos (token de Telegram, `.env`) viven en `.claude/settings.local.json` o variables de entorno, nunca en el repositorio.
