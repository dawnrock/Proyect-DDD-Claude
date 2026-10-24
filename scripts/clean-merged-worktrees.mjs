#!/usr/bin/env node
// Borra los worktrees de los teammates (.claude/worktrees/agent-*) cuya rama ya está fusionada en la
// rama de una fase (origin/Monolithic u origin/Hexagonal) y que no tienen cambios sin commitear, junto
// con sus ramas locales y las ramas worktree-agent-* que quedan huérfanas.
//
// Uso:
//   node scripts/clean-merged-worktrees.mjs --dry-run   # solo lista lo que borraría
//   node scripts/clean-merged-worktrees.mjs             # borra
//   node scripts/clean-merged-worktrees.mjs --hook      # modo hook PostToolUse: solo actúa tras `gh pr merge`
//
// Nunca toca: el checkout principal, el worktree desde el que se ejecuta, los worktrees de sesiones
// (fuera de agent-*), los bloqueados ni los que tienen cambios sin commitear o ficheros sin seguimiento.
// Solo reconoce fusiones con commit de merge (las que usa este repositorio), no squash ni rebase.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, realpathSync, rmSync } from 'node:fs';
import { basename, resolve } from 'node:path';

const BASE_BRANCHES = ['Monolithic', 'Hexagonal'];
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

  // Ramas de los worktrees borrados y ramas worktree-agent-* que ya no usa ningún worktree.
  const checkedOut = new Set(worktrees().map((w) => w.branch).filter(Boolean));
  const orphans = git(['branch', '--list', 'worktree-agent-*', '--format=%(refname:short)']).split(/\r?\n/).filter(Boolean);
  for (const branch of new Set([...removedBranches, ...orphans])) {
    if (!dryRun && checkedOut.has(branch)) continue;
    const base = mergedInto(branch, bases);
    if (!base) { report.push(`- rama ${branch}: sin fusionar, se conserva`); continue; }
    if (dryRun) { report.push(`- rama ${branch} (fusionada en ${base}): se borraría`); continue; }
    // -D: la comprobación de fusión ya está hecha contra la rama de la fase, no contra HEAD.
    const deleted = tryGit(['branch', '-D', branch]);
    report.push(`- rama ${branch}: ${deleted.ok ? 'borrada' : `NO borrada (${deleted.out})`}`);
  }

  return report.length ? report : ['No hay worktrees de teammates fusionados que limpiar.'];
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
