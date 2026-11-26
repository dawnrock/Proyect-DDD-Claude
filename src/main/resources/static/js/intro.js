// Intro de monA Bank (#178, ADR 0022): 5 s antes de las puertas del login. Doce palabras positivas de la banca y la
// bolsa se quedan un segundo a la vista, pierden su terminación, sus iniciales vuelan a las letras del logo, se pinta la
// gráfica de la marca y el logo se queda otro segundo antes de desvanecerse.
// Prototipo aprobado: docs/brand/prototypes/intro.html.
//
// Mejora progresiva: la capa (#splash) llega oculta y solo se muestra si va a reproducirse. Se ve en cada carga de las
// puertas; no sin JavaScript, con movimiento reducido ni si la tipografía no carga. Es un módulo: se evalúa una vez por
// documento, así que las navegaciones de htmx (hx-boost) no la repiten. Esc o un clic la quitan.
import { arrow, leftLeg, legPolygon, partial, rightStem, strokeThickness, topLine } from 'brand-geometry';

const splash = document.getElementById('splash');
const canvas = splash?.querySelector('canvas');
const FONT = 'Tektur';
const DURATION = 5000;
const WORDS_HOLD = 1000;   // las palabras, quietas y enteras, antes de convertirse en la marca
const FADE = 350;          // la capa se desvanece en los últimos 350 ms; antes, el logo queda quieto un segundo más
const COLORS = { paper: '#eef0f2', mist: '#b9c0c7', flat: '#8b949c', drop: '#f0545c', rise: '#1fc8b0' };
// Palabras de cada letra del logo, en orden: m o n A (monA) · B a n k (Bank)
const WORDS = [['Maximize', 'Markets'], ['Optimism'], ['Net gain'], ['Affluence', 'Assets'],
    ['Bull', 'Bonus'], ['Alpha'], ['Next'], ['Key', 'Keep']];
const TIER = {
    Optimism: 0, Affluence: 0, Maximize: 1, Bull: 1, Key: 1, Markets: 2, 'Net gain': 2, Alpha: 2,
    Assets: 3, Bonus: 3, Next: 3, Keep: 3,
};
const MON = 0.75;    // «mon» al 75 % de la A
const ARROW = 0.18;  // largo del asta sobre el vértice, en tamaños de la A

const fontReady = async () => {
    try {
        await Promise.all([document.fonts.load(`900 40px "${FONT}"`), document.fonts.load(`400 40px "${FONT}"`)]);
        return document.fonts.check(`700 40px "${FONT}"`);
    } catch {
        return false;
    }
};

const clamp = (v) => Math.min(1, Math.max(0, v));
const easeOut = (t) => 1 - (1 - t) ** 3;
const easeInOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2);
const easeInOutQuart = (t) => (t < 0.5 ? 8 * t ** 4 : 1 - (-2 * t + 2) ** 4 / 2);

/** Prepara la escena para la pantalla actual y devuelve la función que dibuja el instante t (ms). */
const stage = () => {
    const ctx = canvas.getContext('2d');
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const W = window.innerWidth;
    const H = window.innerHeight;
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    const font = (weight, size) => `${Math.round(weight / 10) * 10} ${size}px "${FONT}"`;
    let seed = 23;   // misma composición en cada visita con la misma pantalla
    const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;

    // ---------- logo: «monA» con «Bank» debajo, centrado ----------
    const S = Math.min(W * 0.86 / 2.25, H * 0.33, 270);
    const KIND = S * 0.3;
    const logo = [...'monA'].map((ch, i) => ({ ch, size: i < 3 ? S * MON : S, weight: 700, color: COLORS.paper }))
        .concat([...'Bank'].map((ch) => ({ ch, size: KIND, weight: 400, color: COLORS.mist })));
    const probe = document.createElement('canvas');
    const px = probe.getContext('2d', { willReadFrequently: true });
    const glyphOf = (ch, size) => {   // la letra sola, para analizarla píxel a píxel
        px.font = font(700, size);
        const advance = px.measureText(ch).width;
        const w = Math.ceil(advance) + 4;
        const h = Math.ceil(size * 1.3);
        probe.width = w;
        probe.height = h;
        px.font = font(700, size);
        px.fillStyle = '#fff';
        const base = Math.round(size);
        px.fillText(ch, 2, base);
        const data = px.getImageData(0, 0, w, h).data;
        return { w, h, base, advance, ink: (x, y) => x >= 0 && y >= 0 && x < w && y < h && data[(y * w + x) * 4 + 3] > 128 };
    };
    const glyphs = logo.slice(0, 4).map((l) => glyphOf(l.ch, l.size));
    ctx.font = font(400, KIND);
    const kindWidth = ctx.measureText('Bank').width;
    const nameWidth = glyphs.reduce((s, g) => s + g.advance, 0);
    const T = strokeThickness(glyphs[1]);
    const leg = leftLeg(glyphs[3], T);
    const above = glyphs[3].base - leg.top + S * ARROW + S * 0.2;   // flecha encima, «Bank» debajo
    const base = Math.round(H / 2 + (above - KIND * 1.25) / 2);
    let pen = Math.round((W - nameWidth) / 2);
    logo.slice(0, 4).forEach((l, i) => { l.x = pen; l.y = base; pen += glyphs[i].advance; });
    let kindPen = pen - kindWidth;
    logo.slice(4).forEach((l) => { l.x = kindPen; l.y = base + KIND * 1.2; kindPen += ctx.measureText(l.ch).width; });
    const at = (i) => ([x, y]) => [logo[i].x - 2 + x, base - glyphs[i].base + y];

    // ---------- la gráfica, en coordenadas de pantalla ----------
    const flat = [0, 1].flatMap((i) => topLine(glyphs[i], T).map(at(i)));
    const nTop = topLine(glyphs[2], T, 0.8);
    const drop = [...nTop, ...rightStem(glyphs[2], nTop.at(-1)[1], T)].map(at(2));
    const legBottom = glyphs[3].base + 2;
    const shape = (c, points) => {
        c.beginPath();
        points.forEach(([x, y], i) => (i ? c.lineTo(x, y) : c.moveTo(x, y)));
    };
    const layer = document.createElement('canvas');
    layer.width = canvas.width;
    layer.height = canvas.height;
    const lx = layer.getContext('2d');
    const drawLogo = (pFlat, pDrop, pLeg, pArrow) => {
        lx.setTransform(1, 0, 0, 1, 0, 0);
        lx.clearRect(0, 0, layer.width, layer.height);
        lx.setTransform(dpr, 0, 0, dpr, 0, 0);
        logo.slice(0, 4).forEach((l) => { lx.font = font(l.weight, l.size); lx.fillStyle = l.color; lx.fillText(l.ch, l.x, l.y); });
        lx.save();
        lx.globalCompositeOperation = 'source-atop';   // solo dentro de las letras
        lx.lineWidth = T * 1.4;
        lx.lineJoin = 'round';
        lx.lineCap = 'butt';
        if (pFlat > 0) { lx.strokeStyle = COLORS.flat; shape(lx, partial(flat, pFlat)); lx.stroke(); }
        if (pDrop > 0) { lx.strokeStyle = COLORS.drop; shape(lx, partial(drop, pDrop)); lx.stroke(); }
        if (pLeg > 0) {   // la pata se llena de abajo arriba
            lx.fillStyle = COLORS.rise;
            shape(lx, legPolygon(leg, legBottom, legBottom + (leg.top - legBottom) * pLeg).map(at(3)));
            lx.fill();
        }
        lx.restore();
        if (pArrow > 0) {   // el asta crece desde el vértice y la punta aparece al final
            const { shaft, head } = arrow(leg, S * ARROW * Math.min(1, pArrow / 0.7), clamp((pArrow - 0.55) / 0.45));
            lx.fillStyle = COLORS.rise;
            shape(lx, shaft.map(at(3)));
            lx.fill();
            if (pArrow > 0.55) { shape(lx, head.map(at(3))); lx.fill(); }
        }
        logo.slice(4).forEach((l) => { lx.font = font(l.weight, l.size); lx.fillStyle = l.color; lx.fillText(l.ch, l.x, l.y); });
    };

    // ---------- palabras: pegadas entre sí, con tamaños muy dispares y giros fuertes ----------
    const unit = Math.min(Math.sqrt(W * H), W * 1.1);
    const portrait = H > W * 1.2;
    const items = WORDS.flatMap((list, li) => list.map((text) => ({ text, li })));
    const range = [H > W ? [0.25, 0.29] : [0.2, 0.23], [0.17, 0.2], [0.1, 0.12], [0.05, 0.065]];
    const deg = Math.PI / 180;
    items.forEach((it) => {
        it.tier = TIER[it.text];
        const [lo, hi] = range[it.tier];
        it.size = unit * (lo + rnd() * (hi - lo));
        it.weight = it.tier === 0 ? 900 : [400, 600, 800][Math.floor(rnd() * 3)];
        ctx.font = font(it.weight, it.size);
        it.width = ctx.measureText(it.text).width;
        const r = rnd();
        if (it.tier === 0) {
            it.angle = (portrait ? (rnd() < 0.5 ? -1 : 1) * (55 + rnd() * 20) : (rnd() - 0.5) * 50) * deg;
        } else if (r < (portrait ? 0.45 : 0.25)) {
            it.angle = (rnd() < 0.5 ? -1 : 1) * (80 + rnd() * 10) * deg;   // casi verticales
        } else {
            it.angle = (rnd() - 0.5) * 110 * deg;
        }
        // que quepa entera ya girada (en vertical, las grandes aprovechan el alto)
        const pc = Math.abs(Math.cos(it.angle));
        const ps = Math.abs(Math.sin(it.angle));
        const fit = Math.min(1, W * 0.95 / (it.width * pc + it.size * ps), H * 0.9 / (it.width * ps + it.size * pc));
        if (fit < 1) { it.size *= fit; ctx.font = font(it.weight, it.size); it.width = ctx.measureText(it.text).width; }
        it.offsets = [...it.text].map((_, k) => ctx.measureText(it.text.slice(0, k)).width);
    });
    items.sort((a, b) => a.tier - b.tier);   // las enormes, al fondo
    const circlesOf = (it, x, y) => {
        const r = it.size * 0.4;
        const k = Math.max(2, Math.ceil(it.width / (r * 1.15)));
        const c = Math.cos(it.angle);
        const s = Math.sin(it.angle);
        return Array.from({ length: k }, (_, j) => {
            const along = (j + 0.5) * it.width / k;
            const down = it.size * 0.45;
            return [x + along * c - down * s, y + along * s + down * c, r];
        });
    };
    const CELL = 28;
    const cols = Math.ceil(W / CELL);
    const rows = Math.ceil(H / CELL);
    const covered = new Uint8Array(cols * rows);
    const cellsOf = (circles) => {
        const cells = new Set();
        circles.forEach(([cx, cy, r]) => {
            for (let gx = Math.floor((cx - r) / CELL); gx <= Math.floor((cx + r) / CELL); gx++) {
                for (let gy = Math.floor((cy - r) / CELL); gy <= Math.floor((cy + r) / CELL); gy++) {
                    if (gx >= 0 && gy >= 0 && gx < cols && gy < rows) cells.add(gy * cols + gx);
                }
            }
        });
        return cells;
    };
    const M = 12;
    const placed = [];
    items.forEach((it, idx) => {
        const c = Math.cos(it.angle);
        const sn = Math.sin(it.angle);
        const box = [[0, 0], [it.width, 0], [0, it.size], [it.width, it.size]].map(([a, b]) => [a * c - b * sn, a * sn + b * c]);
        const bx0 = M - Math.min(...box.map((p) => p[0]));
        const bx1 = W - M - Math.max(...box.map((p) => p[0]));
        const by0 = M - Math.min(...box.map((p) => p[1]));
        const by1 = H - M - Math.max(...box.map((p) => p[1]));
        const span = (a, b, f) => (b > a ? a + (b - a) * f : (a + b) / 2);
        let best = null;
        // pasadas: estricta; sin exigir que toque a otra; con más solape; y, como último recurso, el menor solape
        for (let pass = 0; pass < 4 && !best; pass++) {
            for (let tries = 0; tries < 1500; tries++) {
                // solo posiciones en las que cabe entera; la primera, en una franja que deja sitio a la otra enorme
                const x = span(bx0, bx1, idx === 0 ? rnd() * 0.3 : rnd());
                const y = span(by0, by1, idx === 0 ? (H > W ? 0.3 + rnd() * 0.4 : rnd() * 0.25) : rnd());
                const mine = circlesOf(it, x, y);
                let hits = 0;
                let worst = 0;
                for (const p of placed) {
                    const limit = (p.tier === it.tier ? 0.35 : p.tier === 0 ? 1.25 : 0.85) * (pass === 2 ? 1.4 : 1);
                    for (const [qx, qy, qr] of p.circles) {
                        for (const [mx, my, mr] of mine) {
                            const depth = (qr + mr - Math.hypot(qx - mx, qy - my)) / Math.min(qr, mr);
                            if (depth > 0) { hits++; worst = Math.max(worst, depth / limit); }
                        }
                    }
                }
                if (worst > 1 && pass < 3) continue;
                // ninguna inicial queda tapada: ni la de esta palabra ni las de las ya colocadas
                const [ix, iy, ir] = mine[0];
                if (placed.some((p) => p.circles.some(([qx, qy, qr]) => Math.hypot(qx - ix, qy - iy) < (qr + ir) * 0.8))) continue;
                if (placed.some((p) => mine.some(([mx, my, mr]) => Math.hypot(p.circles[0][0] - mx, p.circles[0][1] - my) < (p.circles[0][2] + mr) * 0.8))) continue;
                if (pass === 0 && idx > 0 && hits === 0) continue;   // acopladas: cada palabra toca al menos a otra
                const cells = cellsOf(mine);
                let fresh = 0;
                cells.forEach((cell) => { if (!covered[cell]) fresh++; });
                const score = fresh * 1.6 + Math.min(hits, 4) * 3 + rnd() * 6 - (pass === 3 ? worst * 200 : 0);
                if (!best || score > best.score) best = { x, y, score, circles: mine, cells };
                if (tries > 500 && best && pass < 3) break;
            }
        }
        best.cells.forEach((cell) => { covered[cell] = 1; });
        Object.assign(it, best);
        placed.push(it);
    });
    items.forEach((it) => {
        it.to = logo[it.li];
        const b = it.size * 0.8;   // de la esquina de la caja a la línea base de la palabra
        it.ox = it.x - b * Math.sin(it.angle);
        it.oy = it.y + b * Math.cos(it.angle);
        it.enter = rnd() * 330;
        it.erase = WORDS_HOLD + 640 + rnd() * 140;
        it.fly = WORDS_HOLD + 950 + it.li * 30 + rnd() * 60;
        it.alpha = [0.36, 0.62, 0.84, 0.95][it.tier];
    });

    // ---------- guion (ms) ----------
    //    0– 600  entran las palabras           600–1600  las palabras, quietas y enteras
    // 1640–2000  se borran las terminaciones
    // 1950–2840  las iniciales vuelan al logo: se enderezan y cambian de tamaño, de peso y de mayúscula a minúscula
    // 2860–3480  la gráfica: gris (m, o) → caída roja (n) → subida turquesa (A) → flecha
    // 3480–4650  el logo, quieto               4650–5000  la capa se desvanece
    const landed = Math.max(...items.map((it) => it.fly)) + 620;
    const words = (t) => {
        items.forEach((it) => {   // la palabra (y su inicial, mientras no despegue)
            const enter = easeOut(clamp((t - it.enter) / 260));
            if (enter <= 0) return;
            const n = it.text.length;
            ctx.save();
            ctx.translate(it.ox, it.oy);
            ctx.rotate(it.angle);
            ctx.translate(0, (1 - enter) * it.size * 0.3);
            ctx.font = font(it.weight, it.size);
            ctx.fillStyle = COLORS.mist;
            [...it.text].forEach((ch, k) => {
                if (k === 0) {
                    if (t < it.fly) { ctx.globalAlpha = enter * it.alpha; ctx.fillText(ch, 0, 0); }
                    return;
                }
                const e = easeOut(clamp((t - it.erase - (n - 1 - k) * 22) / 150));
                if (e >= 1) return;
                ctx.globalAlpha = enter * it.alpha * (1 - e);
                ctx.fillText(ch, it.offsets[k], e * it.size * 0.22);
            });
            ctx.restore();
        });
        items.forEach((it) => {   // iniciales en vuelo
            if (t < it.fly) return;
            const f = easeInOutQuart(clamp((t - it.fly) / 620));
            const tg = it.to;
            ctx.save();
            ctx.translate(it.ox + (tg.x - it.ox) * f, it.oy + (tg.y - it.oy) * f);
            ctx.rotate(it.angle * (1 - f));
            ctx.font = font(it.weight + (tg.weight - it.weight) * f, it.size + (tg.size - it.size) * f);
            const alpha = it.alpha + (1 - it.alpha) * f;
            const swap = clamp((f - 0.35) / 0.3);
            const from = it.text[0];
            if (from !== tg.ch) { ctx.globalAlpha = alpha * (1 - swap); ctx.fillStyle = COLORS.paper; ctx.fillText(from, 0, 0); }
            ctx.globalAlpha = from === tg.ch ? alpha : alpha * swap;
            ctx.fillStyle = tg.color;
            ctx.fillText(tg.ch, 0, 0);
            ctx.restore();
        });
    };
    return (t) => {
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.textBaseline = 'alphabetic';
        if (t < landed) {
            words(t);
        } else {
            const step = (from, length, ease = easeInOut) => ease(clamp((t - landed - 20 - from) / length));
            drawLogo(step(0, 200), step(200, 150), step(350, 150), step(500, 120, easeOut));
            ctx.globalAlpha = 1;
            ctx.setTransform(1, 0, 0, 1, 0, 0);
            ctx.drawImage(layer, 0, 0);
        }
        splash.style.opacity = String(1 - easeInOut(clamp((t - (DURATION - FADE)) / FADE)));
    };
};

const play = async () => {
    if (!splash || !canvas || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    splash.hidden = false;   // cuanto antes, para que no se vean las puertas antes de la intro
    let frame = 0;
    const finish = () => { cancelAnimationFrame(frame); splash.remove(); };
    if (!(await fontReady())) { finish(); return; }
    const render = stage();
    let start = null;
    const tick = (now) => {
        start ??= now;
        const t = now - start;
        render(Math.min(t, DURATION));
        if (t < DURATION) frame = requestAnimationFrame(tick); else finish();
    };
    frame = requestAnimationFrame(tick);
    splash.addEventListener('click', finish, { once: true });
    document.addEventListener('keydown', (event) => { if (event.key === 'Escape') finish(); });
};

play();
