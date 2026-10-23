---
description: Crea un commit siguiendo el formato Conventional Commits
argument-hint: [pista opcional sobre el tipo o alcance del cambio]
allowed-tools: Bash(git rev-parse:*), Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git branch:*), Bash(git check-ignore:*), Bash(git add:*), Bash(git commit:*)
---

Crea un commit de git con el formato [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/).

## Contexto

Cada comando termina en `|| true` para que un fallo llegue como texto y no aborte el comando.

- Rama actual: !`git branch --show-current 2>&1 || true`
- Estado: !`git status --short 2>&1 || true`
- Cambios en staging: !`git diff --cached 2>&1 || true`
- Cambios sin stage: !`git diff 2>&1 || true`
- Commits recientes (para ver el estilo): !`git log --oneline -10 2>&1 || true`

Pista del usuario (puede estar vacía): $ARGUMENTS

## Casos especiales

Compruébalos en este orden, antes de redactar nada.

1. **Rama `master`**: está congelada. No commitees: explícaselo al usuario y detente.
2. **Sin cambios** (ni en staging, ni sin stage, ni archivos sin seguimiento): díselo al usuario y no hagas nada más.
3. **Archivos que no deben entrar**: comprueba con `git check-ignore` que `target/`, `.env` y `.claude/settings.local.json` están ignorados. Si alguno no lo está, avisa y no lo incluyas.
4. **Fallo de `git commit`** por falta de identidad: indícale que configure `git config user.name` / `user.email`. No lo configures tú.

## Formato

```
<tipo>(<alcance opcional>): <descripción>

[cuerpo opcional]

[pie opcional]
```

Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.

Alcances habituales: la capa o paquete afectado (`domain`, `application`, `persistence`, `web`, `rest`, `adapter`, `config`, `arch`, `claude`, `docs`).

## Reglas

1. Si hay cambios en staging, commitea solo esos. Si no, añade con `git add` los archivos relevantes por nombre (nunca `git add -A` ni `git add .`).
2. Si los cambios mezclan propósitos independientes, propón dividirlos en varios commits.
3. La descripción va en español, en imperativo, en minúscula, sin punto final y con un máximo de ~72 caracteres.
4. Muestra el mensaje con `AskUserQuestion` y espera su conformidad. Si no le convence, propón otro.
5. Añade cuerpo solo si el *porqué* no es evidente. Si el commit cierra una tarea de un plan, el cuerpo es `Plan task Tn: <título>` y `Refs #<issue>`.
6. Cambio incompatible: `!` tras el tipo y pie `BREAKING CHANGE: ...`.
7. Usa `git commit -m` (varios `-m` para cuerpo y pies). Si hay una instrucción de atribución vigente para commits, añádela como último pie.
8. No hagas `git push`, no uses `--amend` ni `--no-verify`.
9. Al terminar, muestra el hash y el mensaje del commit creado.
