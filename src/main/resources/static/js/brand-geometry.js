// Geometría del logo de monA Bank (#178, ADR 0022): dónde se pinta la gráfica dentro de las letras de «monA».
// La comparten la intro del login (intro.js, sobre los píxeles de un canvas) y el generador de los SVG del logo
// (scripts/build-brand.mjs, sobre el contorno de las letras de Tektur), para que los dos dibujen lo mismo.
//
// Todo trabaja sobre una letra suelta, en sus propias coordenadas: { w, h, base, ink(x, y) }, donde «base» es la
// fila de la línea base e «ink» dice si el punto (x, y) cae dentro de la letra. Sin DOM: se prueba con node:test
// (scripts/brand-geometry.test.mjs).

const STEP = 2;

const hasInkInRow = (glyph, y) => {
  for (let x = 0; x < glyph.w; x++) if (glyph.ink(x, y)) return true;
  return false;
};

/** Primer renglón con tinta: la parte de arriba de la letra. */
export const glyphTop = (glyph) => {
  let y = 0;
  while (y < glyph.h && !hasInkInRow(glyph, y)) y++;
  return y;
};

/** Grosor del trazo: la franja de tinta de la columna central, de arriba abajo (se mide sobre la «o»). */
export const strokeThickness = (glyph) => {
  const x = Math.round(glyph.w / 2);
  let y0 = 0;
  while (y0 < glyph.h && !glyph.ink(x, y0)) y0++;
  let y1 = y0;
  while (y1 < glyph.h && glyph.ink(x, y1)) y1++;
  return y1 - y0;
};

/**
 * Línea por la parte de arriba de la letra: en cada columna, el centro de la primera franja de tinta, sin pasar del
 * grosor del trazo (en una pata, la franja llega hasta abajo). Con «to» solo recorre esa fracción del ancho.
 */
export const topLine = (glyph, thickness, to = 1) => {
  const points = [];
  for (let x = 0; x < glyph.w * to; x += STEP) {
    let y0 = -1;
    for (let y = 0; y < glyph.h; y++) if (glyph.ink(x, y)) { y0 = y; break; }
    if (y0 < 0) continue;
    let y1 = y0;
    while (y1 < y0 + thickness && glyph.ink(x, y1)) y1++;
    points.push([x, (y0 + y1) / 2]);
  }
  return points;
};

/**
 * Pata derecha (la de la n), desde «fromY» hasta la línea base: el centro de la franja de tinta más a la derecha de
 * cada fila. Acaba un trazo por debajo, para que la pintura llegue al suelo sin dejar un filo.
 */
export const rightStem = (glyph, fromY, thickness) => {
  const points = [];
  for (let y = Math.round(fromY); y <= glyph.base; y += STEP) {
    let right = glyph.w - 1;
    while (right > 0 && !glyph.ink(right, y)) right--;
    if (right === 0) continue;
    let left = right;
    while (left > 0 && glyph.ink(left, y)) left--;
    points.push([(left + right) / 2, y]);
  }
  if (points.length > 0) points.push([points.at(-1)[0], points.at(-1)[1] + thickness]);
  return points;
};

// recta x = a·y + b por mínimos cuadrados
const fit = (points) => {
  const k = points.length;
  const my = points.reduce((s, p) => s + p[1], 0) / k;
  const mx = points.reduce((s, p) => s + p[0], 0) / k;
  let num = 0;
  let den = 0;
  points.forEach(([x, y]) => { num += (y - my) * (x - mx); den += (y - my) ** 2; });
  const slope = num / den;
  return (y) => slope * y + mx - slope * my;
};

/**
 * Pata izquierda de la A: sus dos bordes (exterior e interior) como rectas x(y), ajustadas con las filas entre la
 * base y algo por debajo de la mitad. Las filas del travesaño, donde la franja izquierda llega hasta la otra pata,
 * no cuentan.
 */
export const leftLeg = (glyph, thickness) => {
  const top = glyphTop(glyph);
  const outer = [];
  const inner = [];
  for (let y = glyph.base - thickness; y > top + (glyph.base - top) * 0.45; y--) {
    let left = 0;
    while (left < glyph.w && !glyph.ink(left, y)) left++;
    let right = left;
    while (right < glyph.w && glyph.ink(right, y)) right++;
    if (left < glyph.w) { outer.push([left, y]); inner.push([right, y]); }
  }
  const widths = outer.map((p, i) => inner[i][0] - p[0]).sort((m, q) => m - q);
  const usual = widths[widths.length >> 2];
  const keep = outer.map((p, i) => inner[i][0] - p[0] <= usual * 1.25);
  return { top, outer: fit(outer.filter((_, i) => keep[i])), inner: fit(inner.filter((_, i) => keep[i])) };
};

/** La pata entre «bottom» y «top», con «pad» de más por fuera para no dejar un filo al recortarla con la letra. */
export const legPolygon = (leg, bottom, top, pad = 3) => [
  [leg.outer(bottom) - pad, bottom], [leg.outer(top) - pad, top], [leg.inner(top), top], [leg.inner(bottom), bottom],
];

/**
 * Flecha que prolonga la pata por encima del vértice: el asta sube «length» con el mismo ancho e inclinación que la
 * pata, y la punta, perpendicular a esa dirección, mide unas 2,6 veces su grosor (1,9 de largo). «headScale» (0-1)
 * hace crecer la punta en la intro.
 */
export const arrow = (leg, length, headScale = 1) => {
  const { top } = leg;
  const end = top - length;
  const shaft = [[leg.outer(top + 1), top + 1], [leg.outer(end), end], [leg.inner(end), end], [leg.inner(top + 1), top + 1]];
  const mid = (y) => (leg.outer(y) + leg.inner(y)) / 2;
  const mx = mid(end);
  const dx = mx - mid(top);
  const dy = end - top;
  const k = Math.hypot(dx, dy) || 1;
  const dir = [dx / k, dy / k];
  const perp = [-dir[1], dir[0]];
  const thick = Math.abs((leg.inner(end) - leg.outer(end)) * perp[0]);
  const half = thick * 1.3 * headScale;
  const tip = [mx + dir[0] * thick * 1.9 * headScale, end + dir[1] * thick * 1.9 * headScale];
  const head = [[mx + perp[0] * half, end + perp[1] * half], tip, [mx - perp[0] * half, end - perp[1] * half]];
  return { shaft, head, tip };
};

/** Los primeros f·100 % de un camino, medidos por su longitud: la gráfica se pinta de izquierda a derecha. */
export const partial = (points, f) => {
  const lengths = points.slice(1).map((p, i) => Math.hypot(p[0] - points[i][0], p[1] - points[i][1]));
  const total = lengths.reduce((s, d) => s + d, 0) * f;
  const out = [points[0]];
  let acc = 0;
  for (let i = 1; i < points.length; i++) {
    const d = lengths[i - 1];
    if (acc + d >= total) {
      const k = d === 0 ? 0 : (total - acc) / d;
      out.push([points[i - 1][0] + (points[i][0] - points[i - 1][0]) * k, points[i - 1][1] + (points[i][1] - points[i - 1][1]) * k]);
      return out;
    }
    acc += d;
    out.push(points[i]);
  }
  return out;
};
