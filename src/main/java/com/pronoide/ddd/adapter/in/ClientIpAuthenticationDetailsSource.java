package com.pronoide.ddd.adapter.in;

import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Detalles de una autenticación con la IP del cliente que da {@link ClientIpResolver} (#133, ADR 0017), en lugar de la
 * dirección de la conexión, que en Render es la de un proxy. La usan el inicio de sesión de la web y la emisión de
 * tokens de la API; {@link ThrottledAuthenticationProvider} la lee para el límite de intentos. La sesión se conserva
 * como en los detalles por defecto de Spring Security.
 */
public class ClientIpAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> {

    private final ClientIpResolver resolver;

    public ClientIpAuthenticationDetailsSource(ClientIpResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public WebAuthenticationDetails buildDetails(HttpServletRequest request) {
        // Varias cabeceras X-Forwarded-For se unen en orden: la primera puede escribirla el cliente, y la que añade un
        // proxy de confianza va después
        String forwardedFor = String.join(",", Collections.list(request.getHeaders("X-Forwarded-For")));
        String ip = resolver.resolve(request.getRemoteAddr(), forwardedFor.isEmpty() ? null : forwardedFor);
        HttpSession session = request.getSession(false);
        return new WebAuthenticationDetails(ip, session == null ? null : session.getId());
    }
}
