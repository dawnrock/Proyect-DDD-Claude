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
 * Lo mismo vale para las páginas estáticas, que no pueden llevar ningún script en línea. Lee los ficheros del código
 * fuente, así que vale para cualquier plantilla o página nueva.
 */
class TemplatesCspTests {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path STATIC = Path.of("src/main/resources/static");
    /** Copiada sin cambios de swagger-ui-dist (#111, #197): se sirve con la CSP de Swagger UI, que la permite. */
    private static final Path SWAGGER_OAUTH_REDIRECT = STATIC.resolve("api-docs/oauth2-redirect.html");

    /** Los comentarios de Thymeleaf ({@code <!--/* ... *}{@code /-->}) y de HTML no se pintan: pueden hablar de todo. */
    private static final Pattern COMMENTS = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final Pattern INLINE_STYLE = Pattern.compile("\\s(?:th:)?style=\"");
    private static final Pattern EVENT_HANDLER = Pattern.compile("\\son[a-z]+=\"");
    private static final Pattern ALPINE = Pattern.compile("\\s(?:x-[a-z]+(?::[a-z.]+)?|@[a-z]+)=\"");
    /** Un {@code <script>} sin {@code src} (en línea) que no lleva el nonce de la petición. */
    /** Un {@code <script>} sin {@code src}: en una página estática no hay nonce posible. */
    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>");
    private static final Pattern INLINE_SCRIPT_WITHOUT_NONCE = Pattern.compile("<script(?![^>]*\\b(?:th:)?src=)(?![^>]*\\bth:nonce=)[^>]*>");

    static Stream<Path> templates() throws IOException {
        return html(TEMPLATES);
    }

    static Stream<Path> staticPages() throws IOException {
        return html(STATIC).filter(page -> !page.equals(SWAGGER_OAUTH_REDIRECT));
    }

    private static Stream<Path> html(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
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

    @ParameterizedTest
    @MethodSource("staticPages")
    @DisplayName("Ninguna página estática (Swagger UI) lleva scripts en línea, estilos en línea ni manejadores on*=")
    void noStaticPageHasInlineScriptsStylesOrHandlers(Path page) throws IOException {
        String html = COMMENTS.matcher(Files.readString(page, StandardCharsets.UTF_8)).replaceAll("");

        assertThat(INLINE_SCRIPT.matcher(html).find()).as("script en línea en %s", page).isFalse();
        assertThat(INLINE_STYLE.matcher(html).find()).as("style en línea en %s", page).isFalse();
        assertThat(EVENT_HANDLER.matcher(html).find()).as("manejador on*= en %s", page).isFalse();
    }
}
