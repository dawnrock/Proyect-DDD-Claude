package com.dawnrock.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.dawnrock.ddd.adapter.in.Auth0Properties;
import com.dawnrock.ddd.adapter.in.ExternalIdentities;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * La API solo confía en Auth0 con el dominio y la audiencia (#197, ADR 0024). El decodificador lee las claves públicas
 * del tenant en el primer token, nunca al arrancar, así que el contexto arranca sin red.
 */
class Auth0ApiTokensConfigTests {

    private static final String DOMAIN = "tenant.example.auth0.com";

    private static final String AUDIENCE = "https://api.example.com";

    private static final String WEB = "app.auth.auth0.domain=" + DOMAIN;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Support.class, Auth0ApiTokensConfig.class)
            .withPropertyValues(WEB, "app.auth.auth0.client-id=id", "app.auth.auth0.client-secret=secreto");

    @EnableConfigurationProperties(Auth0Properties.class)
    static class Support {

        @Bean
        ExternalIdentities identities() {
            return mock(ExternalIdentities.class);
        }

        @Bean
        UserDetailsService users() {
            return mock(UserDetailsService.class);
        }
    }

    @Nested
    @DisplayName("Los beans de la API")
    class Beans {

        @Test
        @DisplayName("sin audiencia no hay decodificador de Auth0: la API solo acepta sus propios tokens")
        void withoutAudienceThereIsNoAuth0Decoder() {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(Auth0ApiTokensConfig.DECODER);
                assertThat(context).doesNotHaveBean(Auth0AccessTokens.class);
            });
        }

        @Test
        @DisplayName("con el dominio y la audiencia hay decodificador y conversor, y el contexto arranca sin red")
        void withDomainAndAudienceThereIsADecoderAndAConverter() {
            runner.withPropertyValues("app.auth.auth0.audience=" + AUDIENCE).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean(Auth0ApiTokensConfig.DECODER);
                assertThat(context).hasSingleBean(Auth0AccessTokens.class);
            });
        }
    }

    @Nested
    @DisplayName("Un decodificador con la validación de Auth0")
    class Decoding {

        private final KeyPair keys = rsaKeys();

        private final JwtDecoder decoder = decoder();

        private JwtDecoder decoder() {
            NimbusJwtDecoder nimbus = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
            nimbus.setJwtValidator(Auth0AccessTokens.validator("https://" + DOMAIN + "/", AUDIENCE));
            return nimbus;
        }

        private String token(String issuer, String audience, Instant expiresAt) {
            RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey(keys.getPrivate()).build();
            NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
            JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                    .subject("auth0|ada").issuedAt(expiresAt.minusSeconds(600)).expiresAt(expiresAt).build();
            return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                    .getTokenValue();
        }

        @Test
        @DisplayName("acepta un token firmado del tenant para la API")
        void acceptsASignedTokenOfTheTenantForTheApi() {
            String token = token("https://" + DOMAIN + "/", AUDIENCE, Instant.now().plusSeconds(600));

            assertThat(decoder.decode(token).getSubject()).isEqualTo("auth0|ada");
        }

        @Test
        @DisplayName("rechaza un token firmado para otra API, de otro emisor o caducado")
        void rejectsAnotherAudienceIssuerOrAnExpiredToken() {
            Instant later = Instant.now().plusSeconds(600);

            assertThatExceptionOfType(JwtValidationException.class)
                    .isThrownBy(() -> decoder.decode(token("https://" + DOMAIN + "/", "https://otra", later)));
            assertThatExceptionOfType(JwtValidationException.class)
                    .isThrownBy(() -> decoder.decode(token("https://otro.auth0.com/", AUDIENCE, later)));
            assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(
                    token("https://" + DOMAIN + "/", AUDIENCE, Instant.now().minusSeconds(3600))));
        }
    }

    private static KeyPair rsaKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
