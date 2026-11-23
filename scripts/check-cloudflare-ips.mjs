// Compara los rangos de Cloudflare de app.security.trusted-proxies (src/main/resources/application.properties) con
// las listas oficiales (https://www.cloudflare.com/ips-v4 y /ips-v6) e indica qué añadir y qué quitar (#133, ADR 0017).
// Se ejecuta a mano de vez en cuando: necesita acceso a www.cloudflare.com y no forma parte del CI.
//
//   node scripts/check-cloudflare-ips.mjs
//
// Sale con código 0 si coinciden y 1 si hay diferencias o no se pueden descargar.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const OFFICIAL = ['https://www.cloudflare.com/ips-v4', 'https://www.cloudflare.com/ips-v6'];
// Rangos internos de la misma propiedad: no son de Cloudflare y no se comparan
const INTERNAL = new Set(['10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16', '100.64.0.0/10', '127.0.0.0/8', '::1/128',
  'fc00::/7']);

function configuredRanges() {
  const text = readFileSync(join(root, 'src', 'main', 'resources', 'application.properties'), 'utf8')
    .replace(/\\\r?\n\s*/g, '');
  const line = text.split(/\r?\n/).find((l) => l.startsWith('app.security.trusted-proxies='));
  if (!line) throw new Error('No encuentro app.security.trusted-proxies en application.properties');
  return line.slice(line.indexOf('=') + 1).split(',').map((r) => r.trim()).filter((r) => r && !INTERNAL.has(r));
}

async function officialRanges() {
  const ranges = [];
  for (const url of OFFICIAL) {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`${url} respondió ${response.status}`);
    ranges.push(...(await response.text()).split(/\s+/).filter(Boolean));
  }
  return ranges;
}

try {
  const configured = new Set(configuredRanges());
  const official = new Set(await officialRanges());
  const add = [...official].filter((r) => !configured.has(r));
  const remove = [...configured].filter((r) => !official.has(r));
  if (add.length === 0 && remove.length === 0) {
    console.log(`✅ Los ${official.size} rangos de Cloudflare de application.properties coinciden con la lista oficial.`);
  } else {
    if (add.length) console.log(`➕ Añadir a app.security.trusted-proxies: ${add.join(', ')}`);
    if (remove.length) console.log(`➖ Quitar de app.security.trusted-proxies: ${remove.join(', ')}`);
    console.log('Actualiza también la fecha del comentario y los ejemplos de TrustedProxiesConfigurationTests.');
    process.exitCode = 1;
  }
} catch (error) {
  console.error(`❌ ${error.message}`);
  process.exitCode = 1;
}
