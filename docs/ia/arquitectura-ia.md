# Arquitectura de IA del proyecto

Cómo se aplica en este repositorio lo aprendido en el curso *Claude Code para desarrollo de software*. Adapta la arquitectura de [curso-claude](https://github.com/dawnrock/curso-claude) (Node/Express/Angular) a un proyecto Java 21 + Spring Boot con DDD.

## Mapa de piezas

```mermaid
flowchart LR
    I[Issue de GitHub<br/>milestone de fase] -->|/plan-feature N| P[docs/plans/*.md<br/>+ comentario en la issue]
    P -->|/implement-plan N| W[git worktree<br/>TDD por tarea]
    W -->|/commit| C[1 commit por tarea]
    W -->|revisor-ddd| R[revisión DDD]
    C --> PR[PR contra Monolithic / Hexagonal]
    PR --> CI[CI: check-domain + mvnw verify]
    PR --> CR[Claude Code Review]
    P -. notify.mjs .-> T[(Telegram)]
    PR -. notify.mjs .-> T
    CI -. si falla .-> T
```

| Pieza | Archivo | Capítulo del curso | Para qué |
| ----- | ------- | ------------------ | -------- |
| Contexto global | `CLAUDE.md` | 6.2, 7.2 | Stack, comandos, arquitectura por fase, convenciones y restricciones. Breve; el detalle está indexado en `docs/` sin `@`. |
| Contexto por carpeta | `src/test/CLAUDE.md` | 7.2.3 | Convenciones de JUnit y ArchUnit; solo se carga al trabajar en los tests. |
| Reglas obligatorias | `.claude/settings.json` (`permissions`) | 7.2.4 | `allow` para comandos seguros (`./mvnw`, `git` de lectura); `deny` para `.env`, el token, los hooks y `git push --force`/`master`. |
| Comando | `.claude/commands/commit.md` | 8.4 | Conventional Commits en español; se niega a commitear en `master`. |
| Skill | `.claude/skills/plan-feature/` | 9, 9.2.3 | Issue → plan con tareas de 5-10 min y criterios, según `assets/template.md`. |
| Skill | `.claude/skills/implement-plan/` | 9, 13.1 | Plan → worktree → TDD → commits → PR contra la rama de la fase. |
| Skill con material de apoyo | `.claude/skills/ddd-building-block/` | 9.2 | `references/` (reglas DDD, Lombok/Immutables), `templates/` (VO, entidad, test) y `scripts/check-domain.mjs`. |
| Skill de fase 2 | `.claude/skills/hexagonal-adapter/` | 9.2 | Plantillas de puerto, adaptador y test de adaptador. |
| Subagente | `.claude/agents/revisor-ddd.md` | 10.2 | Revisión de DDD y arquitectura, sin poder editar, con Sonnet. |
| Subagente | `.claude/agents/buscador-referencias.md` | 10.1, 10.3 | Búsquedas amplias con Haiku para no llenar el contexto principal. |
| Teammates | `.claude/agents/backend-dev.md`, `.claude/agents/web-dev.md` | 10.2, 13.2 | Un agente por lado (back / web), con herramientas y paquetes limitados; revisan y negocian el contrato. |
| Skill de equipo | `.claude/skills/implement-plan-team/` | 13.1, 13.2 | Issue back + web: contrato primero (`docs/contracts/`), implementación en paralelo en worktrees, integración y una PR (ADR 0007). |
| Hook `PreToolUse` | `.claude/hooks/block-backlog-json.mjs` | 12 | Bloquea la lectura de la semilla del backlog: la verdad son las issues. |
| Hooks `Notification` / `Stop` | `.claude/hooks/notify-telegram.mjs` | 12.1 | Aviso push al móvil cuando Claude espera permiso o termina. |
| Scripts deterministas | `scripts/seed-issues.mjs`, `scripts/notify.mjs` | 9.2.2, 14 | Crear el backlog; enviar notificaciones. |
| CI/CD | `.github/workflows/ci.yml` | 14 | Lo determinista (build, tests, ArchUnit, reglas del dominio) sin IA. |
| CI/CD con IA | `.github/workflows/claude.yml`, `claude-code-review.yml` | 14 | `@claude` en issues/PR y revisión automática de cada PR. |
| Equipos de agentes | `env.CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS` | 13.2 | Habilitado para tareas de investigación o revisión en paralelo. |

## Funcionalidades back + web en equipo (capítulo 13)

```mermaid
sequenceDiagram
    participant L as Lead (implement-plan-team)
    participant B as backend-dev
    participant W as web-dev
    L->>L: T0 · borrador de docs/contracts/slug.md + tipos sin lógica
    L->>B: revisa el contrato
    L->>W: revisa el contrato
    B-->>W: propuestas / acuerdo
    W-->>B: propuestas / acuerdo
    L->>L: commit "acuerda el contrato" (congelado)
    par en paralelo, cada uno en su worktree
        B->>B: domain → application → persistence → REST (TDD)
    and
        W->>W: controladores y plantillas Thymeleaf contra el servicio simulado (TDD)
    end
    B-->>L: rama -back lista
    W-->>L: rama -web lista
    L->>L: merge back + web, tareas "ambos", revisor-ddd, mvnw verify, PR
```

Un cambio de contrato durante la implementación se pide al lead, se acuerda con el otro lado y se registra en la sección "Cambios" del contrato.

## Estrategia de modelos y modos (capítulos 4 y 5)

- Planificar (`/plan-feature`) en **modo plan** con Opus (o `opusplan`); implementar (`/implement-plan`) con Sonnet en edición automática o modo auto.
- `buscador-referencias` usa Haiku; `revisor-ddd`, Sonnet.

## Seguridad

- Los hooks se ejecutan con tus permisos: el agente no puede editarlos (`deny` sobre `.claude/hooks/**`).
- El token de Telegram vive en `.claude/settings.local.json` (ignorado por git) y el agente tiene prohibido leerlo.
- `master` está congelada: `deny` de `git push origin master` y `/commit` se niega a commitear en ella.

## Registro de uso

Cada fase añade aquí, al cerrarse, qué planes y PRs se generaron con estas piezas y qué ajustes hubo que hacer.
