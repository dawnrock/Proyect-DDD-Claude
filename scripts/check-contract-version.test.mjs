// Tests de check-contract-version.mjs (#159): un contrato con finales de línea CRLF (copia de trabajo en Windows con
// core.autocrlf=true) da el mismo resultado que con LF. Se ejecutan con el runner de Node, sin dependencias:
//
//   node --test "scripts/*.test.mjs"

import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { after, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const script = join(root, 'scripts', 'check-contract-version.mjs');
const contract = readFileSync(join(root, 'docs', 'api', 'openapi.yaml'), 'utf8').replace(/\r\n?/g, '\n');
const tempDir = mkdtempSync(join(tmpdir(), 'contract-version-test-'));
after(() => rmSync(tempDir, { recursive: true, force: true }));

const crlf = (text) => text.replace(/\n/g, '\r\n');

// El contrato con otra info.version y, si se pide, un cambio que no toca la versión.
// Si el bloque info: del contrato cambiara de forma y un replace no encontrara nada, el test fallaría aquí en lugar de
// comparar dos contratos iguales sin comprobar nada.
function variant({ version, change = false }) {
  const versioned = contract.replace(/^( {2}version:\s*)['"]?[0-9]+\.[0-9]+\.[0-9]+['"]?/m, `$1${version}`);
  assert.match(versioned, new RegExp(`^ {2}version: ${version.replace(/\./g, '\\.')}$`, 'm'));
  if (!change) return versioned;
  const changed = versioned.replace(/^info:\n/m, 'info:\n  x-test-change: true\n');
  assert.notEqual(changed, versioned);
  return changed;
}

let files = 0;
function write(text) {
  const path = join(tempDir, `contract-${files++}.yaml`);
  writeFileSync(path, text);
  return path;
}

// Modo de dos ficheros. OASDIFF apunta a un binario que no existe: si el script llega a oasdiff, sale con 2 y lo dice.
function run(base, head) {
  const result = spawnSync(process.execPath, [script, write(base), write(head)], {
    encoding: 'utf8',
    // NODE_NO_WARNINGS: los avisos de Node llevan el PID y harían distintas dos salidas iguales
    env: { ...process.env, NODE_NO_WARNINGS: '1', OASDIFF: join(tempDir, 'oasdiff-inexistente') },
  });
  return { status: result.status, output: (result.stdout + result.stderr).replace(/\s+$/, '') };
}

describe('check-contract-version con finales de línea CRLF', () => {
  const base = variant({ version: '1.0.0' });
  const cases = {
    'contrato sin cambios': variant({ version: '1.0.0' }),
    'contrato que cambia sin subir la versión': variant({ version: '1.0.0', change: true }),
    'contrato que sube la versión': variant({ version: '1.1.0', change: true }),
  };

  for (const [name, head] of Object.entries(cases)) {
    test(`${name}: el contrato nuevo en CRLF da el mismo resultado que en LF`, () => {
      assert.deepEqual(run(base, crlf(head)), run(base, head));
    });

    test(`${name}: el contrato base en CRLF da el mismo resultado que en LF`, () => {
      assert.deepEqual(run(crlf(base), head), run(base, head));
    });
  }

  test('los resultados con LF son los que dicta la regla', () => {
    assert.equal(run(base, cases['contrato sin cambios']).status, 0);
    assert.equal(run(base, cases['contrato que cambia sin subir la versión']).status, 1);
    const bumped = run(base, cases['contrato que sube la versión']);
    assert.equal(bumped.status, 2);
    assert.match(bumped.output, /No se pudo ejecutar/);
  });
});
