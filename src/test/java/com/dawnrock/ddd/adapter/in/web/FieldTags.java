package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba en el HTML pintado cómo queda un campo de formulario con o sin error (#145): un campo con error lleva
 * {@code aria-invalid="true"} y {@code aria-describedby} con el id de su mensaje; sin error, ninguno de los dos.
 */
final class FieldTags {

    private FieldTags() {
    }

    /** Etiqueta de apertura del {@code <input>} o {@code <select>} con ese id. */
    static String tag(String html, String id) {
        Matcher matcher = Pattern.compile("<(?:input|select)\\b[^>]*\\bid=\"" + Pattern.quote(id) + "\"[^>]*>")
                .matcher(html);
        assertThat(matcher.find()).as("campo con id %s", id).isTrue();
        return matcher.group();
    }

    /** El campo está marcado como erróneo y apunta a su mensaje, que existe con ese id. */
    static void assertInvalid(String html, String id) {
        String tag = tag(html, id);
        assertThat(tag).as("campo %s", id)
                .contains("aria-invalid=\"true\"")
                .contains("aria-describedby=\"" + id + "-error\"");
        assertThat(html).contains("id=\"" + id + "-error\"");
    }

    /** El campo no lleva marcas de error (puede describirlo un texto de ayuda, pero no un mensaje de error). */
    static void assertValid(String html, String id) {
        assertThat(tag(html, id)).as("campo %s", id)
                .doesNotContain("aria-invalid")
                .doesNotContainPattern("aria-describedby=\"[^\"]*-error");
    }

    /**
     * El campo de contraseña tiene su botón «Mostrar contraseña» (#146): un {@code <button type="button">} que controla
     * el campo, empieza sin pulsar y está oculto hasta que {@code app.js} lo muestra (#206, sin Alpine.js), así que sin
     * JavaScript no aparece. Ni el botón ni el campo llevan atributos de Alpine ni estilos en línea (CSP).
     */
    static void assertPasswordToggle(String html, String id) {
        Matcher matcher = Pattern.compile("<button\\b[^>]*\\baria-controls=\"" + Pattern.quote(id) + "\"[^>]*>(.*?)</button>",
                Pattern.DOTALL).matcher(html);
        assertThat(matcher.find()).as("botón de mostrar la contraseña %s", id).isTrue();
        assertThat(matcher.group()).as("botón de %s", id)
                .contains("type=\"button\"")
                .contains("data-password-toggle")
                .contains(" hidden")
                .contains("aria-pressed=\"false\"")
                .contains("#eye\"")
                .contains("#eye-off\"")
                .contains("Mostrar contraseña")
                .doesNotContain("x-")
                .doesNotContain("style=");
        assertThat(tag(html, id)).as("campo %s", id)
                .contains("type=\"password\"")
                .doesNotContain("value=")
                .doesNotContain("x-bind");
        assertThat(html).as("sin componentes de Alpine").doesNotContain("x-data=\"passwordToggle\"");
    }
}
