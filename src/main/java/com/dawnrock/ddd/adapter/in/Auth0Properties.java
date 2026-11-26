package com.dawnrock.ddd.adapter.in;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cliente de Auth0 ({@code app.auth.auth0.*}, ADR 0024). En Render llegan por el enlace relajado de Spring como
 * {@code APP_AUTH_AUTH0_DOMAIN}, {@code APP_AUTH_AUTH0_CLIENT_ID} y {@code APP_AUTH_AUTH0_CLIENT_SECRET}. Sin ninguno,
 * la aplicación no ofrece Auth0; con solo algunos, no arranca: una configuración a medias es un error, no un
 * "desactivado". No hay valores por defecto vacíos en {@code application.properties}: uno vacío contaría como presente.
 *
 * @param domain       dominio del tenant, por ejemplo {@code mi-tenant.eu.auth0.com}
 * @param clientId     identificador de la aplicación en Auth0
 * @param clientSecret secreto de la aplicación: solo en el entorno, nunca en el repositorio
 */
@ConfigurationProperties(prefix = "app.auth.auth0")
public record Auth0Properties(String domain, String clientId, String clientSecret) {

    /** Cuántos valores hacen falta: dominio, identificador y secreto. */
    private static final int VALUES = 3;

    public Auth0Properties {
        domain = domain == null ? "" : domain.strip();
        clientId = clientId == null ? "" : clientId.strip();
        clientSecret = clientSecret == null ? "" : clientSecret.strip();
        List<String> missing = new ArrayList<>();
        if (domain.isEmpty()) {
            missing.add("APP_AUTH_AUTH0_DOMAIN");
        }
        if (clientId.isEmpty()) {
            missing.add("APP_AUTH_AUTH0_CLIENT_ID");
        }
        if (clientSecret.isEmpty()) {
            missing.add("APP_AUTH_AUTH0_CLIENT_SECRET");
        }
        if (!missing.isEmpty() && missing.size() < VALUES) {
            throw new IllegalStateException("Configuración de Auth0 incompleta: faltan " + String.join(", ", missing));
        }
    }

    /** Si están los tres valores. */
    public boolean isConfigured() {
        return !domain.isEmpty();
    }
}
