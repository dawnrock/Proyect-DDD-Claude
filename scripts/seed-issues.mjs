#!/usr/bin/env node
// Crea etiquetas, milestones e issues de GitHub a partir de un JSON, usando GitHub CLI (gh).
//
// Uso:
//   node scripts/seed-issues.mjs -f scripts/issues-fase-1.json [-R owner/repo] [-n]
//
// Opciones:
//   -f, --file     JSON de issues (obligatorio)
//   -R, --repo     Repositorio owner/repo (por defecto, el del directorio actual)
//   -n, --dry-run  Muestra lo que se crearía sin llamar a GitHub
//   -h, --help     Ayuda
//
// Formato del JSON:
//   {
//     "milestone": { "title": "Fase 1 · Monolito en capas", "description": "..." },
//     "phaseLabel": "fase:1-monolito",
//     "issues": [
//       { "title": "...", "type": "feature|refactor|docs|chore|build|test",
//         "layers": ["domain", "application"], "body": "Markdown" }
//     ]
//   }
// Cada issue recibe las etiquetas tipo:<type>, capa:<layer> y la de fase, y el milestone.
// Es idempotente: si ya existe una issue con el mismo título en el repo, no la duplica.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const args = process.argv.slice(2);
let file, repo, dryRun = false;
for (let i = 0; i < args.length; i++) {
  const a = args[i];
  if (a === '-f' || a === '--file') file = args[++i];
  else if (a === '-R' || a === '--repo') repo = args[++i];
  else if (a === '-n' || a === '--dry-run') dryRun = true;
  else if (a === '-h' || a === '--help') { help(); process.exit(0); }
  else { console.error(`Opción desconocida: ${a}`); help(); process.exit(1); }
}
if (!file) { help(); process.exit(1); }

function help() {
  console.log(readFileSync(new URL(import.meta.url), 'utf8').split('\n').slice(1, 12)
    .map((l) => l.replace(/^\/\/ ?/, '')).join('\n'));
}

const spec = JSON.parse(readFileSync(file, 'utf8'));
const repoArgs = repo ? ['--repo', repo] : [];
const gh = (...a) => execFileSync('gh', a, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
const ghApi = (path, ...a) => gh('api', repo ? path.replace(':repo', repo) : path.replace(':repo', '{owner}/{repo}'), ...a);

const COLORS = {
  'fase:1-monolito': '1d76db', 'fase:2-hexagonal': '5319e7',
  'tipo:feature': '84b6eb', 'tipo:refactor': 'fbca04', 'tipo:docs': '0e8a16',
  'tipo:chore': 'c5def5', 'tipo:build': 'bfd4f2', 'tipo:test': 'd4c5f9',
};
const color = (l) => COLORS[l] ?? (l.startsWith('capa:') ? 'ededed' : 'cccccc');

if (!dryRun) {
  try { gh('auth', 'status'); } catch { console.error("No has iniciado sesión en gh. Ejecuta 'gh auth login'."); process.exit(1); }
}

// Etiquetas
const labels = new Set([spec.phaseLabel]);
for (const it of spec.issues) {
  labels.add(`tipo:${it.type}`);
  for (const l of it.layers ?? []) labels.add(`capa:${l}`);
}
const existingLabels = dryRun ? [] : JSON.parse(gh('label', 'list', '--limit', '200', '--json', 'name', ...repoArgs)).map((l) => l.name);
for (const l of labels) {
  if (existingLabels.includes(l)) continue;
  console.log(`Etiqueta: ${l}`);
  if (!dryRun) gh('label', 'create', l, '--color', color(l), ...repoArgs);
}

// Milestone
const ms = spec.milestone.title;
if (!dryRun) {
  const existing = JSON.parse(ghApi('repos/:repo/milestones?state=all&per_page=100')).map((m) => m.title);
  if (!existing.includes(ms)) {
    console.log(`Milestone: ${ms}`);
    ghApi('repos/:repo/milestones', '-f', `title=${ms}`, '-f', `description=${spec.milestone.description ?? ''}`);
  }
} else {
  console.log(`Milestone: ${ms}`);
}

// Issues
const existingTitles = dryRun ? [] : JSON.parse(gh('issue', 'list', '--state', 'all', '--limit', '500', '--json', 'title', ...repoArgs)).map((i) => i.title);
const tmp = mkdtempSync(join(tmpdir(), 'seed-issues-'));
try {
  spec.issues.forEach((it, idx) => {
    const itLabels = [spec.phaseLabel, `tipo:${it.type}`, ...(it.layers ?? []).map((l) => `capa:${l}`)];
    console.log(`[${idx + 1}/${spec.issues.length}] ${it.title} (${itLabels.join(', ')})`);
    if (dryRun) return;
    if (existingTitles.includes(it.title)) { console.log('   ya existe, se omite'); return; }
    const bodyFile = join(tmp, `${idx}.md`);
    writeFileSync(bodyFile, it.body, 'utf8');
    const url = gh('issue', 'create', '--title', it.title, '--body-file', bodyFile, '--milestone', ms,
      ...itLabels.flatMap((l) => ['--label', l]), ...repoArgs);
    console.log(`   ${url}`);
  });
} finally {
  rmSync(tmp, { recursive: true, force: true });
}
console.log('Listo.');
