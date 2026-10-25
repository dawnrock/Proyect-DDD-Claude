#!/usr/bin/env node
// Borra lo que ya no sirve tras fusionar una PR en la rama de una fase (origin/Monolithic u
// origin/Hexagonal):
//   1. Los worktrees de los teammates (.claude/worktrees/agent-*) cuya rama está fusionada y que no tienen
//      cambios sin commitear.
//   2. Todas las ramas locales fusionadas que no estén en uso en ningún worktree (las de los teammates y
//      las demás: feat/*, docs/*, chore/*...). Borrar una rama fusionada no pierde ningún commit.
//
// Uso:
//   node scripts/clean-merged-worktrees.mjs --dry-run   # solo lista lo que borraría
//   node scripts/clean-merged-worktrees.mjs             # borra
//   node scripts/clean-merged-worktrees.mjs --hook      # modo hook PostToolUse: solo actúa tras `gh pr merge`
//
// Nunca toca: el checkout principal, el worktree desde el que se ejecuta, los worktrees de sesiones
// (fuera de agent-*), los bloqueados ni los que tienen cambios sin commitear o ficheros sin seguimiento.
// Nunca borra: las ramas de fase y master, las que están en uso en algún worktree, ni las claude/*, que
// crea la app de Claude para sus sesiones y puede volver a usar al reanudar una.
// Solo reconoce fusiones con commit de merge (las que usa este repositorio), no squash ni rebase.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, realpathSync, rmSync } from 'node:fs';
import { basename, resolve } from 'node:path';

const BASE_BRANCHES = ['Monolithic', 'Hexagonal'];
const PROTECTED = new Set([...BASE_BRANCHES, 'master', 'main']);
const args = new Set(process.argv.slice(2));
const dryRun = args.has('--dry-run') || args.has('-n');
const hook = args.has('--hook');

function git(gitArgs, cwd) {
  return execFileSync('git', gitArgs, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}

function tryGit(gitArgs, cwd) {
  try { return { ok: true, out: git(gitArgs, cwd) }; } catch (e) { return { ok: false, out: String(e.stderr || e.message).trim() }; }
}

function samePath(a, b) {
  const norm = (p) => { try { return realpathSync(p); } catch { return resolve(p); } };
  return norm(a).toLowerCase() === norm(b).toLowerCase();
}

function worktrees() {
  const list = [];
  let current = null;
  for (const line of git(['worktree', 'list', '--porcelain']).split(/\r?\n/)) {
    if (line.startsWith('worktree ')) { current = { path: line.slice(9) }; list.push(current); }
    else if (line.startsWith('HEAD ')) current.head = line.slice(5);
    else if (line.startsWith('branch ')) current.branch = line.slice(7).replace('refs/heads/', '');
    else if (line.startsWith('locked')) current.locked = true;
  }
  return list;
}

function mergedInto(commit, bases) {
  return bases.find((base) => tryGit(['merge-base', '--is-ancestor', commit, `origin/${base}`]).ok);
}

function clean() {
  tryGit(['fetch', '--quiet', 'origin', ...BASE_BRANCHES]);
  const bases = BASE_BRANCHES.filter((b) => tryGit(['rev-parse', '--verify', '--quiet', `origin/${b}`]).ok);
  if (bases.length === 0) return ['No hay ninguna rama de fase en origin: no se borra nada.'];

  const here = git(['rev-parse', '--show-toplevel']);
  const [main, ...others] = worktrees();
  const report = [];
  const removedBranches = new Set();

  for (const wt of others) {
    const name = basename(wt.path);
    if (!name.startsWith('agent-') || samePath(wt.path, here) || samePath(wt.path, main.path)) continue;
    const base = mergedInto(wt.head, bases);
    if (wt.locked) { report.push(`- ${name}: bloqueado, se conserva`); continue; }
    if (!base) { report.push(`- ${name} (${wt.branch ?? 'HEAD suelto'}): sin fusionar, se conserva`); continue; }
    const status = tryGit(['status', '--porcelain'], wt.path);
    if (!status.ok || status.out) { report.push(`- ${name}: tiene cambios sin commitear, se conserva`); continue; }

    if (dryRun) {
      report.push(`- ${name} (${wt.branch ?? 'HEAD suelto'}, fusionada en ${base}): se borraría`);
    } else {
      const removed = tryGit(['worktree', 'remove', wt.path]);
      if (!removed.ok && existsSync(wt.path)) {
        // OneDrive puede bloquear la carpeta: se borra a mano y se limpia el registro de git.
        try { rmSync(wt.path, { recursive: true, force: true }); } catch { /* se informa abajo */ }
        tryGit(['worktree', 'prune']);
      }
      report.push(`- ${name} (${wt.branch ?? 'HEAD suelto'}, fusionada en ${base}): ${existsSync(wt.path) ? 'NO se pudo borrar la carpeta' : 'borrado'}`);
    }
    if (wt.branch) removedBranches.add(wt.branch);
  }

  // Ramas locales: las de los worktrees borrados y cualquier otra que ya no use ningún worktree. En modo
  // prueba los worktrees siguen ahí, así que sus ramas cuentan como libres.
  const checkedOut = new Set(worktrees().map((w) => w.branch).filter(Boolean));
  const local = git(['branch', '--format=%(refname:short)']).split(/\r?\n/).filter(Boolean);
  for (const branch of local) {
    if (PROTECTED.has(branch) || branch.startsWith('claude/')) continue;
    if (checkedOut.has(branch) && !(dryRun && removedBranches.has(branch))) continue;
    const base = mergedInto(branch, bases);
    if (!base) { report.push(`- rama ${branch}: sin fusionar, se conserva`); continue; }
    if (dryRun) { report.push(`- rama ${branch} (fusionada en ${base}): se borraría`); continue; }
    // -D: la comprobación de fusión ya está hecha contra la rama de la fase, no contra HEAD.
    const deleted = tryGit(['branch', '-D', branch]);
    report.push(`- rama ${branch}: ${deleted.ok ? 'borrada' : `NO borrada (${deleted.out})`}`);
  }

  return report.length ? report : ['No hay worktrees ni ramas fusionados que limpiar.'];
}

if (hook) {
  // PostToolUse: solo tras un `gh pr merge`. Si la fusión falló, la rama no está fusionada y no se borra
  // nada. Nunca bloquea la sesión.
  let input = {};
  try { input = JSON.parse(readFileSync(0, 'utf8') || '{}'); } catch { process.exit(0); }
  const command = input.tool_input?.command ?? '';
  if (!/\bgh\s+pr\s+merge\b/.test(command)) process.exit(0);
  try {
    console.log(`Limpieza de worktrees tras la fusión:\n${clean().join('\n')}`);
  } catch (e) {
    console.log(`Limpieza de worktrees no completada: ${e.message}`);
  }
  process.exit(0);
}

console.log(`${dryRun ? 'Modo prueba (no se borra nada):\n' : ''}${clean().join('\n')}`);
