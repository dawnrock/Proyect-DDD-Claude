# scripts/

Scripts deterministas que usan las skills y los hooks. Solo necesitan Node 18+ (y `gh` para las issues).

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

## notify.mjs

Envía un mensaje push a Telegram. Lo usan `plan-feature`, `implement-plan` y el hook `notify-telegram.mjs`. Configuración en [docs/ia/notificaciones.md](../docs/ia/notificaciones.md).

```bash
node scripts/notify.mjs "Prueba desde Proyect-DDD-Claude"
```
