package com.dawnrock.ddd.adapter.in.web;

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
 * Nombre del banco en la web (#178): lo que lee el usuario dice «monA Bank». Recorre las plantillas para que ninguna se
 * quede con el nombre antiguo ni con el de la cabecera anterior.
 */
class BrandNameTemplatesTests {

    @Test
    @DisplayName("Ninguna plantilla escribe «Mon-A Bank» ni «Cuentas corrientes»: el banco es monA Bank")
    void noTemplateUsesTheOldBankName() throws IOException {
        Resource[] templates = new PathMatchingResourcePatternResolver().getResources("classpath:templates/**/*.html");
        List<String> withOldName = new ArrayList<>();
        for (Resource template : templates) {
            String html = template.getContentAsString(StandardCharsets.UTF_8);
            if (html.contains("Mon-A") || html.contains("Cuentas corrientes")) {
                withOldName.add(template.getFilename());
            }
        }

        assertThat(templates).hasSizeGreaterThanOrEqualTo(10);
        assertThat(withOldName).isEmpty();
    }
}
