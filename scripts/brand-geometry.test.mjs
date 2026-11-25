// Tests de la geometría de la marca monA Bank (#178, ADR 0022): src/main/resources/static/js/brand-geometry.js,
// el módulo que comparten la intro del login (sobre los píxeles de un canvas) y el generador de los SVG del logo
// (sobre el contorno de las letras). Se prueban con letras sintéticas, definidas como funciones de tinta.
//
//   node --test "scripts/*.test.mjs"

import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  arrow, glyphTop, leftLeg, legPolygon, partial, rightStem, strokeThickness, topLine,
} from '../src/main/resources/static/js/brand-geometry.js';

const BASE = 100;
const near = (actual, expected, tolerance, message) =>
  assert.ok(Math.abs(actual - expected) <= tolerance, `${message}: ${actual} no está a ±${tolerance} de ${expected}`);
const glyph = (w, ink) => ({ w, h: BASE + 10, base: BASE, ink });

// «o» rectangular: caja de 10 a 90 en x y de 20 a 100 en y, con trazo de 15
const inBox = (x, y, x0, y0, x1, y1) => x >= x0 && x < x1 && y >= y0 && y < y1;
const o = glyph(100, (x, y) => inBox(x, y, 10, 20, 90, BASE) && !inBox(x, y, 25, 35, 75, BASE - 15));
// «n» cuadrada: barra superior de 20 a 35 y dos patas de 15 de ancho hasta la línea base
const n = glyph(100, (x, y) => inBox(x, y, 10, 20, 90, 35) || inBox(x, y, 10, 20, 25, BASE) || inBox(x, y, 75, 20, 90, BASE));
// «A»: pata izquierda entre x = 40 − 0,3·y y x = 60 − 0,3·y, la derecha simétrica respecto a x = 60, y un travesaño
const leftOuter = (y) => 40 - 0.3 * y;
const a = glyph(130, (x, y) => y >= 0 && y < BASE && (
  (x >= leftOuter(y) && x < leftOuter(y) + 20)
  || (x <= 120 - leftOuter(y) && x > 100 - leftOuter(y))
  || (y >= 60 && y < 72 && x >= leftOuter(y) && x <= 120 - leftOuter(y))));

describe('grosor del trazo y línea superior', () => {
  test('el grosor es la franja de tinta del centro de la letra, de arriba abajo', () => {
    assert.equal(strokeThickness(o), 15);
  });

  test('la línea superior pasa por el centro de la franja de arriba, también sobre los laterales', () => {
    const line = topLine(o, 15);
    assert.ok(line.length > 30, 'recorre todo el ancho de la letra');
    line.forEach(([x, y]) => near(y, 27.5, 0.01, `y en x=${x}`));
    assert.equal(line[0][0], 10);
  });

  test('con «to» solo recorre esa fracción del ancho', () => {
    assert.ok(topLine(n, 15, 0.5).every(([x]) => x < 50));
  });

  test('el primer renglón con tinta es la parte de arriba de la letra', () => {
    assert.equal(glyphTop(n), 20);
  });
});

describe('pata derecha de la n', () => {
  test('baja por el centro de la pata hasta la línea base y un trazo más, para no dejar un filo sin pintar', () => {
    const stem = rightStem(n, 40, 15);
    stem.forEach(([x]) => near(x, 82, 0.5, 'centro de la pata'));
    assert.equal(stem[0][1], 40);
    assert.equal(stem.at(-1)[1], stem.at(-2)[1] + 15);
    assert.ok(stem.at(-2)[1] >= BASE - 2);
  });
});

describe('pata izquierda de la A y flecha', () => {
  test('ajusta los dos bordes de la pata sin dejarse engañar por el travesaño', () => {
    const leg = leftLeg(a, 15);
    assert.equal(leg.top, 0);
    [10, 40, 85].forEach((y) => {
      near(leg.outer(y), leftOuter(y), 1, `borde exterior en y=${y}`);
      near(leg.inner(y), leftOuter(y) + 20, 1, `borde interior en y=${y}`);
    });
  });

  test('el polígono de la pata va de la base al vértice entre sus dos bordes, con un margen por fuera', () => {
    const leg = leftLeg(a, 15);
    const [p0, p1, p2, p3] = legPolygon(leg, BASE + 2, leg.top, 3);
    near(p0[0], leg.outer(BASE + 2) - 3, 1e-9, 'abajo, por fuera');
    assert.equal(p1[1], 0);
    near(p2[0], leg.inner(0), 1e-9, 'arriba, por dentro');
    assert.equal(p3[1], BASE + 2);
  });

  test('el asta prolonga la pata con su mismo ancho y la punta mide unas 2,6 veces ese grosor', () => {
    const leg = leftLeg(a, 15);
    const { shaft, head, tip } = arrow(leg, 30);
    near(shaft[1][1], -30, 1e-9, 'el asta sube 30 sobre el vértice');
    near(shaft[2][0] - shaft[1][0], leg.inner(-30) - leg.outer(-30), 1e-9, 'mismo ancho que la pata');
    const width = Math.hypot(head[0][0] - head[2][0], head[0][1] - head[2][1]);
    const thick = (leg.inner(0) - leg.outer(0)) * Math.cos(Math.atan(0.3));
    near(width / thick, 2.6, 0.05, 'base de la punta / grosor');
    assert.ok(tip[1] < -30, 'la punta queda por encima del asta');
    assert.ok(tip[0] > shaft[1][0], 'y sigue la inclinación de la pata, hacia la derecha');
  });

  test('con escala 0 la punta no se ve', () => {
    const { head } = arrow(leftLeg(a, 15), 30, 0);
    head.forEach((p) => near(Math.hypot(p[0] - head[0][0], p[1] - head[0][1]), 0, 1e-9, 'punta degenerada'));
  });
});

describe('recorrido parcial', () => {
  test('devuelve el primer tramo del camino según la fracción de su longitud', () => {
    assert.deepEqual(partial([[0, 0], [10, 0], [10, 10]], 0.75), [[0, 0], [10, 0], [10, 5]]);
    assert.deepEqual(partial([[0, 0], [10, 0]], 1), [[0, 0], [10, 0]]);
  });
});
