---
name: implement-plan-team
description: Implementa el plan de una issue que toca back y web (etiqueta equipo, o plan con T0 · Contrato y tareas con Lado back y web) con un equipo de agentes. El lead redacta el contrato (application + OpenAPI), backend-dev y web-dev lo revisan y acuerdan, se congela en un commit, cada uno implementa su lado en paralelo en su worktree, y el lead integra, verifica y abre una sola PR contra la rama de la fase. Úsala cuando se pida implementar en equipo, en paralelo back y web, o una issue con la etiqueta equipo.
argument-hint: <número de issue de GitHub>
allowed-tools: Read, Grep, Glob, Edit, Write, AskUserQuestion, Bash(git:*), Bash(./mvnw:*), Bash(mvnw.cmd:*), Bash(node:*), Bash(npx @redocly/cli:*), Bash(curl:*), Bash(ls:*), Bash(cp:*), Bash(gh issue view:*), Bash(gh pr create:*), Bash(gh pr view:*)
---

Implementa en equipo el plan de la issue: $ARGUMENTS

Eres el **lead**. Coordinas, redactas el contrato, integras y verificas; no implementas las tareas de ningún lado. Los teammates son `backend-dev` y `web-dev` (`.claude/agents/`). Todo lo de [implement-plan](../implement-plan/SKILL.md) sobre issue, rama base, TDD, commits, criterios globales, PR y Telegram aplica igual; aquí solo se describe lo que cambia.

## 0. Comprobaciones

- Carga la issue y el plan como en `implement-plan` (paso 1). El plan debe tener **T0 · Contrato** y tareas con `Lado: back`, `Lado: web` o `Lado: ambos`. Si no las tiene, detente y sugiere replanificar con `/plan-feature <número>`.
- Requiere equipos de agentes (`CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`, ya en `.claude/settings.json`). Si no están disponibles, avisa y ofrece seguir en secuencia con `implement-plan`.
- Cada teammate es una instancia completa: avisa al usuario de que consume más cuota y confirma con `AskUserQuestion` antes de lanzar el equipo.

## 1. Rama de integración

Crea el worktree de integración desde la rama base, como en `implement-plan` (paso 2): rama `<tipo>/<slug>` en `.claude/worktrees/<slug>`. Commitea el plan si hace falta.

## 2. Contrato (T0)

1. Redacta `docs/contracts/<slug>.md` a partir de [assets/contract-template.md](assets/contract-template.md): servicio de aplicación (firmas), commands y vistas (campos y tipos), errores (excepción → qué ve la web → código HTTP) y el fragmento de `docs/api/openapi.yaml`.
2. Crea en el worktree de integración los **tipos del contrato** para que los dos lados compilen contra ellos: la clase del servicio de aplicación con las firmas y cuerpo `throw new UnsupportedOperationException("pendiente: backend-dev")`, los `*Def` de Immutables de commands y vistas, y el fragmento de OpenAPI. Sin lógica.
3. Lanza el equipo con los dos teammates y pídeles que **revisen el contrato** (cada uno desde su lado, según su definición) y que se respondan entre ellos hasta estar de acuerdo. Tú arbitras: si no convergen en dos rondas, decide tú y explica por qué en el contrato.
4. Con el acuerdo, `./mvnw verify` (debe compilar) y commit `docs(contract): acuerda el contrato <slug>`. El contrato queda **congelado**: anota en el documento el hash del commit.

## 3. Implementación en paralelo

1. Crea un worktree por lado desde el commit del contrato:
   ```bash
   git worktree add -b <tipo>/<slug>-back .claude/worktrees/<slug>-back <commit-contrato>
   git worktree add -b <tipo>/<slug>-web  .claude/worktrees/<slug>-web  <commit-contrato>
   ```
2. Asigna a `backend-dev` las tareas `Lado: back` (en `<slug>-back`) y a `web-dev` las `Lado: web` (en `<slug>-web`), en la lista de tareas compartida del equipo. Las `Lado: ambos` las haces tú en la integración.
3. Cada teammate sigue TDD, un commit por tarea y marca sus tareas en **su** copia del plan. La web trabaja contra el servicio del contrato simulado con `@MockitoBean`.

### Renegociar el contrato

Si un teammate necesita cambiar el contrato, **para su tarea**, envía la propuesta al lead y al otro teammate, y espera. Si se acepta, el lead aplica el cambio en el worktree de integración con un commit `docs(contract): ...`, anota el motivo en la sección "Cambios" del contrato, y cada lado lo incorpora con `git merge <rama-integración>`. Nadie cambia el contrato en su rama por su cuenta.

## 4. Integración

1. Cuando los dos terminen, en el worktree de integración: `git merge --no-ff <tipo>/<slug>-back` y después `<tipo>/<slug>-web`. Resuelve conflictos (solo deberían aparecer en el plan: conserva las tareas marcadas de ambos lados).
2. Haz las tareas `Lado: ambos` (por ejemplo, un test de extremo a extremo con el servicio real en lugar del stub).
3. Pide una revisión a `revisor-ddd` sobre `git diff origin/<rama-base>...HEAD` y corrige lo relevante.
4. Criterios globales, `**Estado:** completado`, PR única contra la rama de la fase con `Closes #<número>` y aviso por Telegram, como en `implement-plan` (pasos 6-8). En el cuerpo de la PR resume: contrato acordado (y renegociaciones), tareas por lado y commits de cada uno.
5. Cierra el equipo. No borres los worktrees ni las ramas `-back`/`-web`: lo decide el usuario tras la revisión.

## 5. Informar

Igual que `implement-plan` (paso 9), añadiendo: cuántas rondas llevó acordar el contrato, renegociaciones durante la implementación y qué hizo cada teammate.
