#!/usr/bin/env node
// Genera los SVG del logo de monA Bank (#178, ADR 0022) en src/main/resources/static/brand/:
//   monabank-stacked.svg     «monA» con «Bank» debajo (bienvenidas del login y del alta)
//   monabank-horizontal.svg  «monA Bank» en una línea (cabecera)
//   favicon.svg              la A con su flecha, sobre el fondo de la web
//
// Las letras son trazos (no dependen de la fuente) y la gráfica se calcula con el mismo módulo que usa la intro
// (static/js/brand-geometry.js), sobre el contorno de cada letra de Tektur. No forma parte del build: se ejecuta a
// mano cuando cambia el logo o la tipografía, y los SVG se suben al repositorio. Necesita Node 22 e internet: descarga
// con `npm pack` (que comprueba la integridad contra el registro) opentype.js y las instancias estáticas 400 y 700 de
// Tektur en una carpeta temporal; opentype.js no aplica las variaciones de una fuente variable.
//
//   node scripts/build-brand.mjs

import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import {
  arrow, leftLeg, legPolygon, rightStem, strokeThickness, topLine,
} from '../src/main/resources/static/js/brand-geometry.js';

const OPENTYPE = 'opentype.js@2.0.0';
const TEKTUR = '@fontsource/tektur@5.3.0';
const COLORS = { paper: '#eef0f2', mist: '#b9c0c7', flat: '#8b949c', drop: '#f0545c', rise: '#1fc8b0', ink: '#212529' };
const SIZE = 200;        // tamaño de la A; «mon» va al 75 %
const MON = 0.75;
const ARROW = 0.18;      // largo del asta sobre el vértice, en tamaños de la A
const CURVE_STEPS = 12;  // segmentos por curva al aplanar el contorno

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'src', 'main', 'resources', 'static', 'brand');

// ---------- descargas ----------
const work = mkdtempSync(join(tmpdir(), 'monabank-brand-'));
const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const unpack = (spec) => {
  const file = execFileSync(npm, ['pack', spec, '--pack-destination', work, '--silent'],
    { encoding: 'utf8', shell: process.platform === 'win32' }).trim().split('\n').at(-1);
  const dir = join(work, file.replace(/\.tgz$/, ''));
  mkdirSync(dir);
  execFileSync('tar', ['-xzf', join(work, file), '-C', dir]);
  return join(dir, 'package');
};
let opentype;
let fonts;
try {
  opentype = await import(pathToFileURL(join(unpack(OPENTYPE), 'dist', 'opentype.mjs')).href);
  const tektur = unpack(TEKTUR);
  const load = (weight) => {
    const bytes = readFileSync(join(tektur, 'files', `tektur-latin-${weight}-normal.woff`));
    return opentype.parse(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
  };
  fonts = { bold: load(700), regular: load(400) };
} finally {
  process.on('exit', () => rmSync(work, { recursive: true, force: true }));
}

// ---------- contornos ----------
const r2 = (v) => Math.round(v * 100) / 100;

/** Contorno de una letra como polígonos (curvas aplanadas) y como «d» de SVG. */
const outline = (font, ch, x, y, size) => {
  const path = font.getPath(ch, x, y, size);
  const polygons = [];
  let current = null;
  let [px, py] = [0, 0];
  for (const c of path.commands) {
    if (c.type === 'M') { current = [[c.x, c.y]]; polygons.push(current); }
    else if (c.type === 'L') current.push([c.x, c.y]);
    else if (c.type === 'Q' || c.type === 'C') {
      for (let i = 1; i <= CURVE_STEPS; i++) {
        const t = i / CURVE_STEPS;
        const u = 1 - t;
        current.push(c.type === 'Q'
          ? [u * u * px + 2 * u * t * c.x1 + t * t * c.x, u * u * py + 2 * u * t * c.y1 + t * t * c.y]
          : [u ** 3 * px + 3 * u * u * t * c.x1 + 3 * u * t * t * c.x2 + t ** 3 * c.x,
            u ** 3 * py + 3 * u * u * t * c.y1 + 3 * u * t * t * c.y2 + t ** 3 * c.y]);
      }
    }
    if (c.type !== 'Z') [px, py] = [c.x, c.y];
  }
  const d = path.commands.map((c) => {
    switch (c.type) {
      case 'M': case 'L': return `${c.type}${r2(c.x)} ${r2(c.y)}`;
      case 'Q': return `Q${r2(c.x1)} ${r2(c.y1)} ${r2(c.x)} ${r2(c.y)}`;
      case 'C': return `C${r2(c.x1)} ${r2(c.y1)} ${r2(c.x2)} ${r2(c.y2)} ${r2(c.x)} ${r2(c.y)}`;
      default: return 'Z';
    }
  }).join('');
  return { polygons, d, box: path.getBoundingBox() };
};

const inside = (polygons, x, y) => {   // regla par-impar, como el relleno de las letras
  let hit = false;
  for (const poly of polygons) {
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const [xi, yi] = poly[i];
      const [xj, yj] = poly[j];
      if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) hit = !hit;
    }
  }
  return hit;
};

/**
 * Una letra en sus propias coordenadas, como la analiza la intro sobre el canvas: dibujada en x = 2 con la línea base
 * en y = round(size), y «ink» evaluada en el centro de cada píxel.
 */
const glyphOf = (font, ch, size) => {
  const base = Math.round(size);
  const { polygons } = outline(font, ch, 2, base, size);
  const advance = font.getAdvanceWidth(ch, size);
  return { w: Math.ceil(advance) + 4, h: Math.ceil(size * 1.3), base, advance, ink: (x, y) => inside(polygons, x + 0.5, y + 0.5) };
};

// ---------- el logo «monA» ----------
const name = [...'monA'].map((ch, i) => ({ ch, size: i < 3 ? SIZE * MON : SIZE }));
const glyphs = name.map((l) => glyphOf(fonts.bold, l.ch, l.size));
const T = strokeThickness(glyphs[1]);
let pen = 0;
name.forEach((l, i) => { l.x = pen; pen += glyphs[i].advance; });
const nameWidth = pen;
// de las coordenadas de cada letra a las del logo (línea base en y = 0)
const at = (i) => ([x, y]) => [name[i].x - 2 + x, y - glyphs[i].base];

const flat = [0, 1].flatMap((i) => topLine(glyphs[i], T).map(at(i)));
const nTop = topLine(glyphs[2], T, 0.8);
const drop = [...nTop, ...rightStem(glyphs[2], nTop.at(-1)[1], T)].map(at(2));
const leg = leftLeg(glyphs[3], T);
const legShape = legPolygon(leg, glyphs[3].base + 2, leg.top).map(at(3));
const { shaft, head } = arrow(leg, SIZE * ARROW);
const arrowShapes = [shaft.map(at(3)), head.map(at(3))];

const letterPaths = name.map((l) => outline(fonts.bold, l.ch, l.x, 0, l.size));
const points = (pts) => pts.map(([x, y]) => `${r2(x)},${r2(y)}`).join(' ');

/** «monA» con su gráfica: letras, pinturas recortadas por cada letra y flecha. */
const nameMarkup = (id) => {
  const clip = (i) => `${id}-${name[i].ch}`;
  const clips = [0, 1, 2, 3].map((i) => `<clipPath id="${clip(i)}"><path d="${letterPaths[i].d}"/></clipPath>`).join('');
  const stroke = `fill="none" stroke-width="${r2(T * 1.4)}" stroke-linejoin="round" stroke-linecap="butt"`;
  const flatM = flat.filter(([x]) => x < name[1].x);
  const flatO = flat.filter(([x]) => x >= name[1].x);
  return `<defs>${clips}</defs>`
    + `<g fill="${COLORS.paper}">${letterPaths.map((p) => `<path d="${p.d}"/>`).join('')}</g>`
    + `<polyline points="${points(flatM)}" stroke="${COLORS.flat}" ${stroke} clip-path="url(#${clip(0)})"/>`
    + `<polyline points="${points(flatO)}" stroke="${COLORS.flat}" ${stroke} clip-path="url(#${clip(1)})"/>`
    + `<polyline points="${points(drop)}" stroke="${COLORS.drop}" ${stroke} clip-path="url(#${clip(2)})"/>`
    + `<polygon points="${points(legShape)}" fill="${COLORS.rise}" clip-path="url(#${clip(3)})"/>`
    + arrowShapes.map((s) => `<polygon points="${points(s)}" fill="${COLORS.rise}"/>`).join('');
};

const bounds = (boxes) => {
  const x1 = Math.min(...boxes.map((b) => b.x1));
  const y1 = Math.min(...boxes.map((b) => b.y1));
  const x2 = Math.max(...boxes.map((b) => b.x2));
  const y2 = Math.max(...boxes.map((b) => b.y2));
  return { x1, y1, x2, y2 };
};
const boxOf = (pts) => bounds(pts.map(([x, y]) => ({ x1: x, y1: y, x2: x, y2: y })));
const nameBox = bounds([...letterPaths.map((p) => p.box), ...arrowShapes.map(boxOf)]);

const svg = (box, body, pad = 4) => {
  const vb = [box.x1 - pad, box.y1 - pad, box.x2 - box.x1 + 2 * pad, box.y2 - box.y1 + 2 * pad].map(r2);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb.join(' ')}" role="img" aria-label="monA Bank">`
    + `<title>monA Bank</title>${body}</svg>\n`;
};

// «Bank»: en la apilada, al 30 % de la A, alineado a la derecha bajo «monA»; en la horizontal, a su lado
const bank = (size, x, y) => outline(fonts.regular, 'Bank', x, y, size);
const kindSize = SIZE * 0.3;
const stackedBank = bank(kindSize, nameWidth - fonts.regular.getAdvanceWidth('Bank', kindSize), SIZE * 0.36);
const lineSize = SIZE * 0.45;
const lineBank = bank(lineSize, nameWidth + SIZE * 0.14, 0);

mkdirSync(out, { recursive: true });
writeFileSync(join(out, 'monabank-stacked.svg'), svg(bounds([nameBox, stackedBank.box]),
  nameMarkup('s') + `<path fill="${COLORS.mist}" d="${stackedBank.d}"/>`));
writeFileSync(join(out, 'monabank-horizontal.svg'), svg(bounds([nameBox, lineBank.box]),
  nameMarkup('h') + `<path fill="${COLORS.mist}" d="${lineBank.d}"/>`));

// favicon: la A con su pata turquesa y la flecha, centrada en un cuadrado redondeado del color de fondo de la web
const aBox = bounds([letterPaths[3].box, ...arrowShapes.map(boxOf)]);
const side = Math.max(aBox.x2 - aBox.x1, aBox.y2 - aBox.y1) * 1.16;
const cx = (aBox.x1 + aBox.x2) / 2;
const cy = (aBox.y1 + aBox.y2) / 2;
const square = { x1: cx - side / 2, y1: cy - side / 2, x2: cx + side / 2, y2: cy + side / 2 };
writeFileSync(join(out, 'favicon.svg'), svg(square,
  `<rect x="${r2(square.x1)}" y="${r2(square.y1)}" width="${r2(side)}" height="${r2(side)}" rx="${r2(side * 0.18)}" fill="${COLORS.ink}"/>`
  + `<defs><clipPath id="f-A"><path d="${letterPaths[3].d}"/></clipPath></defs>`
  + `<path fill="${COLORS.paper}" d="${letterPaths[3].d}"/>`
  + `<polygon points="${points(legShape)}" fill="${COLORS.rise}" clip-path="url(#f-A)"/>`
  + arrowShapes.map((s) => `<polygon points="${points(s)}" fill="${COLORS.rise}"/>`).join(''), 0));

console.log(`✅ Logos generados en ${out}`);
