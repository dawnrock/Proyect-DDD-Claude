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
 * Navegación con htmx (#126): toda página completa de la web lleva {@code hx-boost} en su {@code <body>}, de modo que una
 * pantalla nueva navega sin recargar sin código propio. Recorre las plantillas para que ninguna se quede fuera.
 */
class HtmxBoostTemplatesTests {

    /** Etiqueta de apertura del body de una plantilla. */
    private static final Pattern BODY = Pattern.compile("<body[^>]*>");

    @Test
    @DisplayName("Toda página completa lleva hx-boost con transición en su body")
    void everyFullPageHasHxBoostInItsBody() throws IOException {
        Resource[] templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/**/*.html");
        List<String> fullPages = new ArrayList<>();
        List<String> withoutBoost = new ArrayList<>();
        for (Resource template : templates) {
            String html = template.getContentAsString(StandardCharsets.UTF_8);
            // Una página completa tiene <main>; los fragmentos (layout, iconos, progreso) no
            Matcher body = BODY.matcher(html);
            if (html.contains("<main") && body.find()) {
                fullPages.add(template.getFilename());
                String tag = body.group();
                if (!tag.contains("hx-boost=\"true\"") || !tag.contains("hx-swap=\"innerHTML transition:true\"")) {
                    withoutBoost.add(template.getFilename());
                }
            }
        }

        assertThat(fullPages).hasSizeGreaterThanOrEqualTo(10);
        assertThat(withoutBoost).as("páginas sin hx-boost").isEmpty();
    }
}
