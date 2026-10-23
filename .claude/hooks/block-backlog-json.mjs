// PreToolUse hook: impide que el agente lea los JSON de semilla del backlog
// (scripts/issues-fase-*.json). La fuente de verdad de las tareas son las issues de GitHub.
// Exit code 2 bloquea la llamada y envía stderr a Claude.
import { readFileSync } from 'node:fs';

let input = {};
try { input = JSON.parse(readFileSync(0, 'utf8') || '{}'); } catch { process.exit(0); }

const t = input.tool_input ?? {};
const targets = {
  Read: [t.file_path],
  Grep: [t.path, t.glob, t.pattern],
  Glob: [t.path, t.pattern],
  Bash: [t.command],
}[input.tool_name] ?? [];

if (targets.filter(Boolean).some((s) => /issues-fase-\d+\.json|issues-fase-\*/.test(s))) {
  process.stderr.write(
    'El acceso a scripts/issues-fase-*.json está bloqueado por un hook del proyecto. ' +
    'Consulta las tareas en GitHub con `gh issue view <n>`.\n');
  process.exit(2);
}
process.exit(0);
