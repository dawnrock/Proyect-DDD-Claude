---
name: implement-plan
description: Implementa el plan de una issue de GitHub (generado con plan-feature) en un git worktree aislado, tarea a tarea, con TDD estricto (JUnit + ArchUnit, ./mvnw). Cada tarea terminada se commitea y se marca en el plan; al cumplir los criterios globales abre una PR contra la rama de la fase (Monolithic o Hexagonal, nunca master) y avisa por Telegram. Úsala cuando se pida implementar, ejecutar o llevar a cabo un plan o una issue.
argument-hint: <número de issue de GitHub>
allowed-tools: Read, Grep, Glob, Edit, Write, AskUserQuestion, Bash(git:*), Bash(./mvnw:*), Bash(mvnw.cmd:*), Bash(node:*), Bash(npx @redocly/cli:*), Bash(curl:*), Bash(ls:*), Bash(cp:*), Bash(gh issue view:*), Bash(gh pr create:*), Bash(gh pr view:*)
---

Implementa el plan indicado siguiendo el proceso de abajo. El plan es la fuente de verdad: no añadas trabajo que no esté en él ni te saltes tareas.

Issue indicada por el usuario: $ARGUMENTS

## Pasos

### 1. Cargar la issue y su plan

- Acepta `42`, `#42` o la URL. Si no hay número, pídelo con `AskUserQuestion`.
- `gh issue view <número> --json number,title,body,labels,milestone,state,url,comments`. Si falla, detente y explícalo. Si está cerrada, pregunta si continuar.
- **Rama base** según la fase de la issue: `fase:1-monolito` → `Monolithic`, `fase:2-hexagonal` → `Hexagonal`. Si la rama base no existe en `origin`, detente: la fase 2 no empieza hasta que se cierre la fase 1. **Nunca** uses `master`.
- Localiza el plan:
  1. En `docs/plans/` de la rama base, el plan cuyo `**Issue:**` sea `#<número>`.
  2. Si no está, el comentario más reciente de la issue que empiece por `# Plan:`; guárdalo en `docs/plans/AAAA-MM-DD-<slug>.md`.
  3. Si no hay plan, detente y sugiere `/plan-feature <número>`.
- Lee el plan completo, `CLAUDE.md` y `src/test/CLAUDE.md`. Resuelve con el usuario las preguntas abiertas que bloqueen alguna tarea **antes** de crear el worktree.

### 2. Crear el git worktree

- Slug = nombre del plan sin fecha ni `.md`. Rama `<tipo>/<slug>` (`feat`, `fix` o `refactor`).
- `git fetch origin <rama-base>` y crea el worktree desde ella:
  ```bash
  git worktree add -b <tipo>/<slug> .claude/worktrees/<slug> origin/<rama-base>
  ```
  Si ya existe para esa rama, reutilízalo y sigue desde la primera tarea sin marcar (`### [ ]`).
- `.claude/worktrees/` está en `.gitignore`; si no lo estuviera, avísalo en el informe.
- Si el plan no está commiteado en la rama base, cópialo al worktree y haz un primer commit `docs: añade el plan <slug>`.
- A partir de aquí **todo** se hace dentro del worktree. Comprueba que las rutas absolutas incluyen `.claude/worktrees/<slug>/` antes de editar.
- Genera el grafo del código en el worktree: `node scripts/graphify.mjs update .` (local, sin tokens; ADR 0010). Si Graphify no está instalado, sigue sin él.
- Cambia `**Status:**` del plan a `in progress`.

### 3. Implementar cada tarea con TDD estricto

En orden, de una en una, respetando `**Depends on:**`. No empieces una tarea sin haber commiteado la anterior.

1. **Rojo.** Escribe primero el test JUnit que expresa el criterio de la tarea. Ejecuta `./mvnw test -Dtest=<Clase>` y confirma que **falla por el motivo esperado** (aserción o clase inexistente), no por un error del propio test.
2. **Verde.** Escribe el código mínimo para que pase. Ejecuta `./mvnw verify`: deben pasar el test nuevo y **todos** los anteriores, incluidas las reglas de ArchUnit.
3. **Refactor.** Aplica las convenciones de `CLAUDE.md` (lenguaje ubicuo, accesores estilo record, uso permitido de Lombok/Immutables) sin cambiar el comportamiento, y repite `./mvnw verify`.
4. **Criterios.** Comprueba uno a uno los de la tarea y márcalos `- [x]` solo si se cumplen de verdad.

Casos especiales:

- **Tests de caracterización** o sobre código existente: pasarán a la primera. Demuestra que pueden fallar rompiendo temporalmente el código, viendo el rojo y revirtiendo. Indícalo en el resumen.
- **Reglas de ArchUnit nuevas:** valídalas introduciendo temporalmente la dependencia prohibida (rojo) y revirtiendo (verde).
- **Tareas sin comportamiento testeable** (docs, configuración, `pom.xml`): verifica sus criterios y ejecuta `./mvnw verify`.
- Si una tarea toca el dominio, ejecuta también `node .claude/skills/ddd-building-block/scripts/check-domain.mjs`.

### 4. Cerrar la tarea

1. En el plan del worktree, `### [ ] Tn` → `### [x] Tn`, con sus criterios marcados.
2. **Un commit por tarea** con código, tests y plan, usando `/commit` si está disponible (Conventional Commits en español). Cuerpo: `Plan task Tn: <título>` y `Refs #<número>`. Añade los archivos por nombre.
3. Al terminar todas las tareas, pide una revisión al agente `revisor-ddd` sobre `git diff origin/<rama-base>...HEAD` y corrige lo que sea un problema real (en una tarea extra `Tn+1 — Correcciones de la revisión`, si hace falta).

### 5. Si algo se tuerce

Si un test no se puede poner en verde sin salirte del plan o un criterio no se puede cumplir, **para** y pregunta con `AskUserQuestion` (ajustar el plan, cambiar el enfoque, saltar la tarea). No marques como hecho algo que no lo está. Nada de `--no-verify`, `git reset --hard` ni reescribir commits sin permiso.

### 6. Criterios globales

1. Comprueba cada criterio global: `./mvnw verify`; arranque con `./mvnw spring-boot:run` en segundo plano + `curl -s localhost:8080/actuator/health` (detén el proceso al acabar); `check-domain.mjs`; `git diff origin/<rama-base>... -- pom.xml` para las dependencias; OpenAPI si aplica.
2. Márcalos. Si alguno falla, trátalo como en el paso 5.
3. **Actualiza el estado del proyecto** (obligatorio: el CI falla si una PR con `Closes #n` no toca `docs/ia/estado-del-proyecto.md`):
   - `estado-del-proyecto.md`: la issue pasa a "Hecho" o a "En curso" con su PR, revisa el "Siguiente paso" y cambia la fecha de "Última actualización". Mantenlo corto: se lee en cada sesión, así que no le añadas detalle.
   - `docs/ia/decisiones.md`: decisiones nuevas que afectan a issues futuras.
   - `docs/ia/pendientes.md`: avisos de la revisión para issues futuras y desviaciones; quita los pendientes que la PR resuelve.
   - `docs/ia/historial.md`: una fila con la fecha, la PR, la issue y el cambio.
4. `**Status:**` → `done` y commit `docs: completa el plan <slug>` (incluye el estado del proyecto).

### 7. Crear la PR

1. `git push -u origin <tipo>/<slug>`.
2. Si ya hay PR para la rama (`gh pr view <tipo>/<slug> --json url`), reutilízala. Si no:
   ```bash
   gh pr create --base <rama-base> --head <tipo>/<slug> --title "<tipo>: <título del plan>" --body "<cuerpo>"
   ```
   Cuerpo en español: objetivo, tareas completadas, desviaciones, ruta del plan, resultado de la revisión `revisor-ddd` y `Closes #<número>`.
3. No crees la PR si la implementación se detuvo a medias o algún criterio global no se cumple.

### 8. Avisar por Telegram

```bash
node scripts/notify.mjs "✅ PR lista para revisar: <título> · <URL de la PR> · #<número> · <n>/<n> tareas"
```

No avises si no hay PR. Si falla o no está configurado, menciónalo en el informe.

### 9. Informar

Issue, rama, worktree, URL de la PR, `git log --oneline origin/<rama-base>..HEAD`, tareas y criterios (con desviaciones y validaciones por sabotaje) y si se avisó por Telegram. No hagas merge ni borres el worktree: eso lo decide el usuario.
