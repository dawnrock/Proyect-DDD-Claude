# Plan: Flujo de equipo back/web con el contrato primero

- **Issue:** [#31](https://github.com/dawnrock/Proyect-DDD-Claude/issues/31)
- **Tipo:** feature (claude)
- **Fase:** 1 · Monolito en capas
- **Rama base:** `Monolithic`
- **Fecha:** 2026-10-23
- **Estado:** completado
- **Estimación total:** ~40 min (5 tareas)

## Objetivo

Que las issues que tocan back y web se implementen con un equipo de agentes que acuerdan primero el contrato (enfoque A: servicio, commands y vistas de `application` más el fragmento de OpenAPI) y después trabajan en paralelo. El usuario aprobó el diseño en la conversación del 2026-10-23.

### Fuera de alcance

- Implementar código Java: el primer uso real del flujo será la primera funcionalidad vertical de la fase 1.
- Cambiar la interfaz web (sigue Thymeleaf, ADR 0005).

## Contexto

| Capa / paquete | Archivo | Cambio |
| -------------- | ------- | ------ |
| claude | `.claude/agents/backend-dev.md`, `.claude/agents/web-dev.md` | Nuevos teammates |
| claude | `.claude/skills/implement-plan-team/` | Nueva skill y plantilla de contrato |
| claude | `.claude/skills/plan-feature/` | T0 · Contrato y campo `Lado` |
| docs | `CLAUDE.md`, `docs/ia/arquitectura-ia.md`, `docs/adr/0007-…`, `docs/contracts/README.md` | Flujo documentado |

## Tareas

### [x] T1 — Agentes backend-dev y web-dev (~10 min)

- **Capa:** claude
- **Criterios de aceptación:**
  - [x] Cada agente declara qué paquetes son suyos y cuáles no puede editar, y cómo revisar el contrato.

### [x] T2 — Skill implement-plan-team y plantilla de contrato (~15 min)

- **Capa:** claude
- **Criterios de aceptación:**
  - [x] La skill define cómo se redacta, acuerda, congela y renegocia el contrato, los worktrees por lado y la integración.

### [x] T3 — plan-feature con T0 · Contrato y campo Lado (~5 min)

- **Capa:** claude
- **Criterios de aceptación:**
  - [x] La skill y la plantilla piden T0 y `Lado` cuando la issue toca back y web.

### [x] T4 — Documentación y ADR 0007 (~10 min)

- **Capa:** docs
- **Criterios de aceptación:**
  - [x] `CLAUDE.md`, `docs/ia/arquitectura-ia.md` (con diagrama de secuencia) y el ADR 0007 describen el flujo.

### [ ] T5 — Reorganizar el backlog en funcionalidades verticales y planificar una con T0 (~10 min)

- **Capa:** docs (GitHub)
- **Criterios de aceptación:**
  - [ ] Las issues de la fase 1 que tocan back y web quedan agrupadas por funcionalidad con las etiquetas `lado:*` y `equipo`.
  - [ ] Una de ellas queda planificada con `T0 · Contrato` (se hace al fusionar esta PR, porque `plan-feature` debe leer la versión nueva de la skill).

## Criterios de aceptación globales

- [x] `./mvnw verify` termina en `BUILD SUCCESS` (no se toca código Java).
- [x] Ningún agente puede editar el lado del otro según su definición.
- [ ] La reorganización del backlog y el primer plan con T0 están hechos (T5).

## Riesgos y preguntas abiertas

- Los equipos de agentes son experimentales y cada teammate es una instancia completa: se usarán solo en issues con la etiqueta `equipo`.
