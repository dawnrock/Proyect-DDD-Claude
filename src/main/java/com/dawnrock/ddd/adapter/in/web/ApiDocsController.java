package com.dawnrock.ddd.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dawnrock.ddd.adapter.in.Auth0Properties;

import lombok.RequiredArgsConstructor;

/**
 * Documentación interactiva de la API (#111): Swagger UI sobre el contrato escrito a mano ({@code /openapi.yaml}, que
 * el build copia de {@code docs/api/openapi.yaml}). La página es estática ({@code static/api-docs/index.html}); aquí
 * se da una URL corta y, desde #197 (ADR 0024), lo que necesita para iniciar sesión con Auth0 como cliente público con
 * PKCE: su configuración ({@code /api-docs/oauth-config}) y un documento de descubrimiento OpenID
 * ({@code /api-docs/openid-configuration}) construido con el dominio del tenant, al que apunta el esquema {@code auth0}
 * del contrato, que así no lleva el tenant escrito. Todo es público y nada es secreto.
 */
@Controller
@RequiredArgsConstructor
public class ApiDocsController {

    private final Auth0Properties auth0;

    @GetMapping("/api-docs")
    public String apiDocs() {
        return "redirect:/api-docs/index.html";
    }

    /**
     * Lo que Swagger UI necesita para «Authorize» con Auth0: el cliente público (SPA) y la audiencia de la API. Sin
     * tenant, audiencia o cliente, {@code enabled} es {@code false} y la página funciona como antes.
     */
    @GetMapping("/api-docs/oauth-config")
    @ResponseBody
    public Map<String, Object> oauthConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        boolean enabled = auth0.trustsApiTokens() && !auth0.apiDocsClientId().isEmpty();
        config.put("enabled", enabled);
        if (enabled) {
            config.put("clientId", auth0.apiDocsClientId());
            config.put("audience", auth0.audience());
        }
        return config;
    }

    /**
     * Documento de descubrimiento mínimo del tenant para el esquema {@code openIdConnect} del contrato: solo
     * Authorization Code con PKCE (S256). Se construye con el dominio, sin llamar a Auth0. Sin Auth0 para la API, 404.
     */
    @GetMapping("/api-docs/openid-configuration")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> openIdConfiguration() {
        if (!auth0.trustsApiTokens()) {
            return ResponseEntity.notFound().build();
        }
        String base = auth0.baseUrl();
        Map<String, Object> discovery = new LinkedHashMap<>();
        discovery.put("issuer", auth0.issuerUri());
        discovery.put("authorization_endpoint", base + "/authorize");
        discovery.put("token_endpoint", base + "/oauth/token");
        discovery.put("jwks_uri", base + "/.well-known/jwks.json");
        discovery.put("response_types_supported", List.of("code"));
        discovery.put("grant_types_supported", List.of("authorization_code"));
        discovery.put("code_challenge_methods_supported", List.of("S256"));
        discovery.put("scopes_supported", List.of("openid", "profile", "email"));
        return ResponseEntity.ok(discovery);
    }
}
