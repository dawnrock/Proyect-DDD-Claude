#!/usr/bin/env node
// Versionado del contrato (#112, ADR 0014): compara docs/api/openapi.yaml de la PR con el de la rama base.
// - Si el contrato no cambia, pasa.
// - Si cambia, info.version debe ser mayor que la de la base (SemVer).
// - Si oasdiff encuentra cambios incompatibles (nivel ERR), la versión mayor debe subir.
// Sale con 1 si se incumple una regla y con 2 si falta algo para comprobarlo.
//
//   BASE_REF=Hexagonal node scripts/check-contract-version.mjs
//   BASE_REF=Hexagonal OASDIFF=~/go/bin/oasdiff node scripts/check-contract-version.mjs
//   node scripts/check-contract-version.mjs <contrato-base.yaml> <contrato-nuevo.yaml>   (sin git, para probarlo)

import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const contractPath = 'docs/api/openapi.yaml';
const oasdiff = process.env.OASDIFF ?? 'oasdiff';

function fail(message, code = 1) {
  console.error(`❌ ${message}`);
  process.exit(code);
}

// info.version: la clave `version:` del bloque `info:` (desde `info:` hasta la siguiente clave sin sangría).
function versionOf(contract) {
  const info = contract.match(/^info:\s*\n((?:[ \t].*\n|\s*\n)*)/m);
  const match = info && info[1].match(/^ {2}version:\s*['"]?([0-9]+\.[0-9]+\.[0-9]+)['"]?\s*$/m);
  if (!match) fail('No encuentro info.version (formato MAYOR.MENOR.PARCHE) en el contrato.', 2);
  return match[1].split('.').map(Number);
}

function compare(a, b) {
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i];
  return 0;
}

let basePath;
let headPath;
if (process.argv.length >= 4) {
  [basePath, headPath] = process.argv.slice(2, 4);
} else {
  const baseRef = process.env.BASE_REF;
  if (!baseRef) fail('Indica la rama base con BASE_REF (o pasa dos ficheros como argumentos).', 2);
  // Si la rama base no se puede leer, la comprobación no se salta en silencio: falla.
  try {
    execFileSync('git', ['rev-parse', '--verify', '--quiet', `origin/${baseRef}`], { cwd: root, stdio: 'ignore' });
  } catch {
    fail(`No encuentro la rama base origin/${baseRef} (¿BASE_REF bien escrito y descargado con git fetch?).`, 2);
  }
  let baseContract;
  try {
    baseContract = execFileSync('git', ['show', `origin/${baseRef}:${contractPath}`],
      { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  } catch {
    console.log(`La rama base origin/${baseRef} no tiene ${contractPath}: nada que comparar.`);
    process.exit(0);
  }
  const tempDir = mkdtempSync(join(tmpdir(), 'contract-'));
  process.on('exit', () => rmSync(tempDir, { recursive: true, force: true }));
  basePath = join(tempDir, 'base.yaml');
  writeFileSync(basePath, baseContract);
  headPath = join(root, contractPath);
}

// Finales de línea a LF (#159): en Windows, con core.autocrlf=true, la copia de trabajo tiene CRLF mientras que git show
// devuelve el contrato base con LF. Sin normalizar, `.` no casa con `\r` al buscar info.version y un contrato sin cambios
// parecería distinto del de la base.
const readContract = (path) => readFileSync(path, 'utf8').replace(/\r\n?/g, '\n');
const base = readContract(basePath);
const head = readContract(headPath);
if (base === head) {
  console.log('✅ El contrato no cambia: no hace falta subir la versión.');
  process.exit(0);
}

const baseVersion = versionOf(base);
const headVersion = versionOf(head);
const shown = (version) => version.join('.');
if (compare(headVersion, baseVersion) <= 0) {
  fail(`El contrato cambia pero info.version no sube (${shown(baseVersion)} → ${shown(headVersion)}). `
    + 'Súbela según ADR 0014 y añade la entrada en docs/api/CHANGELOG.md.');
}

const diff = spawnSync(oasdiff, ['breaking', basePath, headPath, '--fail-on', 'ERR'], { encoding: 'utf8' });
if (diff.error) fail(`No se pudo ejecutar «${oasdiff}» (${diff.error.message}). Instálalo con: go install github.com/oasdiff/oasdiff@v1.35.0`, 2);
// oasdiff sale con 1 si hay cambios incompatibles; con otro código (102: no carga un fichero), no ha comparado nada.
if (diff.status !== 0 && diff.status !== 1) {
  console.error(diff.stdout, diff.stderr);
  fail(`oasdiff no pudo comparar los contratos (código ${diff.status ?? diff.signal}).`, 2);
}
const breaking = diff.status === 1;
if (breaking && headVersion[0] <= baseVersion[0]) {
  console.error(diff.stdout, diff.stderr);
  fail(`Hay cambios incompatibles (arriba) y la versión mayor no sube (${shown(baseVersion)} → ${shown(headVersion)}). `
    + `Usa ${baseVersion[0] + 1}.0.0 o evita el cambio incompatible.`);
}

console.log(`✅ Versión del contrato correcta: ${shown(baseVersion)} → ${shown(headVersion)}`
  + (breaking ? ' (con cambios incompatibles y versión mayor nueva).' : '.'));
