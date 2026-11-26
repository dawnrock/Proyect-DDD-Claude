package com.dawnrock.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import com.dawnrock.ddd.adapter.in.ExternalIdentities;

/**
 * Los tokens de acceso de Auth0 en la API (#197, ADR 0024): se validan el emisor, la audiencia y las fechas, y el
 * {@code sub} se traduce al usuario local con sus roles de la base de datos.
 */
class Auth0AccessTokensTests {

    private static final String ISSUER = "https://tenant.example.auth0.com/";

    private static final String AUDIENCE = "https://api.example.com";

    private static final Instant NOW = Instant.now();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .issuer(ISSUER).audience(List.of(AUDIENCE, ISSUER + "userinfo")).subject("auth0|ada")
                .issuedAt(NOW.minusSeconds(60)).expiresAt(NOW.plusSeconds(600));
    }

    private static void isAnInvalidToken(UnlinkedAccessTokenException e) {
        assertThat(e.getError().getErrorCode()).isEqualTo("invalid_token");
    }

    @Nested
    @DisplayName("El validador")
    class Validator {

        @Test
        @DisplayName("acepta un token del tenant para la audiencia de la API")
        void acceptsATokenOfTheTenantForTheApiAudience() {
            assertThat(Auth0AccessTokens.validator(ISSUER, AUDIENCE).validate(token().build()).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("rechaza un token emitido para otra API del mismo tenant")
        void rejectsATokenForAnotherApi() {
            Jwt other = token().audience(List.of("https://otra-api.example.com")).build();

            assertThat(Auth0AccessTokens.validator(ISSUER, AUDIENCE).validate(other).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("rechaza un token de otro emisor")
        void rejectsATokenOfAnotherIssuer() {
            Jwt other = token().claim(JwtClaimNames.ISS, "https://otro.auth0.com/").build();

            assertThat(Auth0AccessTokens.validator(ISSUER, AUDIENCE).validate(other).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("rechaza un token caducado")
        void rejectsAnExpiredToken() {
            Jwt expired = token().issuedAt(NOW.minusSeconds(3600)).expiresAt(NOW.minusSeconds(1800)).build();

            assertThat(Auth0AccessTokens.validator(ISSUER, AUDIENCE).validate(expired).hasErrors()).isTrue();
        }
    }

    @Nested
    @DisplayName("El conversor")
    class Converter {

        private final ExternalIdentities identities = mock(ExternalIdentities.class);

        private final InMemoryUserDetailsManager users = new InMemoryUserDetailsManager(
                User.withUsername("ada").password("{noop}x").roles("USER").build(),
                User.withUsername("root").password("{noop}x").roles("ADMIN").build(),
                User.withUsername("baja").password("{noop}x").roles("USER").disabled(true).build());

        private final Auth0AccessTokens tokens = new Auth0AccessTokens(identities, users);

        private Authentication convert(String subject, Optional<String> linkedTo) {
            when(identities.usernameOf(ExternalIdentities.AUTH0, subject)).thenReturn(linkedTo);
            return tokens.convert(token().subject(subject).build());
        }

        @Test
        @DisplayName("da al sub vinculado el nombre y los roles del usuario local")
        void givesALinkedSubTheLocalNameAndRoles() {
            Authentication ada = convert("auth0|ada", Optional.of("ada"));
            Authentication root = convert("auth0|root", Optional.of("root"));

            assertThat(ada.getName()).isEqualTo("ada");
            assertThat(ada.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
            assertThat(root.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("rechaza un sub sin vincular diciendo cómo vincularlo")
        void rejectsAnUnlinkedSubSayingHowToLinkIt() {
            assertThatExceptionOfType(UnlinkedAccessTokenException.class)
                    .isThrownBy(() -> convert("auth0|nadie", Optional.empty()))
                    .satisfies(Auth0AccessTokensTests::isAnInvalidToken);
        }

        @Test
        @DisplayName("rechaza igual a un usuario local deshabilitado o que ya no existe, sin distinguirlos")
        void rejectsADisabledOrMissingLocalUserTheSameWay() {
            assertThatExceptionOfType(UnlinkedAccessTokenException.class)
                    .isThrownBy(() -> convert("auth0|baja", Optional.of("baja")))
                    .satisfies(Auth0AccessTokensTests::isAnInvalidToken);
            assertThatExceptionOfType(UnlinkedAccessTokenException.class)
                    .isThrownBy(() -> convert("auth0|borrado", Optional.of("borrado")))
                    .satisfies(Auth0AccessTokensTests::isAnInvalidToken);
        }
    }
}
