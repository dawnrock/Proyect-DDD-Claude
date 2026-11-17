# scripts/

Scripts deterministas que usan las skills, los hooks y el CI. Solo necesitan Node 18+ (sin dependencias npm), `gh` para las issues y, para `contract-test.mjs`, Java 21 y Schemathesis.

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

Lo ejecuta el CI en cada PR: si el cuerpo cierra una issue (`Closes #n`, `Fixes #n`…) y la PR no modifica `docs/ia/estado-del-proyecto.md`, falla. Sus anexos (`decisiones.md`, `pendientes.md`, `historial.md`) se actualizan según el paso 6.3 de `implement-plan`, pero el CI solo comprueba el estado.

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

## graphify.mjs

Lanzador portable de [Graphify](https://pypi.org/project/graphifyy/) (ADR 0010). El grafo del código Java vive en `graphify-out/` (no versionado, uno por worktree) y se usa solo para preguntas de dependencias.

```bash
node scripts/graphify.mjs update .                                  # regenera el grafo (local, sin tokens)
node scripts/graphify.mjs affected "MoneyTransferService"           # quién depende de una clase
node scripts/graphify.mjs path "TransferController" "CheckingAccount" # cómo se relacionan dos clases
```

Busca `graphify` en el PATH y, si no está, `py -m graphify` (Windows) o `python3 -m graphify`. Fuerza `GRAPHIFY_BACKEND=none`. Códigos de salida: 0 con respuesta, 3 si el nombre es ambiguo (lista los candidatos), 4 si no hay nodo y 1 si Graphify no está instalado (quien lo llama sigue con Grep). Instalación: `py -m pip install --user graphifyy` o `pip install graphifyy`.

## contract-test.mjs

Pruebas de contrato (#107, [ADR 0013](../docs/adr/0013-contract-tests-schemathesis.md)): arranca el jar de `target/` con el perfil `demo`, espera a `/actuator/health`, ejecuta [Schemathesis](https://schemathesis.readthedocs.io/) contra `docs/api/openapi.yaml` con la configuración de `schemathesis.toml` y detiene la aplicación. Sale con el código de Schemathesis; lo ejecuta el CI tras `./mvnw verify`.

Requisitos: Java 21, el jar construido (`./mvnw verify` o `./mvnw package`) y Schemathesis (`pip install schemathesis==4.33.4`, Python 3.10+).

```bash
node scripts/contract-test.mjs
CONTRACT_TEST_PORT=18080 SCHEMATHESIS=~/.venvs/st/bin/schemathesis node scripts/contract-test.mjs
```

| Variable | Por defecto | Descripción |
| -------- | ----------- | ----------- |
| `CONTRACT_TEST_PORT` | `8080` | Puerto en el que arranca la aplicación |
| `SCHEMATHESIS` | `schemathesis` | Ejecutable de Schemathesis (por ejemplo, el de un entorno virtual) |

## check-contract-version.mjs

Versionado del contrato (#112, [ADR 0014](../docs/adr/0014-contract-versioning.md)). Lo ejecuta el CI en cada PR: compara `docs/api/openapi.yaml` con el de la rama base. Si cambia, `info.version` debe subir; si [oasdiff](https://github.com/oasdiff/oasdiff) encuentra cambios incompatibles, debe subir la versión mayor. Sale con 1 si se incumple una regla y con 2 si falta algo para comprobarlo.

Requisitos: `git` con la rama base descargada y oasdiff (`go install github.com/oasdiff/oasdiff@v1.35.0`, Go 1.26).

```bash
BASE_REF=Hexagonal node scripts/check-contract-version.mjs
OASDIFF=~/go/bin/oasdiff node scripts/check-contract-version.mjs base.yaml nuevo.yaml   # dos ficheros, sin git
```
