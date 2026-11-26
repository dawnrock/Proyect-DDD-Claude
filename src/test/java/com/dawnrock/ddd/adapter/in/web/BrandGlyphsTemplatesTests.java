package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Glifos de la marca (#185, ADR 0023): la subida de la A, la caída de la n, la A de marca de agua y la A gigante de
 * fondo salen de los contornos del logo. Son decoración (el texto de al lado ya dice qué es) y toman el color del CSS.
 */
class BrandGlyphsTemplatesTests {

    private static final Pattern FRAGMENT = Pattern.compile(
            "<(svg|div) th:fragment=\"(\\w+)\"[^>]*>.*?</\\1>", Pattern.DOTALL);

    @ParameterizedTest
    @ValueSource(strings = {"rise", "drop", "watermark", "backdrop"})
    @DisplayName("Cada glifo de la marca existe, es decorativo para los lectores de pantalla y no fija ningún color")
    void eachBrandGlyphIsDecorativeAndTakesItsColourFromTheStyles(String name) throws IOException {
        String fragment = fragment(name);

        assertThat(fragment).contains("aria-hidden=\"true\"");
        assertThat(fragment).doesNotContainPattern("(fill|stroke)=\"#");
        assertThat(fragment).doesNotContain("style=");
    }

    private static String fragment(String name) throws IOException {
        String html = new ClassPathResource("templates/fragments/brand.html").getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = FRAGMENT.matcher(html);
        while (matcher.find()) {
            if (matcher.group(2).equals(name)) {
                return matcher.group();
            }
        }
        throw new AssertionError("No hay fragmento «" + name + "» en fragments/brand.html");
    }
}
