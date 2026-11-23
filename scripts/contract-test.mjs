#!/usr/bin/env node
// Pruebas de contrato (#107, ADR 0013): arranca el jar con el perfil demo, espera a que esté UP, ejecuta
// Schemathesis contra docs/api/openapi.yaml (configuración en schemathesis.toml) y detiene la aplicación.
// Sale con el código de Schemathesis (2 si falta algo para ejecutarlo). Requisitos: Java 21,
// `./mvnw verify` (o `package`) hecho y `pip install schemathesis==4.33.4`. Funciona desde cualquier directorio.
//
//   node scripts/contract-test.mjs
//   CONTRACT_TEST_PORT=18080 SCHEMATHESIS=/ruta/a/schemathesis node scripts/contract-test.mjs

import { spawn } from 'node:child_process';
import { readdirSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const port = process.env.CONTRACT_TEST_PORT ?? '8080';
const schemathesis = process.env.SCHEMATHESIS ?? 'schemathesis';
const baseUrl = `http://localhost:${port}`;
const startupSeconds = 90;
const stopSeconds = 10;

function fail(message) {
  console.error(message);
  process.exit(2);
}

async function isUp() {
  try {
    const response = await fetch(`${baseUrl}/actuator/health`);
    return response.ok && (await response.json()).status === 'UP';
  } catch {
    return false;
  }
}

// El jar más reciente de target/ (si hubiera varios, el último construido).
const targetDir = join(root, 'target');
let jars = [];
try {
  jars = readdirSync(targetDir)
    .filter((name) => name.endsWith('.jar') && !name.endsWith('-plain.jar'))
    .map((name) => join(targetDir, name))
    .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
} catch {
  // sin target/: se informa abajo
}
if (jars.length === 0) fail('No hay ningún jar en target/: ejecuta antes ./mvnw verify (o ./mvnw package).');

// Si ya responde algo en el puerto, Schemathesis probaría esa aplicación y no la del jar.
if (await isUp()) fail(`Ya hay una aplicación en ${baseUrl}: detenla o elige otro puerto con CONTRACT_TEST_PORT.`);

console.log(`Arrancando ${jars[0]} con el perfil demo en ${baseUrl}…`);
// Schemathesis envía credenciales inventadas a POST /api/auth/token: con el límite normal de intentos (ADR 0017)
// bloquearía su propia IP y luego no obtendría el token que necesitan las demás operaciones. Con el límite de altas
// (ADR 0018), signUp respondería 429 tras tres altas y no se probaría el contrato.
const app = spawn('java', ['-jar', jars[0], '--spring.profiles.active=demo', `--server.port=${port}`,
  '--app.security.login-attempts.max-per-user=1000000', '--app.security.login-attempts.max-per-ip=1000000',
  '--app.security.sign-ups.max-per-ip=1000000', '--app.security.sign-ups.max-total=1000000'], {
  cwd: root,
  stdio: ['ignore', 'ignore', 'inherit'],
});
let appExited = false;
const appExit = new Promise((resolve) => app.on('exit', () => { appExited = true; resolve(); }));
app.on('error', (error) => fail(`No se pudo ejecutar java (${error.message}). Hace falta Java 21.`));

async function stopApp() {
  if (appExited) return;
  app.kill('SIGTERM');
  const killer = setTimeout(() => app.kill('SIGKILL'), stopSeconds * 1000);
  await appExit;
  clearTimeout(killer);
}

// Si cancelan el proceso (Ctrl+C, cancelación o timeout del CI), la aplicación no queda viva.
for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP']) {
  process.on(signal, async () => { await stopApp(); process.exit(130); });
}

async function waitUntilUp() {
  for (let second = 0; second < startupSeconds; second++) {
    if (appExited) return false;
    if (await isUp()) return true;
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  return false;
}

let exitCode = 1;
try {
  if (!(await waitUntilUp())) {
    console.error(`La aplicación no llegó a UP en ${startupSeconds} s.`);
  } else {
    exitCode = await new Promise((resolve) => {
      const run = spawn(schemathesis, ['run', join(root, 'docs', 'api', 'openapi.yaml'), '--url', baseUrl], {
        cwd: root, // schemathesis.toml se lee del directorio de trabajo
        stdio: 'inherit',
      });
      run.on('error', (error) => {
        console.error(`No se pudo ejecutar «${schemathesis}» (${error.message}). Instálalo con: pip install schemathesis==4.33.4`);
        resolve(2);
      });
      run.on('exit', (code) => resolve(code ?? 1));
    });
    if (appExited) {
      console.error('La aplicación se detuvo durante las pruebas: los fallos de conexión no son del contrato.');
      exitCode = exitCode || 1;
    }
  }
} finally {
  await stopApp();
}
process.exit(exitCode);
