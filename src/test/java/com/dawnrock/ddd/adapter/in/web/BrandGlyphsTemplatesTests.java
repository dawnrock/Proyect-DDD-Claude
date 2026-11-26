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

    /** Contorno de la A y pata verde, con las coordenadas de static/brand/favicon.svg. */
    private static final String A_OUTLINE = "M358.8 0L323.2 0L354 -140L419.2 -140L450 0L414.4 0L408.6 -30L364.6 -30L358.8 0"
            + "M380.4 -113.4L368.8 -54.2L404.4 -54.2L392.8 -113.4L380.4 -113.4";
    private static final String GREEN_LEG = "319.57,2 350.98,-140 384.42,-140 358.7,2";

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

    @ParameterizedTest
    @ValueSource(strings = {"watermark", "backdrop"})
    @DisplayName("En la A de fondo y en la marca de agua, la pata verde va recortada por el contorno de la A, como en el logo")
    void theGreenLegIsClippedByTheOutlineOfTheA(String name) throws IOException {
        String fragment = fragment(name);
        Matcher clip = Pattern.compile("<clipPath id=\"([\\w-]+)\"><path d=\"" + Pattern.quote(A_OUTLINE) + "\">")
                .matcher(fragment);

        assertThat(clip.find()).as("clipPath con el contorno de la A").isTrue();
        assertThat(fragment).contains("<polygon points=\"" + GREEN_LEG + "\" clip-path=\"url(#" + clip.group(1) + ")\">");
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
