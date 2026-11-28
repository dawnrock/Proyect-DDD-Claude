package com.dawnrock.ddd.adapter.in.rest;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import com.dawnrock.ddd.adapter.in.ThrottledAuthenticationProvider;
import com.dawnrock.ddd.adapter.in.TooManyLoginAttemptsException;

/**
 * Comprueba unas credenciales contra los usuarios de la aplicación (los mismos que entran en la web, también los
 * clientes dados de alta) y emite su token JWT (ADR 0015): {@code iss}, {@code sub} (el usuario), {@code roles}
 * ({@code ADMIN} o {@code USER}), {@code iat} y {@code exp}.
 *
 * <p>Usa su propio {@link AuthenticationManager} en lugar de publicar uno como bean, para no cambiar el que usa el
 * inicio de sesión de la web.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.auth.local", matchIfMissing = true)
public class AccessTokenIssuer {

    static final String ROLES_CLAIM = "roles";
    static final String TOKEN_TYPE = "Bearer";
    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public AccessTokenIssuer(ThrottledAuthenticationProvider provider, JwtEncoder encoder, JwtProperties properties) {
        // El mismo proveedor con límite de intentos que el inicio de sesión de la web (#130, ADR 0017)
        this.authenticationManager = new ProviderManager(provider);
        this.encoder = encoder;
        this.properties = properties;
    }

    /**
     * @param details detalles de la petición, con la IP desde la que se pide (cuenta para el límite de intentos)
     * @throws TooManyLoginAttemptsException si hay demasiados intentos fallidos recientes, sin comprobar la contraseña
     * @throws AuthenticationException       si el usuario no existe o la contraseña no es la suya (no se distingue cuál)
     */
    public TokenResponse issue(String username, String password, WebAuthenticationDetails details) {
        UsernamePasswordAuthenticationToken credentials =
                UsernamePasswordAuthenticationToken.unauthenticated(username, password);
        credentials.setDetails(details);
        Authentication authentication = authenticationManager.authenticate(credentials);
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim(ROLES_CLAIM, roles)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, TOKEN_TYPE, properties.ttl().toSeconds());
    }
}
