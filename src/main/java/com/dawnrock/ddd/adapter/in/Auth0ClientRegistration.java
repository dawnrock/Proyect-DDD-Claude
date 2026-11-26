package com.dawnrock.ddd.adapter.in;

import java.time.Clock;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * El cliente OAuth2 de Auth0 (#196, ADR 0024), solo cuando {@code app.auth.auth0.domain}, {@code client-id} y
 * {@code client-secret} tienen valor ({@link Auth0Configured}). El registro se construye a mano a partir del dominio:
 * los endpoints de Auth0 son fijos, así que el arranque y los tests no hacen ninguna llamada de red.
 */
@Configuration
@Conditional(Auth0ClientRegistration.Auth0Configured.class)
public class Auth0ClientRegistration {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(Auth0Properties properties) {
        return new InMemoryClientRegistrationRepository(registration(properties));
    }

    /** Resuelve el {@code sub} de Auth0 al usuario local con los roles de la base de datos. */
    @Bean
    public LinkedOidcUserService linkedOidcUserService(ExternalIdentities identities, UserDetailsService users,
                                                       ObjectProvider<Clock> clock) {
        return new LinkedOidcUserService(new OidcUserService(), identities, users, clock);
    }

    /** El registro {@code auth0} para el tenant de las propiedades. */
    static ClientRegistration registration(Auth0Properties properties) {
        String host = properties.domain().replaceFirst("^https?://", "").replaceFirst("/+$", "");
        String base = "https://" + host;
        return ClientRegistration.withRegistrationId(ExternalIdentities.AUTH0)
                .clientName("Auth0")
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(base + "/authorize")
                .tokenUri(base + "/oauth/token")
                .userInfoUri(base + "/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri(base + "/.well-known/jwks.json")
                .issuerUri(base + "/")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", base + "/oidc/logout"))
                .build();
    }

    /**
     * Los tres valores con texto. No es {@code @ConditionalOnProperty}: ese toma {@code ""} como presente, y una variable
     * de entorno vacía en Render no debe activar Auth0.
     */
    static class Auth0Configured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return hasText(context, "domain") && hasText(context, "client-id") && hasText(context, "client-secret");
        }

        private static boolean hasText(ConditionContext context, String key) {
            String value = context.getEnvironment().getProperty("app.auth.auth0." + key);
            return value != null && !value.isBlank();
        }
    }
}
