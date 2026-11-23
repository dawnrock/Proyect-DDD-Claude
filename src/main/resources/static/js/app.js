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
    const animateEntrance = () => {
        if (reducedMotion || !window.Motion) {
            return;
        }
        const blocks = document.querySelectorAll('main > *');
        if (blocks.length > 0) {
            Motion.animate(blocks, { opacity: [0, 1], y: [8, 0] },
                { duration: 0.3, ease: 'easeOut', delay: Motion.stagger(0.04) });
        }
    };
    document.addEventListener('DOMContentLoaded', animateEntrance);

    // Navegación con hx-boost (#126): htmx sustituye el <body> sin cargar la página, así que la entrada se anima aquí.
    document.addEventListener('htmx:afterSettle', (event) => {
        if (event.detail.target === document.body) {
            animateEntrance();
        }
    });

    // Operaciones de cuenta con htmx (#125): el servidor responde el panel de la cuenta (#account-panel) y htmx lo
    // sustituye dentro de una transición de vista. Con movimiento reducido no hay transición ni animaciones.
    document.addEventListener('htmx:beforeTransition', (event) => {
        if (reducedMotion) {
            event.preventDefault();
        }
    });

    // Tras una operación (el panel trae un aviso de éxito o de rechazo), Motion resalta el saldo y el movimiento nuevo.
    document.addEventListener('htmx:afterSettle', (event) => {
        const panel = document.getElementById('account-panel');
        if (reducedMotion || !window.Motion || !panel || !panel.contains(event.detail.elt)) {
            return;
        }
        if (!panel.querySelector('.alert-success, .alert-warning')) {
            return;
        }
        const balance = panel.querySelector('.display-5');
        if (balance) {
            Motion.animate(balance, { scale: [0.92, 1], opacity: [0.4, 1] }, { duration: 0.4, ease: 'easeOut' });
        }
        const newest = panel.querySelector('.list-group-item');
        if (newest) {
            Motion.animate(newest, { opacity: [0, 1], x: [-12, 0] }, { duration: 0.35, delay: 0.1, ease: 'easeOut' });
        }
    });

    // Errores accesibles (#145): los formularios usan novalidate y el servidor devuelve la página (o el panel de la cuenta)
    // con los errores, así que el navegador no mueve el foco. Se lleva al primer campo erróneo, que el lector de pantalla
    // anuncia con su mensaje (aria-describedby); si el error es del formulario entero, a su aviso. Sin errores, no se toca
    // el foco (gana el autofocus de la página).
    const focusFirstError = () => {
        const field = document.querySelector('[aria-invalid="true"]');
        if (field) {
            field.focus();
            return;
        }
        const alert = document.querySelector('form .alert[role="alert"]');
        if (alert) {
            alert.tabIndex = -1;
            alert.focus();
        }
    };
    document.addEventListener('DOMContentLoaded', focusFirstError);
    document.addEventListener('htmx:afterSettle', focusFirstError);

    // htmx pide HTML, como el navegador al navegar: así un error genérico llega como página y no como JSON (#126).
    document.addEventListener('htmx:configRequest', (event) => {
        event.detail.headers.Accept = 'text/html';
    });

    // htmx no pinta las respuestas de error. Si el servidor devuelve una página de error (las propias 403 y 404, o la de
    // Spring para el resto), sustituye al contenido de la página, como haría el navegador sin JavaScript al navegar a
    // ella, en vez de dejar el clic sin respuesta (#125, ampliado a todos los errores con la navegación de #126).
    document.addEventListener('htmx:beforeSwap', (event) => {
        const { xhr } = event.detail;
        if (xhr.status >= 400 && /<html/i.test(xhr.responseText)) {
            event.detail.shouldSwap = true;
            event.detail.target = document.body;
            event.detail.swapOverride = 'innerHTML';
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
