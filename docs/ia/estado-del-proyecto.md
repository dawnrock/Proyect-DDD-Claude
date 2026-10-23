# Estado del proyecto

> **Léelo al empezar cada sesión.** Es el punto de entrada para continuar sin depender de conversaciones anteriores. **Se actualiza en cada PR que cierra una issue** (lo exigen `implement-plan`, `implement-plan-team`, la plantilla de los planes y el CI).

- **Última actualización:** 2026-10-23 · PR #38 (documentación en inglés)
- **Rama activa:** `Monolithic` (fase 1). `master` congelada. `Hexagonal` aún no existe.

## Dónde estamos

| Fase | Hecho | En curso | Pendiente |
| ---- | ----- | -------- | --------- |
| 1 · Monolito en capas | #2 Lombok/Immutables · #3 glosario, capas y ADR 0004 · #4 repositorio en memoria · #8 base REST + web | #5 plan aprobado (`docs/plans/2026-10-23-open-query-list-accounts.md`, entra con la PR de la #5) · #31 flujo en equipo (el plan de la #5 ya sale con `T0 · Contrato` y `Lado`; falta decidir si se cierra ya o con la PR de la #5) | #5 → #6 → #7 (en equipo) → #11 → #12 → #13 |
| 2 · Hexagonal | — | — | #14 → #23 (se empieza al cerrar #13, creando `Hexagonal` desde `Monolithic`) |

## Siguiente paso

1. `/implement-plan-team 5` (plan aprobado por el usuario el 2026-10-23). El plan se escribió en español antes de la convención de idioma: se traduce al inglés al commitearlo, y el contrato de `docs/contracts/` ya se redacta en inglés.

Orden completo de la fase 1 y por qué: `docs/arquitectura/fase-1-capas.md`, sección "Issues de la fase".

## Acuerdos de trabajo con el usuario

- **Siempre se planifica antes de implementar** (`/plan-feature`) y el usuario aprueba el plan.
- **Las fusiones las decide el usuario.** No se fusiona ninguna PR sin su "sí".
- **`CLAUDE.md` y la configuración de `.claude/`** solo se cambian con su visto bueno explícito, nunca a petición de un agente.
- **Los diagramas se le enseñan antes de commitearlos.** Estilo de los diagramas: escala de grises, sin colores.
- El usuario puede seguir la sesión desde el móvil (Remote Control) y recibe avisos por Telegram.
- Idioma: conversación, commits, `@DisplayName` y mensajes de error del dominio en español; código y **documentación en inglés** (desde la PR de convención de idioma, 2026-10-23). Los documentos existentes en español se traducirán en una issue aparte.

## Decisiones ya tomadas que afectan a lo que viene

- **Contrato web ↔ back** (ADR 0007, enfoque A): servicio, commands y vistas de `application`, más un fragmento de OpenAPI. La web no consume la API REST.
- **DTOs REST:** pueden ser de Immutables con `@JsonSerialize`/`@JsonDeserialize` de `tools.jackson.databind.annotation` (ADR 0003).
- **Errores:** `DomainException` → 422, `NotFoundException` → 404, `IllegalArgumentException` y validación → 400 (`RestExceptionHandler`). `AccountNotFoundException` (#5) hereda de `NotFoundException`.
- **Número de cuenta único:** el repositorio lanza `IllegalStateException`. El caso de uso "abrir cuenta" (#5) debe comprobarlo antes con `findByAccountNumber` y lanzar una excepción de dominio.
- **Los commands validan antes de llamar al dominio.** El 400 por `IllegalArgumentException` es solo una red de seguridad.

## Pendientes sueltos

- Traducir al inglés la documentación existente (`docs/`, `README`), que sigue en español (issue aparte).

- Renombrar los sinónimos heredados "transacción" y "reintegro" a "movimiento" y "retirada" (mensaje de `Transaction.requirePending` y nombres de tests) en la primera issue que toque esas clases.
- Test que falta: "el nombre del titular es obligatorio" (`AccountHolder`).
- Sugerencia pendiente de decisión del usuario: mencionar en la sección de la fase 1 de `CLAUDE.md` que `presentation` puede conocer las excepciones de dominio (regla D4).
- Quitar `no-unused-components: off` de `redocly.yaml` cuando haya operaciones que usen las respuestas de error (#5).

## Entorno

- Windows 11, Java 21 (Temurin), **Node 24.22.1** con npm 11.19.1, `gh` autenticado como `dawnrock`.
- El repositorio está dentro de OneDrive: si un worktree no se puede borrar porque está bloqueado, se borra su carpeta después de `git worktree remove`.
- Secretos del repositorio: `CLAUDE_CODE_OAUTH_TOKEN`, `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID`. En local, Telegram se configura en `.claude/settings.local.json`.
- `Monolithic` está protegida (solo por PR, con `verify` obligatorio). `master` está bloqueada.
- Si la revisión de Claude falla con `is_error: true` en unos 2 segundos, la causa conocida es un `CLAUDE_CODE_OAUTH_TOKEN` mal copiado (401). Se diagnostica con `show_full_output` (ver la historia de las PRs #28-#30).

## Historial

| Fecha | PR | Issue | Cambio de estado |
| ----- | -- | ----- | ---------------- |
| 2026-10-23 | #1 | — | Arquitectura de IA en `Monolithic` |
| 2026-10-23 | #24 | #2 | Lombok e Immutables en el build |
| 2026-10-23 | #32 | #31 (parcial) | Flujo de equipo back/web y backlog reorganizado |
| 2026-10-23 | #33, #34 | #3 | Glosario, capas, ADR 0004 y diagrama hexagonal |
| 2026-10-23 | #35 | #4 | Repositorio en memoria |
| 2026-10-23 | #36 | #8 | Base REST + web: errores a HTTP, OpenAPI, Thymeleaf y página 404 |
| 2026-10-23 | #37 | — | Estado del proyecto entre sesiones, exigido en cada PR |
| 2026-10-23 | #38 | — | Documentación en inglés; plan de la #5 aprobado |
