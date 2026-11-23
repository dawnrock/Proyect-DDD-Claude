package com.dawnrock.ddd.adapter.in;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proxies de confianza ({@code app.security.trusted-proxies}, #133, ADR 0017): rangos CIDR, IPv4 o IPv6, de los proxies
 * que hay entre el cliente y la aplicación (el proxy interno de Render y Cloudflare). Solo de ellos se cree la cabecera
 * {@code X-Forwarded-For}. Sin ninguno, la IP es siempre la de la conexión.
 */
@ConfigurationProperties(prefix = "app.security")
public record TrustedProxiesProperties(List<String> trustedProxies) {

    public TrustedProxiesProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
