#!/usr/bin/env node
// Comprueba que el dominio no usa Lombok (ADR 0009: sus anotaciones tienen retención SOURCE y ArchUnit
// solo las ve a medias, por @lombok.Generated) y repite como red de seguridad las reglas de dependencias.
//
// Uso: node .claude/skills/ddd-building-block/scripts/check-domain.mjs [raíz-del-dominio]
// Sale con código 1 e imprime cada infracción como "archivo:línea: motivo".
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = process.argv[2] ?? 'src/main/java/com/pronoide/ddd/domain';
if (!existsSync(root)) {
  console.error(`No existe ${root}`);
  process.exit(1);
}

const rules = [
  [/^\s*import\s+org\.springframework\./, 'el dominio no puede depender de Spring'],
  [/^\s*import\s+jakarta\./, 'el dominio no puede depender de jakarta'],
  [/^\s*import\s+org\.immutables\./, 'Immutables no se usa en el dominio'],
  // Sin el import (o el nombre completo) una anotación de Lombok no compila: basta con vigilar los dos.
  [/^\s*import\s+(?:static\s+)?lombok\./, 'Lombok no se usa en el dominio (ADR 0009)'],
  [/@lombok\./, 'Lombok no se usa en el dominio (ADR 0009)'],
  [/^\s*public\s+(?:final\s+)?void\s+set[A-Z]\w*\s*\(/, 'setter público en el dominio: usa un método de negocio'],
];

const files = [];
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p);
    else if (p.endsWith('.java')) files.push(p);
  }
})(root);

const errors = [];
for (const file of files) {
  readFileSync(file, 'utf8').split(/\r?\n/).forEach((line, i) => {
    for (const [re, why] of rules) {
      if (re.test(line)) errors.push(`${relative('.', file)}:${i + 1}: ${why}`);
    }
  });
}

if (errors.length) {
  console.error(errors.join('\n'));
  console.error(`\n${errors.length} infracción(es) en el dominio.`);
  process.exit(1);
}
console.log(`Dominio correcto (${files.length} archivos revisados).`);
