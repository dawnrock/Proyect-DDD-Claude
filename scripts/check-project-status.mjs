#!/usr/bin/env node
// Comprueba que una PR que cierra una issue actualiza docs/ia/estado-del-proyecto.md.
//
// Uso (en CI):
//   PR_BODY="..." node scripts/check-project-status.mjs <archivo con la lista de archivos cambiados>
//   git diff --name-only origin/Monolithic...HEAD | PR_BODY="..." node scripts/check-project-status.mjs -
//
// Sale con 0 si la PR no cierra ninguna issue o si toca el documento; con 1 si cierra una issue y no lo toca.
import { readFileSync } from 'node:fs';

export const STATUS_FILE = 'docs/ia/estado-del-proyecto.md';
const CLOSING = /\b(close[sd]?|fix(e[sd])?|resolve[sd]?)\s+#(\d+)/gi;

export function checkProjectStatus(body, changedFiles) {
  const closes = [...(body ?? '').matchAll(CLOSING)].map((m) => `#${m[3]}`);
  if (closes.length === 0) {
    return { ok: true, message: 'La PR no cierra ninguna issue: no se exige actualizar el estado del proyecto.' };
  }
  const files = changedFiles.map((f) => f.trim().replace(/\\/g, '/')).filter(Boolean);
  if (files.includes(STATUS_FILE)) {
    return { ok: true, message: `La PR cierra ${closes.join(', ')} y actualiza ${STATUS_FILE}.` };
  }
  return {
    ok: false,
    message: `La PR cierra ${closes.join(', ')} pero no actualiza ${STATUS_FILE}. ` +
      'Actualiza el estado de la issue y el siguiente paso, y sus anexos (decisiones, pendientes, historial) ' +
      '(paso 6.3 de implement-plan).',
  };
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].replace(/\\/g, '/').split('/').pop())) {
  const source = process.argv[2];
  if (!source) {
    console.error('Uso: PR_BODY="..." node scripts/check-project-status.mjs <archivo|->');
    process.exit(2);
  }
  const list = readFileSync(source === '-' ? 0 : source, 'utf8').split(/\r?\n/);
  const result = checkProjectStatus(process.env.PR_BODY, list);
  (result.ok ? console.log : console.error)(result.message);
  process.exit(result.ok ? 0 : 1);
}
