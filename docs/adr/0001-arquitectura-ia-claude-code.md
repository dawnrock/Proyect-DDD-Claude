# 0001 · Arquitectura de IA con Claude Code

- **Estado:** aceptada · 2026-10-23

## Contexto

El proyecto sirve para practicar a la vez DDD en Java y el trabajo con agentes. En [curso-claude](https://github.com/dawnrock/curso-claude) ya existe un flujo probado: issues de GitHub → plan → implementación en worktree con TDD → PR, apoyado en skills, hooks y GitHub Actions. Aquel proyecto avisaba por Slack, que no se usa en este.

## Decisión

- Reutilizar ese flujo adaptado a Java/Maven: `CLAUDE.md`, `/commit`, skills `plan-feature` e `implement-plan`, hook de bloqueo del backlog y workflows `claude.yml` y `claude-code-review.yml`.
- Añadir piezas propias del dominio: skill `ddd-building-block` (con `references/`, `templates/` y `scripts/`), skill `hexagonal-adapter`, agentes `revisor-ddd` y `buscador-referencias`, y un `ci.yml` determinista.
- Escribir hooks y scripts en **Node** en lugar de bash + `jq`, para que funcionen igual en Windows, Linux y en la CI.
- Sustituir Slack por **Telegram** (Bot API): push inmediato al móvil, gratuito, sin servidor de correo. El email (SMTP) se descartó por necesitar credenciales de correo y llegar con más retraso.

## Consecuencias

- Las tareas siempre parten de una issue con milestone de fase; el plan queda versionado en `docs/plans/` y publicado en la issue.
- Las notificaciones son opcionales: sin token, nada falla.
- Los workflows de Claude necesitan el secreto `CLAUDE_CODE_OAUTH_TOKEN` (`claude setup-token`) y, como GitHub ejecuta los de issues desde la rama por defecto, esa rama debe ser la de la fase activa (ADR 0002).

## Nota · 2026-10-23: versión fijada de `claude-code-action`

Desde la v1.0.255, `claude-code-review.yml` terminaba con `is_error: true` en ~2 s, 1 turno y 0 $, sin llegar al modelo. Fallaba también en curso-claude con su token anterior, y `claude -p` funcionaba en local con la misma configuración. Los workflows se fijan a `anthropics/claude-code-action@v1.0.254`, la última versión con la que funcionó. Antes de volver a `@v1`, hay que comprobar en una PR que la versión nueva ya pasa.

Detalle de cada pieza: [docs/ia/arquitectura-ia.md](../ia/arquitectura-ia.md).
