package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** El modo realista ({@code app.auth.local=false}, #199) no arranca sin la configuración completa de Auth0. */
class RealisticModeCheckTests {

    private static final String[] WEB = {"app.auth.auth0.domain=tenant.example.auth0.com",
            "app.auth.auth0.client-id=web", "app.auth.auth0.client-secret=secreto"};

    private static final String[] API = {"app.auth.auth0.audience=https://api.monabank.example",
            "app.auth.auth0.api-docs-client-id=spa"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class, RealisticModeCheck.class);

    /** Sin {@code @Configuration} a propósito: el escaneo de los tests de contexto completo no debe recogerla. */
    @EnableConfigurationProperties({Auth0Properties.class, AuthProperties.class})
    static class Properties {
    }

    @Test
    @DisplayName("Sin autenticación local y sin Auth0 la aplicación no arranca y nombra las cinco variables")
    void withoutLocalAuthenticationAndWithoutAuth0TheApplicationDoesNotStart() {
        runner.withPropertyValues("app.auth.local=false").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("app.auth.local=false")
                    .hasMessageContaining("APP_AUTH_AUTH0_DOMAIN")
                    .hasMessageContaining("APP_AUTH_AUTH0_CLIENT_ID")
                    .hasMessageContaining("APP_AUTH_AUTH0_CLIENT_SECRET")
                    .hasMessageContaining("APP_AUTH_AUTH0_AUDIENCE")
                    .hasMessageContaining("APP_AUTH_AUTH0_API_DOCS_CLIENT_ID");
        });
    }

    @Test
    @DisplayName("Sin autenticación local y con solo la web de Auth0 no arranca y nombra la audiencia y el cliente de Swagger UI")
    void withoutLocalAuthenticationAndOnlyTheWebValuesTheApplicationDoesNotStart() {
        runner.withPropertyValues("app.auth.local=false").withPropertyValues(WEB).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("APP_AUTH_AUTH0_AUDIENCE")
                    .hasMessageContaining("APP_AUTH_AUTH0_API_DOCS_CLIENT_ID")
                    .message().doesNotContain("APP_AUTH_AUTH0_DOMAIN");
        });
    }

    @Test
    @DisplayName("Sin autenticación local y con los cinco valores de Auth0 la aplicación arranca")
    void withoutLocalAuthenticationAndTheFiveValuesTheApplicationStarts() {
        runner.withPropertyValues("app.auth.local=false").withPropertyValues(WEB).withPropertyValues(API)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("Con la autenticación local la aplicación arranca sin Auth0")
    void withLocalAuthenticationTheApplicationStartsWithoutAuth0() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }
}
