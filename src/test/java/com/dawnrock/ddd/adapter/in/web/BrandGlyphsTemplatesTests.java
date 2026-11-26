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

    /** Contorno de la A, con las coordenadas de static/brand/favicon.svg. */
    private static final String A_OUTLINE = "M358.8 0L323.2 0L354 -140L419.2 -140L450 0L414.4 0L408.6 -30L364.6 -30L358.8 0"
            + "M380.4 -113.4L368.8 -54.2L404.4 -54.2L392.8 -113.4L380.4 -113.4";
    /**
     * Pata verde a gran tamaño. Su borde izquierdo es el contorno exterior de la A; el derecho sigue el lado del hueco
     * (entra en él para que el recorte la deje justo en su borde), cruza el travesaño de esquina a esquina y entra entre las
     * patas. La del logo tiene otra inclinación y, ampliada, dejaba una cuña gris junto al hueco.
     */
    private static final String GREEN_LEG =
            "319.57,2 350.98,-140 385.6,-140 380.4,-113.4 386,-84 368.8,-54.2 364.6,-30 359.8,0 359.8,2";
    /**
     * Asta de la flecha a gran tamaño: prolonga hacia arriba el contorno exterior de la A y el lado del hueco, así que desde
     * su base hasta el travesaño cada borde es una sola recta. La del logo es algo más estrecha y, ampliada, se quebraba.
     */
    private static final String ARROW_SHAFT = "353.76,-139 361.94,-176 392.67,-176 385.42,-139";

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
    @DisplayName("En la A de fondo y en la marca de agua, la pata verde llena la pata izquierda hasta el hueco de la A, sin salirse")
    void theGreenLegFillsTheLeftLegUpToTheHoleOfTheA(String name) throws IOException {
        String fragment = fragment(name);
        Matcher clip = Pattern.compile("<clipPath id=\"([\\w-]+)\"><path d=\"" + Pattern.quote(A_OUTLINE) + "\">")
                .matcher(fragment);

        assertThat(clip.find()).as("clipPath con el contorno de la A").isTrue();
        assertThat(fragment).contains("<polygon points=\"" + GREEN_LEG + "\" clip-path=\"url(#" + clip.group(1) + ")\">");
    }

    @ParameterizedTest
    @ValueSource(strings = {"watermark", "backdrop"})
    @DisplayName("En la A de fondo y en la marca de agua, la flecha sale de la pata sin quiebros: sus bordes siguen las rectas de la A")
    void theArrowContinuesTheLegInStraightLines(String name) throws IOException {
        assertThat(fragment(name)).contains("<polygon points=\"" + ARROW_SHAFT + "\">");
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
