package com.pronoide.ddd.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Documentación interactiva de la API (#111): Swagger UI sobre el contrato escrito a mano ({@code /openapi.yaml}, que
 * el build copia de {@code docs/api/openapi.yaml}). La página es estática ({@code static/api-docs/index.html}); aquí
 * solo se da una URL corta. Pública: para usar «Try it out» hace falta autenticarse con HTTP Basic, como en la API.
 */
@Controller
public class ApiDocsController {

    @GetMapping("/api-docs")
    public String apiDocs() {
        return "redirect:/api-docs/index.html";
    }
}
