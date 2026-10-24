# scripts/

Scripts deterministas que usan las skills y los hooks. Solo necesitan Node 16+ (sin dependencias npm) y `gh` para las issues.

## seed-issues.mjs

Crea las etiquetas, el milestone y las issues de una fase a partir de un JSON, con [GitHub CLI](https://cli.github.com/) autenticado (`gh auth login`).

```bash
node scripts/seed-issues.mjs -f scripts/issues-fase-1.json --dry-run   # revisar sin crear nada
node scripts/seed-issues.mjs -f scripts/issues-fase-1.json             # crear en el repo actual
node scripts/seed-issues.mjs -f scripts/issues-fase-2.json -R dawnrock/Proyect-DDD-Claude
```

| Opción | Descripción |
| ------ | ----------- |
| `-f`, `--file` | JSON de la fase (obligatorio) |
| `-R`, `--repo` | `owner/repo` (por defecto, el del directorio actual) |
| `-n`, `--dry-run` | Muestra lo que se crearía sin llamar a GitHub |

Es idempotente: no duplica etiquetas, milestones ni issues con el mismo título.

Los JSON son solo la **semilla** del backlog: una vez creadas, la fuente de verdad son las issues de GitHub. Por eso un hook (`.claude/hooks/block-backlog-json.mjs`) impide que Claude los lea.

## check-project-status.mjs

Lo ejecuta el CI en cada PR: si el cuerpo cierra una issue (`Closes #n`, `Fixes #n`…) y la PR no modifica `docs/ia/estado-del-proyecto.md`, falla.

```bash
git diff --name-only origin/Monolithic...HEAD | PR_BODY="Closes #5" node scripts/check-project-status.mjs -
```

## notify.mjs

Envía un mensaje push a Telegram. Lo usan `plan-feature`, `implement-plan` y el hook `notify-telegram.mjs`. Configuración en [docs/ia/notificaciones.md](../docs/ia/notificaciones.md).

```bash
node scripts/notify.mjs "Prueba desde Proyect-DDD-Claude"
```

## clean-merged-worktrees.mjs

Limpia lo que ya no sirve tras fusionar una PR en `origin/Monolithic` u `origin/Hexagonal`:

- los worktrees que dejan los teammates de `implement-plan-team` (`.claude/worktrees/agent-*`) cuya rama está fusionada y que no tienen cambios sin commitear;
- todas las ramas locales fusionadas que no estén en uso en ningún worktree, de teammates o no (`feat/*`, `docs/*`, `chore/*`...). Borrar una rama fusionada no pierde ningún commit.

```bash
node scripts/clean-merged-worktrees.mjs --dry-run   # lista lo que borraría
node scripts/clean-merged-worktrees.mjs             # borra
```

Se ejecuta solo tras cada `gh pr merge` con el hook `PostToolUse` de `.claude/settings.json` (`--hook`). Nunca toca el checkout principal, el worktree desde el que se ejecuta, los worktrees de sesiones (fuera de `agent-*`), los bloqueados ni los que tienen trabajo sin commitear. Nunca borra las ramas de fase, `master`, las ramas en uso ni las `claude/*`, que crea la app de Claude para sus sesiones. Solo reconoce fusiones con commit de merge, las que usa este repositorio. Si OneDrive bloquea una carpeta, la borra a mano y ejecuta `git worktree prune`.
