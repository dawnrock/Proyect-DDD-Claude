package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La configuración de Auth0 para la API (#197, ADR 0024): la audiencia de la API y el cliente público de Swagger UI se
 * suman a los tres valores del inicio de sesión web (#196).
 */
class Auth0PropertiesTests {

    private static final String DOMAIN = "tenant.example.auth0.com";

    private static final String AUDIENCE = "https://api.example.com";

    @Test
    @DisplayName("Sin audiencia la API no confía en los tokens de Auth0, aunque la web sí entre con Auth0")
    void withoutAudienceTheApiDoesNotTrustAuth0Tokens() {
        assertThat(new Auth0Properties(DOMAIN, "id", "secreto", null, null).trustsApiTokens()).isFalse();
        assertThat(new Auth0Properties(null, null, null, null, null).trustsApiTokens()).isFalse();
    }

    @Test
    @DisplayName("Con el dominio y la audiencia la API confía en los tokens de Auth0")
    void withDomainAndAudienceTheApiTrustsAuth0Tokens() {
        Auth0Properties properties = new Auth0Properties(DOMAIN, "id", "secreto", " " + AUDIENCE + " ", "spa");

        assertThat(properties.trustsApiTokens()).isTrue();
        assertThat(properties.audience()).isEqualTo(AUDIENCE);
        assertThat(properties.apiDocsClientId()).isEqualTo("spa");
    }

    @Test
    @DisplayName("Una audiencia sin el resto de Auth0 impide arrancar y nombra las variables que faltan")
    void anAudienceAloneFailsNamingTheMissingVariables() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new Auth0Properties(null, null, null, AUDIENCE, null))
                .withMessageContaining("APP_AUTH_AUTH0_DOMAIN")
                .withMessageContaining("APP_AUTH_AUTH0_CLIENT_ID")
                .withMessageContaining("APP_AUTH_AUTH0_CLIENT_SECRET");
    }

    @Test
    @DisplayName("El cliente de Swagger UI sin el resto de Auth0 impide arrancar")
    void anApiDocsClientAloneFails() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new Auth0Properties(null, null, null, null, "spa"))
                .withMessageContaining("APP_AUTH_AUTH0_DOMAIN");
    }

    @Test
    @DisplayName("Para el modo realista no falta nada si están los cinco valores")
    void nothingIsMissingForTheRealisticModeWithTheFiveValues() {
        assertThat(new Auth0Properties(DOMAIN, "id", "secreto", AUDIENCE, "spa").missingForRealisticMode()).isEmpty();
    }

    @Test
    @DisplayName("Para el modo realista, con solo los valores de la web faltan la audiencia y el cliente de Swagger UI")
    void withOnlyTheWebValuesTheRealisticModeMissesTheApiValues() {
        assertThat(new Auth0Properties(DOMAIN, "id", "secreto", null, " ").missingForRealisticMode())
                .containsExactly("APP_AUTH_AUTH0_AUDIENCE", "APP_AUTH_AUTH0_API_DOCS_CLIENT_ID");
    }

    @Test
    @DisplayName("Para el modo realista, sin Auth0 faltan las cinco variables, en orden")
    void withoutAuth0TheRealisticModeMissesTheFiveVariables() {
        assertThat(new Auth0Properties(null, null, null, null, null).missingForRealisticMode())
                .containsExactly("APP_AUTH_AUTH0_DOMAIN", "APP_AUTH_AUTH0_CLIENT_ID", "APP_AUTH_AUTH0_CLIENT_SECRET",
                        "APP_AUTH_AUTH0_AUDIENCE", "APP_AUTH_AUTH0_API_DOCS_CLIENT_ID");
    }
}
