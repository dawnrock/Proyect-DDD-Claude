package com.dawnrock.ddd.adapter.in;

import java.util.Optional;

/**
 * Las Content-Security-Policy de la aplicación (#206, ADR 0025), como texto para la cabecera. Vive en la raíz de
 * {@code adapter.in}, con el resto del soporte de seguridad, porque la usan las dos cadenas (la web y la API).
 *
 * <ul>
 *   <li>{@link #web}: solo recursos propios; el único script en línea permitido es el que lleva el nonce de la petición
 *       (el import map del login). {@code data:} en imágenes porque el CSS de Bootstrap dibuja iconos como SVG
 *       {@code data:}. {@code form-action} incluye Auth0 porque Chromium también la aplica a la redirección que sigue
 *       a un formulario (cerrar sesión y el alta con Auth0 terminan en el tenant).</li>
 *   <li>{@link #apiDocs}: Swagger UI se carga de jsDelivr, pinta estilos en línea y, con Auth0, pide el token al
 *       tenant (PKCE).</li>
 *   <li>{@link #api}: la API solo devuelve JSON: no carga nada ni se puede enmarcar (OWASP REST Security Cheat
 *       Sheet).</li>
 * </ul>
 */
public final class ContentSecurityPolicies {

    private static final String JSDELIVR = "https://cdn.jsdelivr.net";

    private ContentSecurityPolicies() {
    }

    /**
     * @param nonce       el nonce de esta petición ({@code CspNonce})
     * @param auth0Origin el origen de Auth0 ({@code https://<dominio>}), si está configurado
     */
    public static String web(String nonce, Optional<String> auth0Origin) {
        return "default-src 'self'; script-src 'self' 'nonce-" + nonce + "'; style-src 'self'; img-src 'self' data:; "
                + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'; "
                + "form-action 'self'" + auth0Origin.map(origin -> " " + origin).orElse("");
    }

    /** @param auth0Origin el origen de Auth0 ({@code https://<dominio>}), si está configurado */
    public static String apiDocs(Optional<String> auth0Origin) {
        return "default-src 'self'; script-src 'self' " + JSDELIVR + "; style-src 'self' " + JSDELIVR
                + " 'unsafe-inline'; img-src 'self' data:; connect-src 'self'"
                + auth0Origin.map(origin -> " " + origin).orElse("")
                + "; object-src 'none'; base-uri 'none'; frame-ancestors 'none'";
    }

    public static String api() {
        return "default-src 'none'; frame-ancestors 'none'";
    }
}
