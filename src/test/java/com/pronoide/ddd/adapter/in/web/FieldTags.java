package com.pronoide.ddd.adapter.in.web;

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

    /** El campo no lleva marcas de error. */
    static void assertValid(String html, String id) {
        assertThat(tag(html, id)).as("campo %s", id)
                .doesNotContain("aria-invalid")
                .doesNotContain("aria-describedby");
    }
}
