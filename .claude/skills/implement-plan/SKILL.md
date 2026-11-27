---
name: implement-plan
description: Implementa el plan de una issue de GitHub (generado con plan-feature) en un git worktree aislado (o, en las sesiones en la nube, en la rama que impone el entorno), tarea a tarea, con TDD estricto (JUnit + ArchUnit, ./mvnw). Cada tarea terminada se commitea y se marca en el plan; al cumplir los criterios globales abre una PR contra la rama de la fase (Monolithic o Hexagonal, nunca master). Úsala cuando se pida implementar, ejecutar o llevar a cabo un plan o una issue.
argument-hint: <número de issue de GitHub>
allowed-tools: Read, Grep, Glob, Edit, Write, AskUserQuestion, Bash(git:*), Bash(./mvnw:*), Bash(mvnw.cmd:*), Bash(node:*), Bash(npx @redocly/cli:*), Bash(curl:*), Bash(ls:*), Bash(cp:*), Bash(gh issue view:*), Bash(gh pr create:*), Bash(gh pr view:*), Bash(gh pr checks:*), Bash(gh pr merge:*)
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
  2. Si no está, el comentario más reciente de la issue que empiece por `# Plan:`; guárdalo en `docs/plans/AAAA-MM-DD-<slug>.md` (con la regla de `plan-feature`: solo quedan los 5 planes más recientes).
  3. Si no hay plan, detente y sugiere `/plan-feature <número>`.
- Lee el plan completo, `CLAUDE.md` y `src/test/CLAUDE.md`. Resuelve con el usuario las preguntas abiertas que bloqueen alguna tarea **antes** de crear el worktree.

### 2. Crear el git worktree

> **¿El entorno impone una rama de desarrollo?** En las sesiones de Claude Code en la nube, las instrucciones del entorno nombran una rama (`claude/<nombre>`) en la que hay que desarrollar y a la que se limita el push. Entonces no se sigue este paso, sino el **2 bis**. En local (sin rama impuesta) se sigue este paso tal cual.

- Slug = nombre del plan sin fecha ni `.md`. Rama `<tipo>/<slug>` (`feat`, `fix` o `refactor`).
- `git fetch origin <rama-base>` y crea el worktree desde ella:
  ```bash
  git worktree add -b <tipo>/<slug> .claude/worktrees/<slug> origin/<rama-base>
  ```
  Si ya existe para esa rama, reutilízalo y sigue desde la primera tarea sin marcar (`### [ ]`).
- `.claude/worktrees/` está en `.gitignore`; si no lo estuviera, avísalo en el informe.
- Si el plan no está commiteado en la rama base, cópialo al worktree y haz un primer commit `docs: añade el plan <slug>`.
- A partir de aquí **todo** se hace dentro del worktree. Comprueba que las rutas absolutas incluyen `.claude/worktrees/<slug>/` antes de editar.
- Cambia `**Status:**` del plan a `in progress`.

### 2 bis. Rama impuesta por el entorno (sesiones en la nube)

Si el entorno impone una rama de desarrollo, se trabaja **en esa rama, sin worktree**: una rama `<tipo>/<slug>` no se podría subir. Todo lo demás del flujo (TDD, un commit por tarea, `revisor-ddd`, criterios globales, estado del proyecto, PR y fusión con los checks en verde) es igual.

- `git fetch origin <rama-base>` y comprueba que la rama impuesta no lleva trabajo sin fusionar: `git log --oneline origin/<rama-base>..HEAD`.
  - Vacío, o solo commits ya contenidos en la rama base (PR anterior fusionada): reiníciala desde la base con `git checkout -B <rama-impuesta> origin/<rama-base>`.
  - Commits de esta misma issue (por ejemplo, su plan): síguela sin reiniciar y continúa desde la primera tarea sin marcar.
  - Commits de otro trabajo sin fusionar: **para** y pregunta con `AskUserQuestion`; no mezcles dos issues en la misma PR.
- Trabaja en el checkout principal. El plan se commitea en la rama como en el paso 2 y `**Status:**` pasa a `in progress`.
- Sube con `git push -u origin <rama-impuesta>`; nunca a otra rama.
- La PR (paso 7) se abre **desde la rama impuesta contra la rama de la fase** (`Monolithic` o `Hexagonal`, nunca `master`). No es una desviación del plan: no hace falta justificarla en la PR.
- Tras la fusión (paso 8) no hay worktree que borrar. GitHub puede borrar la rama remota al fusionar; la local se reinicia desde la base ya actualizada (`git fetch origin <rama-base> && git checkout -B <rama-impuesta> origin/<rama-base>`) y se sube si el entorno pide que no queden commits sin subir. Así la siguiente issue parte de la base fusionada.

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

### 4. Cerrar la tarea

1. En el plan del worktree, `### [ ] Tn` → `### [x] Tn`, con sus criterios marcados.
2. **Un commit por tarea** con código, tests y plan, usando `/commit` si está disponible (Conventional Commits en español). Cuerpo: `Plan task Tn: <título>` y `Refs #<número>`. Añade los archivos por nombre.
3. Al terminar todas las tareas, pide una revisión al agente `revisor-ddd` sobre `git diff origin/<rama-base>...HEAD` y corrige lo que sea un problema real (en una tarea extra `Tn+1 — Correcciones de la revisión`, si hace falta).

### 5. Si algo se tuerce

Si un test no se puede poner en verde sin salirte del plan o un criterio no se puede cumplir, **para** y pregunta con `AskUserQuestion` (ajustar el plan, cambiar el enfoque, saltar la tarea). No marques como hecho algo que no lo está. Nada de `--no-verify`, `git reset --hard` ni reescribir commits sin permiso.

### 6. Criterios globales

1. Comprueba cada criterio global: `./mvnw verify`; arranque con `./mvnw spring-boot:run` en segundo plano + `curl -s localhost:8080/actuator/health` (detén el proceso al acabar); `git diff origin/<rama-base>... -- pom.xml` para las dependencias; OpenAPI si aplica y, si el plan toca la API, `node scripts/contract-test.mjs` (pruebas de contrato con Schemathesis, ADR 0013; si no está instalado: `pip install schemathesis==4.33.4`).
2. Márcalos. Si alguno falla, trátalo como en el paso 5.
3. **Actualiza el estado del proyecto** (obligatorio: el CI falla si una PR con `Closes #n` no toca `docs/ia/estado-del-proyecto.md`):
   - `estado-del-proyecto.md`: escribe el estado como quedará tras fusionar la PR: la issue en "Done" con su número de PR y el "Next step" apuntando a la próxima issue; cambia la fecha de "Last updated". Mantenlo corto: se lee en cada sesión, así que no le añadas detalle.
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

### 7 bis. Fusión automática con los checks en verde

Decisión del usuario (2026-11-17): **cuando el usuario pide una PR** (al lanzar `/implement-plan`, `/implement-plan-team` o `/advance-issues`, o al pedir expresamente "haz/abre una PR"), la fusionas tú en cuanto sus checks pasen en verde, sin volver a preguntar. Una PR que abras por tu cuenta, sin que él la pida, no se fusiona sin su "sí".

1. Al abrirla, suscríbete a su actividad (o, sin esa herramienta, vuelve a mirar los checks cuando acaben) y espera a que terminen. No hagas sondeo con `sleep`.
2. Fusiona solo si se cumple **todo**:
   - todos los checks del commit de cabeza han terminado con `success` (`verify`, `claude-review` y los que haya);
   - la revisión automática no deja hallazgos bloqueantes sin resolver;
   - no hay conflictos con la rama base;
   - la base es `Hexagonal` o, si la issue es una excepción autorizada, `Monolithic`; **nunca `master`**.
3. Fusiona igual que el botón **Merge pull request** de GitHub con su opción por defecto, **Create a merge commit** (no «Squash and merge» ni «Rebase and merge»), fijando la cabeza esperada para no fusionar un commit que no has visto en verde: `gh pr merge <n> --merge --match-head-commit <sha>` (o `expectedHeadSha` con el SHA completo en la herramienta de GitHub).
4. Si un check falla o hay un hallazgo bloqueante, **no fusiones**: arregla la causa en la misma rama, sube el arreglo y vuelve a esperar. Nunca desactives un test para ponerlo en verde.
5. No fusiones si el usuario dice que la deje abierta, si la PR es un borrador o si en el informe quedó algo pendiente de su decisión.
6. Después de fusionar: deja de seguir la PR y borra sus ramas y worktrees como en el paso 8.

### 8. Informar

Issue, rama, worktree (o rama impuesta, paso 2 bis), URL de la PR, `git log --oneline origin/<rama-base>..HEAD`, tareas y criterios (con desviaciones y validaciones por sabotaje) y si la PR se fusionó (paso 7 bis) o qué la frena. Una vez fusionada (por ti con los checks en verde o por el usuario), borra el worktree y las ramas de la PR, local y remota, tras comprobar que están contenidas en la rama base (`git merge-base --is-ancestor`); nunca `master`, `Monolithic` ni `Hexagonal`. Con rama impuesta por el entorno, en vez de borrarla se reinicia desde la base (paso 2 bis).
