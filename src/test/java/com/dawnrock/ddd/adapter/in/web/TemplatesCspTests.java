package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Las plantillas cumplen la Content-Security-Policy de la web (#206, ADR 0025): sin estilos en línea ({@code style-src
 * 'self'} los bloquea), sin manejadores de eventos en atributos ni atributos de Alpine.js (sin {@code 'unsafe-eval'}), y
 * todo {@code <script>} en línea lleva el nonce de la petición ({@code th:nonce}).
 * Lee los ficheros del código fuente, así que vale para cualquier plantilla nueva.
 */
class TemplatesCspTests {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** Los comentarios de Thymeleaf ({@code <!--/* ... *}{@code /-->}) y de HTML no se pintan: pueden hablar de todo. */
    private static final Pattern COMMENTS = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final Pattern INLINE_STYLE = Pattern.compile("\\s(?:th:)?style=\"");
    private static final Pattern EVENT_HANDLER = Pattern.compile("\\son[a-z]+=\"");
    private static final Pattern ALPINE = Pattern.compile("\\s(?:x-[a-z]+(?::[a-z.]+)?|@[a-z]+)=\"");
    /** Un {@code <script>} sin {@code src} (en línea) que no lleva el nonce de la petición. */
    private static final Pattern INLINE_SCRIPT_WITHOUT_NONCE = Pattern.compile("<script(?![^>]*\\b(?:th:)?src=)(?![^>]*\\bth:nonce=)[^>]*>");

    static Stream<Path> templates() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            List<Path> html = files.filter(file -> file.toString().endsWith(".html")).toList();
            assertThat(html).isNotEmpty();
            return html.stream();
        }
    }

    @ParameterizedTest
    @MethodSource("templates")
    @DisplayName("Ninguna plantilla lleva estilos en línea, manejadores on*=, atributos de Alpine.js ni scripts en línea sin nonce")
    void noTemplateBreaksTheContentSecurityPolicy(Path template) throws IOException {
        String html = COMMENTS.matcher(Files.readString(template, StandardCharsets.UTF_8)).replaceAll("");

        assertThat(INLINE_STYLE.matcher(html).find()).as("style en línea en %s", template).isFalse();
        assertThat(EVENT_HANDLER.matcher(html).find()).as("manejador on*= en %s", template).isFalse();
        assertThat(ALPINE.matcher(html).find()).as("atributo de Alpine.js en %s", template).isFalse();
        assertThat(INLINE_SCRIPT_WITHOUT_NONCE.matcher(html).find()).as("script en línea sin nonce en %s", template).isFalse();
    }
}
