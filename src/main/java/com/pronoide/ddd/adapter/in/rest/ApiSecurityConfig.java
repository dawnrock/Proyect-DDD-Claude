package com.pronoide.ddd.adapter.in.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad de la API REST ({@code /api/**}, ADR 0008 y 0015): tokens JWT con {@code Authorization: Bearer}, sin
 * sesión y sin CSRF. Va antes que la de la web. HTTP Basic ya no se acepta (#122).
 * Son públicos {@code POST /api/auth/token} (pedir el token) y {@code POST /api/customers} (el alta de un cliente);
 * {@code POST /api/accounts} (abrir una cuenta) y {@code POST /api/accounts/{id}/account-number} (sustituir su IBAN)
 * solo los permite a los administradores; la propiedad de cada cuenta la comprueba la capa de aplicación. El claim
 * {@code roles} del token se convierte en las autoridades {@code ROLE_*} y {@code sub} en el nombre, que
 * {@code CurrentCaller} traduce al {@code Caller}. Sin token, o con uno no válido o caducado, responde 401; sin el rol,
 * 403; ambos como {@code ProblemDetail} (RFC 9457) igual que {@link RestExceptionHandler}. Contrato:
 * {@code docs/contracts/login-roles.md}.
 */
@Import(JwtSupport.class)
@Configuration
public class ApiSecurityConfig {

    static final String UNAUTHENTICATED_TITLE = "No autenticado";
    static final String FORBIDDEN_TITLE = "Acceso denegado";
    static final String MISSING_TOKEN_DETAIL =
            "Hace falta autenticarse para usar la API: pide un token en POST /api/auth/token y envíalo como Bearer";
    static final String INVALID_TOKEN_DETAIL = "El token no es válido o ha caducado";

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, exception) -> {
            if (exception instanceof OAuth2AuthenticationException) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer error=\"invalid_token\", error_description=\"The token is invalid or expired\"");
                writeProblem(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_TITLE, INVALID_TOKEN_DETAIL);
            } else {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                writeProblem(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_TITLE, MISSING_TOKEN_DETAIL);
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
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
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
