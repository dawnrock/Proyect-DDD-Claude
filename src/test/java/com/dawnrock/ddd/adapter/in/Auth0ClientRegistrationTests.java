package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

class Auth0ClientRegistrationTests {

    private static final String DOMAIN = "tenant.example.auth0.com";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class, Auth0ClientRegistration.class);

    @Configuration
    @EnableConfigurationProperties({Auth0Properties.class, AuthProperties.class})
    static class Properties {
    }

    @Nested
    @DisplayName("Sin configuración de Auth0")
    class WithoutConfiguration {

        @Test
        @DisplayName("no hay registro de cliente y la aplicación arranca como siempre")
        void thereIsNoClientRegistrationAndTheApplicationStarts() {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
            });
        }

        @Test
        @DisplayName("los valores en blanco cuentan como no configurados")
        void blankValuesCountAsNotConfigured() {
            runner.withPropertyValues("app.auth.auth0.domain=", "app.auth.auth0.client-id= ",
                    "app.auth.auth0.client-secret=").run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
                    });
        }
    }

    @Nested
    @DisplayName("Con los tres valores de Auth0")
    class WithAllValues {

        private final ApplicationContextRunner configured = runner.withPropertyValues(
                "app.auth.auth0.domain=" + DOMAIN, "app.auth.auth0.client-id=mi-id",
                "app.auth.auth0.client-secret=mi-secreto");

        @Test
        @DisplayName("el registro auth0 tiene los endpoints, los scopes y el cierre de sesión de Auth0")
        void theRegistrationHasAuth0EndpointsScopesAndLogout() {
            configured.run(context -> {
                ClientRegistration registration = context.getBean(ClientRegistrationRepository.class)
                        .findByRegistrationId(ExternalIdentities.AUTH0);

                assertThat(registration.getClientId()).isEqualTo("mi-id");
                assertThat(registration.getClientSecret()).isEqualTo("mi-secreto");
                assertThat(registration.getClientAuthenticationMethod())
                        .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
                assertThat(registration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
                assertThat(registration.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
                assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
                ClientRegistration.ProviderDetails provider = registration.getProviderDetails();
                assertThat(provider.getAuthorizationUri()).isEqualTo("https://" + DOMAIN + "/authorize");
                assertThat(provider.getTokenUri()).isEqualTo("https://" + DOMAIN + "/oauth/token");
                assertThat(provider.getUserInfoEndpoint().getUri()).isEqualTo("https://" + DOMAIN + "/userinfo");
                assertThat(provider.getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("sub");
                assertThat(provider.getJwkSetUri()).isEqualTo("https://" + DOMAIN + "/.well-known/jwks.json");
                assertThat(provider.getIssuerUri()).isEqualTo("https://" + DOMAIN + "/");
                assertThat(provider.getConfigurationMetadata())
                        .containsEntry("end_session_endpoint", "https://" + DOMAIN + "/oidc/logout");
            });
        }

        @Test
        @DisplayName("el dominio puede escribirse con https:// y con barra final")
        void theDomainMayCarrySchemeAndTrailingSlash() {
            runner.withPropertyValues("app.auth.auth0.domain=https://" + DOMAIN + "/", "app.auth.auth0.client-id=a",
                    "app.auth.auth0.client-secret=b").run(context -> assertThat(
                            context.getBean(ClientRegistrationRepository.class).findByRegistrationId("auth0")
                                    .getProviderDetails().getIssuerUri()).isEqualTo("https://" + DOMAIN + "/"));
        }
    }

    @Nested
    @DisplayName("Con una configuración parcial")
    class WithPartialConfiguration {

        @Test
        @DisplayName("el arranque falla y nombra las variables que faltan")
        void startupFailsNamingTheMissingVariables() {
            runner.withPropertyValues("app.auth.auth0.domain=" + DOMAIN).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining("APP_AUTH_AUTH0_CLIENT_ID")
                        .hasMessageContaining("APP_AUTH_AUTH0_CLIENT_SECRET")
                        .hasMessageNotContaining("APP_AUTH_AUTH0_DOMAIN");
            });
        }

        @Test
        @DisplayName("sin dominio, el mensaje nombra APP_AUTH_AUTH0_DOMAIN")
        void withoutDomainTheMessageNamesTheDomainVariable() {
            runner.withPropertyValues("app.auth.auth0.client-id=a", "app.auth.auth0.client-secret=b").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("APP_AUTH_AUTH0_DOMAIN");
            });
        }
    }

    @Nested
    @DisplayName("AuthProperties")
    class AuthPropertiesBinding {

        @Test
        @DisplayName("sigue enlazando app.auth.local con las claves app.auth.auth0.* presentes")
        void localStillBindsWithTheAuth0KeysPresent() {
            runner.withPropertyValues("app.auth.local=false", "app.auth.auth0.domain=" + DOMAIN,
                    "app.auth.auth0.client-id=a", "app.auth.auth0.client-secret=b")
                    .run(context -> assertThat(context.getBean(AuthProperties.class).local()).isFalse());
            runner.withPropertyValues("app.auth.auth0.domain=" + DOMAIN, "app.auth.auth0.client-id=a",
                    "app.auth.auth0.client-secret=b")
                    .run(context -> assertThat(context.getBean(AuthProperties.class).local()).isTrue());
        }
    }
}
