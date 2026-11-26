package com.dawnrock.ddd.adapter.in.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.dawnrock.ddd.adapter.in.Auth0Properties;
import com.dawnrock.ddd.adapter.in.ExternalIdentities;

/**
 * Los tokens de acceso de Auth0 en la API (#197, ADR 0024), solo con {@code APP_AUTH_AUTH0_DOMAIN} y
 * {@code APP_AUTH_AUTH0_AUDIENCE} ({@link Auth0ApiConfigured}). El decodificador valida RS256 con las claves públicas
 * del tenant ({@code /.well-known/jwks.json}), que Nimbus pide en el primer token de Auth0 y guarda en caché: arrancar
 * no llama a Auth0.
 */
@Configuration
@Conditional(Auth0ApiTokensConfig.Auth0ApiConfigured.class)
public class Auth0ApiTokensConfig {

    /** Nombre del bean del decodificador de Auth0; lo busca {@link ApiSecurityConfig} y lo sustituyen los tests. */
    public static final String DECODER = "auth0JwtDecoder";

    @Bean(DECODER)
    public JwtDecoder auth0JwtDecoder(Auth0Properties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.baseUrl() + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(Auth0AccessTokens.validator(properties.issuerUri(), properties.audience()));
        return decoder;
    }

    @Bean
    public Auth0AccessTokens auth0AccessTokens(ExternalIdentities identities, UserDetailsService users) {
        return new Auth0AccessTokens(identities, users);
    }

    /**
     * Dominio y audiencia con texto. No es {@code @ConditionalOnProperty}: ese toma {@code ""} como presente, y una
     * variable de entorno vacía en Render no debe abrir la API a Auth0.
     */
    static class Auth0ApiConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return hasText(context, "domain") && hasText(context, "audience");
        }

        private static boolean hasText(ConditionContext context, String key) {
            String value = context.getEnvironment().getProperty("app.auth.auth0." + key);
            return value != null && !value.isBlank();
        }
    }
}
