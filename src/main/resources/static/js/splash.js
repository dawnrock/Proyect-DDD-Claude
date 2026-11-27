// Intro de las puertas del login (#178, ADR 0022): destapa la capa de la intro antes de que se pinte la página, para que
// no asome la bienvenida, solo en una carga completa (tras una navegación con htmx no suena) y sin movimiento reducido.
// Si intro.js no llega a arrancar, la quita. Es un fichero y no un script en línea por la Content-Security-Policy (#206);
// se carga sin defer justo después de la capa, así que se ejecuta mientras el documento aún se está cargando.
if (document.readyState === 'loading' && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
    const splash = document.getElementById('splash');
    splash.hidden = false;
    document.documentElement.dataset.intro = 'playing';
    setTimeout(() => {
        if (!window.monaIntro) { splash.remove(); delete document.documentElement.dataset.intro; }
    }, 2500);
}
