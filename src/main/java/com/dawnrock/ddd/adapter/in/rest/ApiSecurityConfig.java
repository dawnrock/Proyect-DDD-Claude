package com.dawnrock.ddd.adapter.in.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.dawnrock.ddd.adapter.in.Auth0Properties;
import com.dawnrock.ddd.adapter.in.AuthProperties;

import com.nimbusds.jose.RemoteKeySourceException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad de la API REST ({@code /api/**}, ADR 0008 y 0015): tokens JWT con {@code Authorization: Bearer}, sin
 * sesión y sin CSRF. Va antes que la de la web. HTTP Basic ya no se acepta (#122).
 * Son públicos {@code POST /api/auth/token} (pedir el token) y {@code POST /api/customers} (el alta de un cliente),
 * que solo existen con la autenticación local (#199);
 * {@code POST /api/accounts} (abrir una cuenta) y {@code POST /api/accounts/{id}/account-number} (sustituir su IBAN)
 * solo los permite a los administradores; la propiedad de cada cuenta la comprueba la capa de aplicación. El claim
 * {@code roles} del token se convierte en las autoridades {@code ROLE_*} y {@code sub} en el nombre, que
 * {@code CurrentCaller} traduce al {@code Caller}. Sin token, o con uno no válido o caducado, responde 401; sin el rol,
 * 403; ambos como {@code ProblemDetail} (RFC 9457) igual que {@link RestExceptionHandler}. Contrato:
 * {@code docs/contracts/login-roles.md}.
 *
 * <p>Dos emisores (#197, ADR 0024): cada token va al gestor de su {@code iss}. Los propios ({@code app.jwt.issuer},
 * HS256), solo con la autenticación local ({@code app.auth.local}), y, si está configurado
 * ({@link Auth0ApiTokensConfig}), los de acceso de Auth0 (RS256, con la audiencia de la API), cuyo {@code sub} se
 * traduce al usuario local con {@link Auth0AccessTokens}. Un emisor desconocido es un token no válido. Una identidad
 * de Auth0 sin vincular recibe 401 con {@link Auth0AccessTokens#UNLINKED_DETAIL}; si no se pueden leer las claves de
 * Auth0, 503 ({@link Auth0UnavailableException}). Sin token, el 401 dice dónde pedirlo: a la propia API con la
 * autenticación local y a Auth0 sin ella (#199).
 */
@Import(JwtSupport.class)
@Configuration
public class ApiSecurityConfig {

    static final String UNAUTHENTICATED_TITLE = "No autenticado";
    static final String FORBIDDEN_TITLE = "Acceso denegado";
    static final String MISSING_TOKEN_DETAIL =
            "Hace falta autenticarse para usar la API: pide un token en POST /api/auth/token y envíalo como Bearer";
    /** El mismo aviso sin autenticación local (#199): el único token posible es el de acceso de Auth0. */
    static final String MISSING_AUTH0_TOKEN_DETAIL =
            "Hace falta autenticarse para usar la API: inicia sesión con Auth0 y envía su token de acceso como Bearer";
    static final String INVALID_TOKEN_DETAIL = "El token no es válido o ha caducado";
    static final String UNAVAILABLE_TITLE = "Servicio no disponible";
    static final String UNAVAILABLE_DETAIL =
            "No se puede comprobar el token de Auth0 en este momento: inténtalo de nuevo en unos minutos";

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http, @Qualifier("jwtDecoder") JwtDecoder ownDecoder,
                                           JwtProperties jwtProperties, AuthProperties authProperties,
                                           Auth0Properties auth0Properties,
                                           @Qualifier(Auth0ApiTokensConfig.DECODER)
                                           ObjectProvider<JwtDecoder> auth0Decoder,
                                           ObjectProvider<Auth0AccessTokens> auth0Tokens) throws Exception {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        if (authProperties.local()) {
            // Sin autenticación local (#199 en prod) los tokens propios no se emiten ni se aceptan
            trust(managers, jwtProperties.issuer(), manager(ownDecoder, jwtAuthenticationConverter()));
        }
        JwtDecoder auth0 = auth0Decoder.getIfAvailable();
        if (auth0 != null) {
            trust(managers, auth0Properties.issuerUri(),
                    manager(reachable(auth0), auth0Tokens.getObject()));
        }
        String missingTokenDetail = authProperties.local() ? MISSING_TOKEN_DETAIL : MISSING_AUTH0_TOKEN_DETAIL;
        AuthenticationEntryPoint unauthenticated = (request, response, exception) -> {
            if (exception instanceof Auth0UnavailableException) {
                writeProblem(response, HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_TITLE, UNAVAILABLE_DETAIL);
            } else if (exception instanceof OAuth2AuthenticationException) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer error=\"invalid_token\", error_description=\"The token is invalid or expired\"");
                writeProblem(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_TITLE,
                        exception instanceof UnlinkedAccessTokenException
                                ? Auth0AccessTokens.UNLINKED_DETAIL : INVALID_TOKEN_DETAIL);
            } else {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                writeProblem(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_TITLE, missingTokenDetail);
            }
        };
        AccessDeniedHandler forbidden = (request, response, exception) -> writeProblem(response,
                HttpStatus.FORBIDDEN, FORBIDDEN_TITLE, "Solo un administrador puede "
                        + (request.getRequestURI().endsWith("/account-number")
                                ? "generar un nuevo número de cuenta" : "abrir cuentas"));
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(requests -> requests
                        // Públicas con cualquier método: uno no declarado llega a Spring MVC y responde 405, no 401
                        .requestMatchers("/api/auth/token", "/api/customers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/accounts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/accounts/*/account-number").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationManagerResolver(new JwtIssuerAuthenticationManagerResolver(managers::get))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * Confía en un emisor con su gestor. Dos gestores para el mismo {@code iss} (por ejemplo, {@code app.jwt.issuer}
     * igual que el tenant de Auth0) sería decidir al azar cuál valida: es un error de configuración.
     *
     * @throws IllegalStateException si el emisor ya tenía gestor
     */
    static void trust(Map<String, AuthenticationManager> managers, String issuer, AuthenticationManager manager) {
        if (managers.putIfAbsent(issuer, manager) != null) {
            throw new IllegalStateException("Dos configuraciones de tokens usan el mismo emisor: " + issuer);
        }
    }

    /**
     * El decodificador de Auth0 con su fallo de red traducido: no poder descargar las claves del tenant (la
     * {@link RemoteKeySourceException} de Nimbus) es {@link Auth0UnavailableException} (503). Cualquier otro fallo
     * (token mal formado, mal firmado, caducado, de otra audiencia, o un error criptográfico) sigue siendo un token no
     * válido (401).
     */
    private static JwtDecoder reachable(JwtDecoder auth0) {
        return token -> {
            try {
                return auth0.decode(token);
            } catch (JwtException e) {
                if (e.getCause() instanceof RemoteKeySourceException) {
                    throw new Auth0UnavailableException(e);
                }
                throw e instanceof BadJwtException ? e : new BadJwtException(e.getMessage(), e);
            }
        };
    }

    /** El gestor de un emisor: su decodificador (firma y validación) y cómo convertir el token en usuario. */
    private static AuthenticationManager manager(JwtDecoder decoder,
                                                 Converter<Jwt, ? extends AbstractAuthenticationToken> converter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return new ProviderManager(provider);
    }

    /** Claim {@code roles} ({@code ADMIN}, {@code USER}) → autoridades {@code ROLE_*}; {@code sub} → nombre. */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AccessTokenIssuer.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                .formatted(title, status.value(), detail));
    }
}
