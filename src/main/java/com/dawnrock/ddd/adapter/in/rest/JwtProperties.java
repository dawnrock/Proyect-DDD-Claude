package com.dawnrock.ddd.adapter.in.rest;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de los tokens JWT de la API ({@code app.jwt.*}, ADR 0015).
 *
 * @param secret secreto HS256 ({@code APP_JWT_SECRET}); al menos 32 bytes. Vacío: clave aleatoria al arrancar
 * @param ttl    duración de un token (15 minutos por defecto)
 * @param issuer emisor ({@code iss}) que se firma y se exige al validar
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

    static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    static final String DEFAULT_ISSUER = "ddd-lab";

    public JwtProperties {
        secret = secret == null ? "" : secret;
        ttl = ttl == null ? DEFAULT_TTL : ttl;
        issuer = issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer;
    }
}
