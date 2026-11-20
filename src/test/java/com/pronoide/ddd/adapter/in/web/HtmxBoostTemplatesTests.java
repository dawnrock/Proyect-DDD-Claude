package com.pronoide.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Navegación con htmx (#126): toda página completa de la web lleva {@code hx-boost} en su {@code <body>}, de modo que una
 * pantalla nueva navega sin recargar sin código propio. Recorre las plantillas para que ninguna se quede fuera.
 */
class HtmxBoostTemplatesTests {

    private static final String BOOSTED_BODY =
            "<body class=\"d-flex flex-column min-vh-100\" hx-boost=\"true\" hx-swap=\"innerHTML transition:true\">";

    @Test
    @DisplayName("Toda página completa lleva hx-boost con transición en su body")
    void toda_pagina_completa_lleva_hx_boost_en_su_body() throws IOException {
        Resource[] templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/**/*.html");
        List<String> fullPages = new ArrayList<>();
        List<String> withoutBoost = new ArrayList<>();
        for (Resource template : templates) {
            String html = template.getContentAsString(StandardCharsets.UTF_8);
            // Una página completa toma la cabecera común; los fragmentos (layout, iconos, progreso) no
            if (html.contains("<head th:replace=\"~{fragments/layout :: head(")) {
                fullPages.add(template.getFilename());
                if (!html.contains(BOOSTED_BODY)) {
                    withoutBoost.add(template.getFilename());
                }
            }
        }

        assertThat(fullPages).hasSizeGreaterThanOrEqualTo(10);
        assertThat(withoutBoost).as("páginas sin hx-boost").isEmpty();
    }
}
