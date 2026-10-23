# 0007 · Equipo back/web con el contrato primero

- **Estado:** aceptada · 2026-10-23

## Contexto

Las funcionalidades de la fase 1 tocan a la vez el back (dominio, aplicación, persistencia y REST) y la web (Thymeleaf). Hacerlas en secuencia obliga a esperar al back para empezar las pantallas. El curso de Claude Code (capítulo 13) propone **equipos de agentes**: un lead y varios teammates con su propio contexto, que se comunican entre ellos y comparten una lista de tareas, cada uno en su git worktree.

Con Thymeleaf, la web no consume la API REST: sus controladores llaman a los servicios de aplicación dentro de la misma aplicación (ADR 0005).

## Decisión

- Para cada issue que toca back y web (etiqueta `equipo`), se acuerda primero un **contrato** en `docs/contracts/<slug>.md`, que cubre las dos fronteras reales:
  - **Web ↔ back:** servicio de aplicación (firmas), commands y vistas (Immutables) y errores.
  - **REST:** el fragmento de `docs/api/openapi.yaml`.
- El lead crea los tipos del contrato sin lógica. Los agentes `backend-dev` y `web-dev` lo revisan y lo acuerdan entre ellos, y se **congela** en un commit.
- Cada agente implementa su lado en paralelo en su worktree. La web prueba contra el servicio simulado (`@MockitoBean`) hasta la integración.
- Los cambios de contrato se renegocian a través del lead y se registran en el propio contrato. Nadie lo cambia en su rama.
- Lo orquesta la skill `implement-plan-team`. `plan-feature` añade `T0 · Contrato` y el campo `Lado` a las tareas.

## Alternativas

- **La web consume la API REST** (`RestClient`, patrón BFF): un único contrato OpenAPI, pero la aplicación se llamaría a sí misma por HTTP y la web dejaría de ser un adaptador más de los mismos casos de uso en la fase 2. Descartada.
- **Subagentes en lugar de un equipo:** más baratos, pero no se hablan entre ellos, así que no pueden negociar el contrato. Se siguen usando para tareas de un solo lado.

## Consecuencias

- Back y web avanzan a la vez; la integración solo une dos ramas que ya compilan contra los mismos tipos.
- El contrato documenta cada funcionalidad y se puede revisar en la PR.
- Cada teammate es una instancia completa de Claude: una issue en equipo consume entre 2 y 3 veces más cuota. Solo se usa cuando la issue toca los dos lados.
- En la fase 2, el contrato web ↔ back pasa a ser el de los puertos de entrada (`*Commands` / `*Queries`), sin cambiar el flujo.
