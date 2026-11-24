package com.dawnrock.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

/** Firma y validación HS256 de los tokens de la API (ADR 0015), sin Spring. */
class JwtSupportTests {

    private static final String SECRET = "un-secreto-de-pruebas-de-al-menos-32-bytes";
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, null, null);

    private final JwtSupport support = new JwtSupport();
    private final SecretKey key = JwtSupport.signingKey(SECRET);
    private final JwtDecoder decoder = support.jwtDecoder(key, PROPERTIES);

    private static String token(SecretKey key, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtEncoder encoder = new JwtSupport().jwtEncoder(key);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("ada").issuedAt(issuedAt)
                .expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String validToken(SecretKey key) {
        Instant now = Instant.now();
        return token(key, "ddd-lab", now, now.plus(15, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Un token firmado con la clave se valida y conserva sus claims")
    void aTokenSignedWithTheKeyIsValidatedAndKeepsItsClaims() {
        Jwt jwt = decoder.decode(validToken(key));

        assertThat(jwt.getSubject()).isEqualTo("ada");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("ddd-lab");
    }

    @Test
    @DisplayName("Un token firmado con otra clave se rechaza")
    void aTokenSignedWithAnotherKeyIsRejected() {
        String foreign = validToken(JwtSupport.signingKey("otro-secreto-distinto-de-al-menos-32-bytes"));

        assertThatExceptionOfType(JwtException.class).isThrownBy(() -> decoder.decode(foreign));
    }

    @Test
    @DisplayName("Un token caducado se rechaza")
    void anExpiredTokenIsRejected() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        String expired = token(key, "ddd-lab", past, past.plus(15, ChronoUnit.MINUTES));

        assertThatExceptionOfType(JwtException.class).isThrownBy(() -> decoder.decode(expired));
    }

    @Test
    @DisplayName("Un token de otro emisor se rechaza")
    void aTokenFromAnotherIssuerIsRejected() {
        Instant now = Instant.now();
        String other = token(key, "otro-emisor", now, now.plus(15, ChronoUnit.MINUTES));

        assertThatExceptionOfType(JwtException.class).isThrownBy(() -> decoder.decode(other));
    }

    @Test
    @DisplayName("Un secreto de menos de 32 bytes impide arrancar con un mensaje claro")
    void aShortSecretStopsTheStartup() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> JwtSupport.signingKey("corto"))
                .withMessageContaining("APP_JWT_SECRET debe tener al menos 32 bytes");
    }

    @Test
    @DisplayName("Sin secreto se genera una clave aleatoria distinta en cada arranque")
    void withoutSecretARandomKeyIsGenerated() {
        SecretKey first = JwtSupport.signingKey("");
        SecretKey second = JwtSupport.signingKey(null);

        assertThat(first.getEncoded()).hasSize(32).isNotEqualTo(second.getEncoded());
    }

    @Test
    @DisplayName("Sin configuración, un token dura 15 minutos y el emisor es ddd-lab")
    void withoutConfigurationATokenLastsFifteenMinutes() {
        JwtProperties defaults = new JwtProperties(null, null, null);

        assertThat(defaults.ttl()).hasMinutes(15);
        assertThat(defaults.issuer()).isEqualTo("ddd-lab");
    }
}
