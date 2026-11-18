package com.pronoide.ddd.adapter.in.rest;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import lombok.extern.slf4j.Slf4j;

/**
 * Firma y validación de los tokens JWT de la API (ADR 0015): HS256 con un secreto compartido. El mismo
 * {@link SecretKey} firma ({@link JwtEncoder}, lo usa {@link AccessTokenIssuer}) y valida ({@link JwtDecoder}, lo usa
 * {@link ApiSecurityConfig}), que además exige el emisor y que no haya caducado.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSupport {

    static final String KEY_ALGORITHM = "HmacSHA256";
    static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSigningKey(JwtProperties properties) {
        return signingKey(properties.secret());
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /**
     * La clave HS256 a partir del secreto (sus bytes UTF-8). Sin secreto genera una aleatoria: la aplicación arranca,
     * pero los tokens dejan de valer al reiniciarla.
     *
     * @throws IllegalStateException si el secreto tiene menos de {@value #MIN_SECRET_BYTES} bytes
     */
    static SecretKey signingKey(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("APP_JWT_SECRET no está definido: se usa una clave aleatoria y los tokens de la API no "
                    + "sobrevivirán a un reinicio");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return new SecretKeySpec(random, KEY_ALGORITHM);
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("APP_JWT_SECRET debe tener al menos %d bytes (tiene %d)"
                    .formatted(MIN_SECRET_BYTES, bytes.length));
        }
        return new SecretKeySpec(bytes, KEY_ALGORITHM);
    }
}
