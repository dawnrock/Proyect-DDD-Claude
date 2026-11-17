#!/usr/bin/env node
// Pruebas de contrato (#107, ADR 0013): arranca el jar con el perfil demo, espera a que esté UP, ejecuta
// Schemathesis contra docs/api/openapi.yaml (configuración en schemathesis.toml) y detiene la aplicación.
// Sale con el código de Schemathesis. Requisitos: Java 21, `./mvnw verify` (o `package`) hecho y
// `pip install schemathesis==4.33.4`.
//
//   node scripts/contract-test.mjs
//   CONTRACT_TEST_PORT=18080 SCHEMATHESIS=/ruta/a/schemathesis node scripts/contract-test.mjs

import { spawn } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { join } from 'node:path';

const port = process.env.CONTRACT_TEST_PORT ?? '8080';
const schemathesis = process.env.SCHEMATHESIS ?? 'schemathesis';
const baseUrl = `http://localhost:${port}`;
const startupSeconds = 90;

const jar = readdirSync('target', { withFileTypes: true })
  .filter((entry) => entry.isFile() && entry.name.endsWith('.jar') && !entry.name.endsWith('-plain.jar'))
  .map((entry) => join('target', entry.name))[0];
if (!jar) {
  console.error('No hay ningún jar en target/: ejecuta antes ./mvnw verify (o ./mvnw package).');
  process.exit(2);
}

console.log(`Arrancando ${jar} con el perfil demo en ${baseUrl}…`);
const app = spawn('java', ['-jar', jar, '--spring.profiles.active=demo', `--server.port=${port}`], {
  stdio: ['ignore', 'ignore', 'inherit'],
});
let appExited = false;
app.on('exit', () => { appExited = true; });

const stopApp = () => { if (!appExited) app.kill('SIGTERM'); };
process.on('SIGINT', () => { stopApp(); process.exit(130); });

async function isUp() {
  try {
    const response = await fetch(`${baseUrl}/actuator/health`);
    return response.ok && (await response.json()).status === 'UP';
  } catch {
    return false;
  }
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
      const run = spawn(schemathesis, ['run', 'docs/api/openapi.yaml', '--url', baseUrl], { stdio: 'inherit' });
      run.on('error', (error) => {
        console.error(`No se pudo ejecutar «${schemathesis}» (${error.message}). Instálalo con: pip install schemathesis==4.33.4`);
        resolve(2);
      });
      run.on('exit', (code) => resolve(code ?? 1));
    });
  }
} finally {
  stopApp();
}
process.exit(exitCode);
