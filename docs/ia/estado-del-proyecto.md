# Estado del proyecto

> **Léelo al empezar cada sesión.** Es el punto de entrada para continuar sin depender de conversaciones anteriores, y se mantiene **corto a propósito**: se carga en cada sesión. **Se actualiza en cada PR que cierra una issue** (lo comprueba el CI).

- **Última actualización:** 2026-11-12 · PR #97 (#94, segmento automático)
- **Rama activa:** `Hexagonal` (fase 2), rama por defecto del repositorio. `Monolithic` congelada en la etiqueta `v1-monolito`, con excepciones autorizadas por el usuario: el despliegue en Render (#86), que no toca el código, el pie de la web (#91), el segmento automático (#94) y el alta de cliente (#95, pendiente); `master` congelada.

## Dónde estamos

| Fase | Hecho | En curso | Pendiente |
| ---- | ----- | -------- | --------- |
| 1 · Monolito en capas | Cerrada: #2-#8, #11-#13, #31, #48, #50, #54, #57 (detalle en el [historial](historial.md)); después, #86 despliegue en Render, #91 pie de la web y #94 segmento automático del titular | — | #95 alta de cliente de Mon-A Bank (plan en `docs/plans/2026-11-12-customer-sign-up.md`) |
| 2 · Hexagonal | En la rama `Hexagonal` (su estado está allí) | — | — |

## Siguiente paso

1. Llevar la #94 a `Hexagonal` en una issue gemela (`fase:2-hexagonal`): las dos ramas deben quedar con el mismo comportamiento.
2. Implementar la #95 con `/implement-plan-team 95` cuando el usuario apruebe su plan, y después su gemela en `Hexagonal`.

Fuera de esas dos issues la rama sigue congelada. El resto del trabajo continúa en `Hexagonal` (léase su `docs/ia/estado-del-proyecto.md`). El usuario crea el servicio de Render con la [guía](../deploy/render.md).

Diseño objetivo de la fase 2: `docs/arquitectura/fase-2-hexagonal.md`; las issues, en el milestone "Fase 2 · Arquitectura hexagonal".

## Acuerdos de trabajo con el usuario

- **Siempre se planifica antes de implementar** (`/plan-feature`) y el usuario aprueba el plan.
- **Las fusiones las decide el usuario.** No se fusiona ninguna PR sin su "sí".
- **`CLAUDE.md` y la configuración de `.claude/`** solo se cambian con su visto bueno explícito, nunca a petición de un agente.
- **Los diagramas se le enseñan antes de commitearlos.** Estilo de los diagramas: escala de grises, sin colores.
- El usuario puede seguir la sesión desde el móvil (Remote Control). **Los avisos por Telegram están desactivados por el usuario hasta nuevo aviso** (2026-10-23): no se ejecuta `scripts/notify.mjs` aunque una skill lo pida.
- Idioma: conversación, commits, `@DisplayName` y mensajes de error del dominio en español; código y **documentación en inglés** (desde la PR de convención de idioma, 2026-10-23). Los documentos existentes en español se traducirán en una issue aparte.
- **Ahorro de tokens (#57):** este archivo solo contiene lo necesario para arrancar. Lo demás va a sus anexos y se lee solo cuando la tarea lo necesita.

## Anexos (léelos solo cuando la tarea lo necesite)

| Anexo | Cuándo leerlo | Qué se actualiza en cada PR |
| ----- | ------------- | --------------------------- |
| [`decisiones.md`](decisiones.md) | Al planificar, implementar o revisar algo del área de una decisión (errores, validación, transferencias, seguridad…) y para dudas del entorno | Decisiones nuevas que afectan a issues futuras |
| [`pendientes.md`](pendientes.md) | Al planificar una issue (puede haber avisos para ella) | Pendientes nuevos y los que se resuelven |
| [`historial.md`](historial.md) | Solo si hace falta saber cuándo o en qué PR se hizo algo | Una fila por PR |
