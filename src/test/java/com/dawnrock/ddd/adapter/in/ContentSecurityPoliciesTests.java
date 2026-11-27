package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Las Content-Security-Policy de la aplicación (#206, ADR 0025): la de la web, con un nonce por petición; la de Swagger UI,
 * que carga de jsDelivr; y la de la API, que solo devuelve JSON. El origen de Auth0 solo aparece si está configurado.
 */
class ContentSecurityPoliciesTests {

    private static final Optional<String> AUTH0 = Optional.of("https://tenant.eu.auth0.com");

    @Nested
    @DisplayName("La de la web")
    class Web {

        @Test
        @DisplayName("solo permite recursos propios y, como script en línea, el que lleve el nonce de la petición")
        void onlyAllowsOwnResourcesAndTheNonceScript() {
            assertThat(ContentSecurityPolicies.web("abc123", Optional.empty())).isEqualTo(
                    "default-src 'self'; script-src 'self' 'nonce-abc123'; style-src 'self'; img-src 'self' data:; "
                            + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; "
                            + "frame-ancestors 'self'; form-action 'self'");
        }

        @Test
        @DisplayName("con Auth0, deja que los formularios terminen en su dominio (cerrar sesión y el alta)")
        void withAuth0FormsMayEndOnItsDomain() {
            assertThat(ContentSecurityPolicies.web("abc123", AUTH0))
                    .endsWith("form-action 'self' https://tenant.eu.auth0.com");
        }
    }

    @Nested
    @DisplayName("La de Swagger UI")
    class ApiDocs {

        @Test
        @DisplayName("solo permite de jsDelivr la versión fija de swagger-ui-dist, sus estilos en línea y, con Auth0, pedir el token a su dominio")
        void allowsJsDelivrItsInlineStylesAndAuth0() {
            assertThat(ContentSecurityPolicies.apiDocs(AUTH0)).isEqualTo(
                    "default-src 'self'; script-src 'self' https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.34.1/; "
                            + "style-src 'self' https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.34.1/ 'unsafe-inline'; "
                            + "img-src 'self' data:; "
                            + "connect-src 'self' https://tenant.eu.auth0.com; object-src 'none'; base-uri 'none'; "
                            + "frame-ancestors 'none'");
        }

        @Test
        @DisplayName("sin Auth0 solo se conecta con la propia aplicación")
        void withoutAuth0OnlyConnectsToItself() {
            assertThat(ContentSecurityPolicies.apiDocs(Optional.empty())).contains("connect-src 'self';");
        }
    }

    @Test
    @DisplayName("La de la API no permite cargar nada ni que la enmarquen")
    void theApiAllowsNothing() {
        assertThat(ContentSecurityPolicies.api()).isEqualTo("default-src 'none'; frame-ancestors 'none'");
    }

    @Test
    @DisplayName("Ninguna permite eval, y solo la de Swagger UI permite estilos en línea")
    void noneAllowsEvalAndOnlySwaggerUiInlineStyles() {
        assertThat(ContentSecurityPolicies.web("n", AUTH0)).doesNotContain("unsafe");
        assertThat(ContentSecurityPolicies.api()).doesNotContain("unsafe");
        assertThat(ContentSecurityPolicies.apiDocs(AUTH0)).doesNotContain("unsafe-eval")
                .containsOnlyOnce("'unsafe-inline'");
    }
}
