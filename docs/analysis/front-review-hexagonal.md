# Revisión del frontend web (rama `Hexagonal`)

> Fecha: 2026-11-23. Alcance: `src/main/resources/templates/**`, `static/css/app.css`, `static/js/app.js`, `static/api-docs/index.html` y los formularios de `adapter.in.web`. Revisado contra las guías de la skill `modern-web-guidance` (plugin del mismo nombre). La rama `Monolithic` tiene las mismas plantillas casi idénticas y los mismos hallazgos, pero está congelada en `v1-monolito`: solo se tocaría con permiso expreso.
>
> Esta revisión **no cambia código**: los hallazgos se convierten en una issue y pasan por `/plan-feature` como cualquier otra tarea.

## Lo que ya está bien

| Aspecto | Dónde | Guía |
| ------- | ----- | ---- |
| Mejora progresiva: todo funciona sin JavaScript (`<details>` para las acciones, formularios con `action` y PRG; htmx y Alpine solo añaden) | `detail.html`, `app.js` | — |
| Transiciones entre documentos con `@view-transition`, solo con JavaScript y sin movimiento reducido; `view-transition-name` único por página y asignado en CSS | `app.css` | `consistent-cross-document-transitions` ("DO NOT assign the same `view-transition-name` to multiple elements") |
| Estilos críticos en `<head>` y scripts con `defer` en un orden explícito | `layout.html` | `consistent-cross-document-transitions` |
| Login: `<label for>`, `autocomplete="username"` / `current-password`, `required`, botón con verbo claro ("Entrar"), formulario propio | `login.html` | `autofill-sign-in-form` |
| Alta: un único campo de nombre con `autocomplete="name"`, `type="email"`, `autocomplete="new-password"`, progreso visible con título por paso | `signup/*.html` | `autofill-sign-up-form` ("Use a single name input where possible") |
| Swagger UI desde CDN con versión fija, `integrity` (SRI) y `crossorigin` | `api-docs/index.html` | `secure-external-subresources` |
| Iconos decorativos con `aria-hidden` y `focusable="false"`; el texto va al lado | `icons.html` | — |
| htmx sin caché de historial (no deja saldos ni IBAN en `sessionStorage`) | `layout.html` | — |

## Hallazgos

Ordenados por impacto. "Esfuerzo" es orientativo (S: minutos, M: una tarea de plan).

### 1. Los errores de campo no están ligados al campo para lectores de pantalla (accesibilidad · M)

Cada campo con error recibe la clase `is-invalid` y un `<p class="invalid-feedback">` hermano, pero ni `aria-invalid="true"` ni `aria-describedby` apuntando al mensaje. Un lector de pantalla ve el campo en rojo como un campo normal y no lee el motivo al enfocarlo.

- Afecta a todos los formularios con `th:errorclass` (`accounts/new`, `accounts/another`, las tres acciones de `accounts/detail`, `signup/data`, `signup/access`, `signup/account`).
- Propuesta: dar `id` al mensaje (`<campo>-error`) y añadir al campo `th:aria-invalid="${#fields.hasErrors('x')}"` y `th:aria-describedby="${#fields.hasErrors('x')} ? 'x-error'"` (Thymeleaf no pinta el atributo cuando el valor es `null`). Lo más limpio es un fragmento `fragments/form :: field(...)` que repita el patrón una sola vez.
- Guía `accessible-error-announcement`: "Use `aria-invalid`, and list the error message in `aria-describedby` while the field is invalid". Como los errores llegan del servidor ya tras el envío, aquí no hace falta el puente JS con `:user-invalid` que describe la guía: basta con pintar los atributos.

### 2. Tras un envío fallido el foco no va al primer error (accesibilidad · S)

Los formularios usan `novalidate` (los mensajes los da Spring en español) y el servidor devuelve la página o, en el detalle de cuenta, el panel por htmx. El foco se queda arriba del todo (página completa) o se pierde (el botón enviado desaparece con el panel sustituido), así que quien navega con teclado o lector de pantalla no sabe que hay errores.

- Propuesta: en `app.js`, en `DOMContentLoaded` y en `htmx:afterSettle`, enfocar el primer `[aria-invalid="true"]` (o el primer `.alert[role=alert]` si el error es global). Mejora progresiva: sin JavaScript sigue funcionando como ahora.
- Guía `accessible-error-announcement`: "after a failed submit, move focus to the first invalid field […] if the form uses `novalidate`, move it in your submit handler".

### 3. Faltan `required` en los campos obligatorios (formularios · S)

Solo el login los lleva. Con `novalidate` el navegador no bloquea el envío, pero `required` sigue sirviendo: expone "obligatorio" a los lectores de pantalla y prepara un estilo con `:user-invalid` si algún día se quiere validar en el cliente.

- Obligatorios según las anotaciones de los formularios: depósito inicial (`OpenAccountForm`, `AnotherAccountForm`, `SignUpForm`), importe (`MovementForm`, `TransferForm`), IBAN de destino (`TransferForm`), documento del titular (`OpenAccountForm`), nombre, documento y correo (`SignUpForm` paso 1), usuario y contraseñas (paso 2), aceptar condiciones (paso 3).
- **No** en titular, correo y usuario de `accounts/new`: son opcionales a propósito (cliente existente).
- Guías `autofill-sign-in-form` ("Add the `required` attribute to mandatory fields") y `autofill-sign-up-form`.

### 4. Ajustes de autocompletado y teclado móvil (formularios · S)

- `login.html`: el usuario no es un correo, así que necesita `autocapitalize="none"` y `spellcheck="false"` (el alta ya los lleva). Guía `autofill-sign-in-form`: "`autocorrect` and `autocapitalize` are always disabled for `input type="email"`, but are required for non-email usernames".
- Pasos del alta: `enterkeyhint="next"` en los campos de los pasos 1 y 2 y `enterkeyhint="done"` en el último campo del paso 3. Guía `autofill-sign-up-form` ("Show sign-up progress").
- Documento (DNI/NIE): `autocapitalize="characters"` evita la letra en minúscula en móvil.
- IBAN de destino: añadir `autocapitalize="characters"` e `inputmode="text"` explícito; ya tiene `spellcheck="false"`.

### 5. Mostrar la contraseña (formularios · S)

Ni el login ni el paso 2 del alta permiten ver lo escrito. La guía recomienda un botón **Mostrar contraseña** (un `<button type="button">` con `aria-pressed` que cambia `type` entre `password` y `text`); con Alpine son pocas líneas y sin JS el botón no se pinta. Guía `autofill-sign-in-form`: "You should add a **Show password** toggle".

### 6. Repetir la contraseña en el alta (decisión de producto · para hablar)

La guía `autofill-sign-up-form` desaconseja pedir la contraseña dos veces ("Don't double up inputs"): los gestores de contraseñas ya la sugieren y guardan, y el botón del punto 5 cubre las erratas. Quitar `passwordRepetition` toca el formulario y sus tests, no el contrato de `application`. **Es una decisión del usuario**; no se propone cambiarlo sin acuerdo.

### 7. Rendimiento de las transiciones (mejora opcional · S)

Los scripts son `defer` y nada bloquea el render, lo cual es correcto. Si alguna vez se ve la transición animar hacia una página a medio pintar, la guía `consistent-cross-document-transitions` propone `<link rel="expect" href="#main" blocking="render">` limitado al contenido visible. Hoy no hace falta: se anota solo como referencia.

## Fuera de alcance

- `type="number"` para importes: funciona y da teclado numérico con `inputmode="decimal"`; cambiarlo a texto con patrón exigiría más validación en el servidor y no hay guía que lo pida.
- Swagger UI (`api-docs`): es una página de terceros con SRI; no se revisa su accesibilidad.

## Siguiente paso

Abrir una issue "Accesibilidad y autocompletado de los formularios web" con los hallazgos 1-5 (y el 6 como pregunta), planificarla con `/plan-feature` e implementarla con `/implement-plan` en `Hexagonal`.
