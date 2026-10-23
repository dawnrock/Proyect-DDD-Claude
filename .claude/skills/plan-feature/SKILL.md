---
name: plan-feature
description: Planifica un desarrollo, un fix o un refactor descrito en una issue de GitHub antes de escribir código. Lee la issue por su número, analiza el código Java/Spring de la fase activa (capas o hexagonal), genera un plan con objetivo, tareas pequeñas (5-10 min) y criterios de aceptación siguiendo assets/template.md, lo guarda en docs/plans/, lo publica como comentario en la issue y avisa por Telegram. Úsala cuando se pida planificar, diseñar o desglosar una issue.
argument-hint: <número de issue de GitHub>
allowed-tools: Read, Grep, Glob, Write, AskUserQuestion, Bash(git status:*), Bash(git log:*), Bash(git diff:*), Bash(git branch:*), Bash(ls:*), Bash(gh issue view:*), Bash(gh issue comment:*), Bash(node scripts/notify.mjs:*)
---

Planifica el trabajo descrito en la issue. **No implementes nada**: el resultado de esta skill es solo el plan.

Issue indicada por el usuario: $ARGUMENTS

## Pasos

### 1. Leer la issue

- Acepta `42`, `#42` o la URL. Si no hay número, pídelo con `AskUserQuestion`; no planifiques sin issue.
- `gh issue view <número> --json number,title,body,labels,milestone,state,url,comments`
- Si no existe o `gh` falla, detente y explícalo. Si está cerrada, pregunta si se planifica igualmente.
- Determina la **fase** por la etiqueta `fase:1-monolito` / `fase:2-hexagonal` (o el milestone) y de ahí la **rama base**: `Monolithic` o `Hexagonal`. Si la rama actual no es la de la fase, avísalo: el análisis debe hacerse sobre el código de esa rama.

### 2. Entender la petición

- Clasifícala como `feature`, `fix` o `refactor`.
- Si es ambigua en algo que cambie el plan, pregunta con `AskUserQuestion`. No preguntes lo que puedas averiguar leyendo el código.

### 3. Analizar el código

- Relee `CLAUDE.md`: arquitectura de la fase, convenciones, tabla de Lombok/Immutables.
- Para dudas sobre bloques tácticos (value object, entidad, agregado, evento, factoría, servicio), lee `.claude/skills/ddd-building-block/references/reglas-ddd.md`.
- En la fase 2, para puertos y adaptadores lee `.claude/skills/hexagonal-adapter/SKILL.md`.
- Localiza y lee los archivos afectados. Delega búsquedas amplias en el agente `buscador-referencias`.
- Determina si el trabajo **toca la API REST** (rutas, cuerpos, códigos de estado, traducción de errores). Si es así, incluye la actualización de `docs/api/openapi.yaml`.

### 4. Desglosar en tareas

Cada tarea debe poder hacerse en **5-10 minutos**:

- Una tarea toca, como norma, **una sola capa** y uno o dos archivos (más su test).
- Cada tarea deja el proyecto con `./mvnw verify` en verde.
- Ordena de dentro hacia fuera: `domain` → `application` → `persistence`/`adapter.out` → `presentation`/`adapter.in` → `docs`. Deja explícitas las dependencias.
- `fix`: la primera tarea es un test que reproduce el error. `refactor`: la primera fija el comportamiento actual (tests de caracterización).
- Todo comportamiento nuevo lleva su test JUnit en la misma tarea (TDD, ver `src/test/CLAUDE.md`).
- Si cambia la arquitectura, incluye la tarea de la regla de ArchUnit y su validación rompiéndola.
- Si toca la API, añade una tarea de capa `docs` tras la última de REST: actualizar `docs/api/openapi.yaml` y validarlo con `npx @redocly/cli lint docs/api/openapi.yaml`.

### 5. Criterios de aceptación

- **Por tarea**: 1-3 criterios comprobables (un test que pasa, una petición `curl` con su código esperado, una regla de ArchUnit).
- **Globales**: los específicos más **todos** los fijos de la plantilla. Marca como "no aplica" los que no correspondan (por ejemplo, OpenAPI si no hay API).

### 6. Escribir el plan

- Rellena [template.md](assets/template.md) respetando sus secciones y quita los comentarios `<!-- -->`.
- Guárdalo en `docs/plans/AAAA-MM-DD-<slug>.md` (slug en inglés, kebab-case).
- Cabecera: issue con número y URL, fase y rama base.
- Plan en español; nombres de archivos, clases y código en inglés.

### 7. Publicarlo en la issue

```bash
gh issue comment <número> --body-file docs/plans/AAAA-MM-DD-<slug>.md
```

Guarda la URL del comentario. Si falla, no bloquees: el plan queda en local; menciónalo.

### 8. Avisar por Telegram

```bash
node scripts/notify.mjs "📝 Plan listo para revisar: <título> (#<número>, <tipo>) · <n> tareas (~<min> min) · <URL del comentario>"
```

Si responde que no está configurado o falla, continúa y menciónalo en el resumen.

### 9. Presentarlo

Resume objetivo, tareas con estimación, ruta del archivo, URL del comentario, si se avisó por Telegram y las preguntas abiertas. Pregunta si quiere ajustar algo o implementarlo con `/implement-plan <número>`. No empieces a implementar sin su confirmación.
