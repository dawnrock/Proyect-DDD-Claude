// Comportamiento del frontend web (ADR 0016). Todo es mejora progresiva: sin JavaScript la web funciona igual.
(() => {
    'use strict';

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Transiciones entre páginas (@view-transition en app.css). Chromium cancela algunas, por ejemplo tras enviar un
    // formulario, y rechaza sus promesas: la página se muestra igual, así que el rechazo no debe llegar a la consola.
    const ignoreAbortedTransition = (event) => {
        const transition = event.viewTransition;
        if (transition) {
            [transition.ready, transition.finished, transition.updateCallbackDone]
                .forEach((promise) => promise?.catch(() => {}));
        }
    };
    window.addEventListener('pageswap', ignoreAbortedTransition);
    window.addEventListener('pagereveal', ignoreAbortedTransition);

    // Entrada de la página con Motion: los bloques de <main> aparecen escalonados. No se anima con movimiento reducido.
    document.addEventListener('DOMContentLoaded', () => {
        if (reducedMotion || !window.Motion) {
            return;
        }
        const blocks = document.querySelectorAll('main > *');
        if (blocks.length > 0) {
            Motion.animate(blocks, { opacity: [0, 1], y: [8, 0] },
                { duration: 0.3, ease: 'easeOut', delay: Motion.stagger(0.04) });
        }
    });

    // Acciones del detalle de cuenta: los <details> se abren solos sin JavaScript; con Alpine, abrir uno cierra los demás.
    document.addEventListener('alpine:init', () => {
        Alpine.data('actionPanels', () => ({
            closeOthers(event) {
                const opened = event.target;
                if (!(opened instanceof HTMLDetailsElement) || !opened.open) {
                    return;
                }
                this.$el.querySelectorAll('details[open]').forEach((panel) => {
                    if (panel !== opened) {
                        panel.open = false;
                    }
                });
            },
        }));
    });
})();
