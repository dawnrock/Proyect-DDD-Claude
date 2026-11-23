package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Errores accesibles (#145): todo campo que la web puede marcar como erróneo ({@code th:errorclass} o
 * {@code is-invalid}) lleva {@code aria-invalid} y {@code aria-describedby} hacia su mensaje, y todo mensaje de error de
 * campo tiene un id. Recorre las plantillas para que ningún formulario nuevo se quede fuera.
 */
class AccessibleFieldErrorsTemplatesTests {

    private static final Pattern FIELD = Pattern.compile("<(?:input|select)\\b[^>]*>");

    private static final Pattern FEEDBACK = Pattern.compile("<p class=\"invalid-feedback\"[^>]*>");

    @Test
    @DisplayName("Todo campo que puede marcarse como erróneo lleva aria-invalid y aria-describedby, y su mensaje un id")
    void todo_campo_que_puede_marcarse_como_erroneo_lleva_aria_invalid_y_aria_describedby() throws IOException {
        Resource[] templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/**/*.html");
        int checked = 0;
        List<String> problems = new ArrayList<>();
        for (Resource template : templates) {
            String html = template.getContentAsString(StandardCharsets.UTF_8);
            Matcher field = FIELD.matcher(html);
            while (field.find()) {
                String tag = field.group();
                if (tag.contains("th:errorclass") || tag.contains("is-invalid")) {
                    checked++;
                    if (!tag.contains("th:aria-invalid=") || !tag.contains("th:aria-describedby=")) {
                        problems.add(template.getFilename() + ": " + tag);
                    }
                }
            }
            Matcher feedback = FEEDBACK.matcher(html);
            while (feedback.find()) {
                if (!feedback.group().contains(" id=\"")) {
                    problems.add(template.getFilename() + ": " + feedback.group());
                }
            }
        }

        assertThat(checked).as("campos revisados").isGreaterThanOrEqualTo(20);
        assertThat(problems).as("campos o mensajes sin enlazar").isEmpty();
    }
}
