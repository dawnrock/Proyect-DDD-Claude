package com.dawnrock.ddd.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.dawnrock.ddd.adapter.in.rest.Auth0AccessTokens;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Un tenant de Auth0 imaginario para los tests de la API (#197): firma tokens de acceso RS256 con una clave local y da
 * el decodificador que los valida como el de verdad (mismo validador), sin pedir {@code jwks.json} a nadie.
 */
public final class TestAuth0Tenant {

    public static final String DOMAIN = "tenant.example.auth0.com";

    public static final String ISSUER = "https://" + DOMAIN + "/";

    public static final String AUDIENCE = "https://api.monabank.example";

    /** Las propiedades de Auth0 para un contexto de Spring de prueba (el secreto es de mentira). */
    public static final String[] PROPERTIES = {
        "app.auth.auth0.domain=" + DOMAIN,
        "app.auth.auth0.client-id=test-client",
        "app.auth.auth0.client-secret=test-secret",
        "app.auth.auth0.audience=" + AUDIENCE};

    /**
     * Las cinco propiedades de Auth0 que exige el modo realista ({@code app.auth.local=false}, #199): las de arriba más
     * el cliente público de Swagger UI.
     */
    public static final String[] REALISTIC_MODE_PROPERTIES = {
        PROPERTIES[0], PROPERTIES[1], PROPERTIES[2], PROPERTIES[3],
        "app.auth.auth0.api-docs-client-id=test-spa"};

    private static final int TTL_SECONDS = 600;

    private static final KeyPair KEYS = rsaKeys();

    private TestAuth0Tenant() {
    }

    /** El decodificador del tenant de prueba, con la validación de emisor, audiencia y fechas de la API. */
    public static JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        decoder.setJwtValidator(Auth0AccessTokens.validator(ISSUER, AUDIENCE));
        return decoder;
    }

    /** Un token de acceso del tenant para la API, válido diez minutos. */
    public static String accessToken(String subject) {
        return accessToken(subject, ISSUER, AUDIENCE, Instant.now().plusSeconds(TTL_SECONDS));
    }

    public static String accessToken(String subject, String issuer, String audience, Instant expiresAt) {
        return sign(KEYS, subject, issuer, audience, expiresAt);
    }

    /** Un token con todo correcto salvo la firma: de otra clave, como si lo fabricara un tercero. */
    public static String signedWithAnotherKey(String subject) {
        return sign(rsaKeys(), subject, ISSUER, AUDIENCE, Instant.now().plusSeconds(TTL_SECONDS));
    }

    private static String sign(KeyPair keys, String subject, String issuer, String audience, Instant expiresAt) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey(keys.getPrivate()).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience, ISSUER + "userinfo"))
                .subject(subject).issuedAt(expiresAt.minusSeconds(TTL_SECONDS)).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static KeyPair rsaKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
