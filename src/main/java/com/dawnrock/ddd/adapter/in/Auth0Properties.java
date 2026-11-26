package com.dawnrock.ddd.adapter.in;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Auth0 ({@code app.auth.auth0.*}, ADR 0024). En Render llegan por el enlace relajado de Spring como
 * {@code APP_AUTH_AUTH0_DOMAIN}, {@code APP_AUTH_AUTH0_CLIENT_ID}, {@code APP_AUTH_AUTH0_CLIENT_SECRET},
 * {@code APP_AUTH_AUTH0_AUDIENCE} y {@code APP_AUTH_AUTH0_API_DOCS_CLIENT_ID}. Sin ninguno, la aplicación no ofrece
 * Auth0; con solo algunos de los tres del inicio de sesión web, o con la audiencia o el cliente de Swagger UI sin
 * ellos, no arranca: una configuración a medias es un error, no un "desactivado". No hay valores por defecto vacíos en
 * {@code application.properties}: uno vacío contaría como presente.
 *
 * @param domain          dominio del tenant, por ejemplo {@code mi-tenant.eu.auth0.com}
 * @param clientId        identificador de la aplicación web en Auth0 (#196)
 * @param clientSecret    secreto de la aplicación web: solo en el entorno, nunca en el repositorio
 * @param audience        identificador de la API en Auth0 (#197): sin él, la API no acepta tokens de Auth0
 * @param apiDocsClientId identificador de la aplicación pública (SPA) de Swagger UI (#197); no es secreto
 */
@ConfigurationProperties(prefix = "app.auth.auth0")
public record Auth0Properties(String domain, String clientId, String clientSecret, String audience,
                              String apiDocsClientId) {

    /** Cuántos valores hacen falta para el inicio de sesión web: dominio, identificador y secreto. */
    private static final int WEB_VALUES = 3;

    public Auth0Properties {
        domain = stripped(domain);
        clientId = stripped(clientId);
        clientSecret = stripped(clientSecret);
        audience = stripped(audience);
        apiDocsClientId = stripped(apiDocsClientId);
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
        boolean apiValues = !audience.isEmpty() || !apiDocsClientId.isEmpty();
        if (!missing.isEmpty() && (missing.size() < WEB_VALUES || apiValues)) {
            throw new IllegalStateException("Configuración de Auth0 incompleta: faltan " + String.join(", ", missing));
        }
    }

    /** Si la API acepta los tokens de acceso de Auth0: hay tenant y audiencia (#197). */
    public boolean trustsApiTokens() {
        return !domain.isEmpty() && !audience.isEmpty();
    }

    /** La URL del tenant, sin barra final, aunque el dominio se escriba con {@code https://} o con barra. */
    public String baseUrl() {
        return "https://" + domain.replaceFirst("^https?://", "").replaceFirst("/+$", "");
    }

    /** El emisor ({@code iss}) de los tokens del tenant: su URL con barra final, como la escribe Auth0. */
    public String issuerUri() {
        return baseUrl() + "/";
    }

    private static String stripped(String value) {
        return value == null ? "" : value.strip();
    }
}
