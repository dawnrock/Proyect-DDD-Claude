package com.pronoide.ddd.adapter.in.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad de la API REST ({@code /api/**}, ADR 0008): HTTP Basic, sin sesión y sin CSRF. Va antes que la de la web.
 * Todo exige autenticación y {@code POST /api/accounts} (abrir una cuenta) solo la permite a los administradores; la
 * propiedad de cada cuenta la comprueba la capa de aplicación. Sin credenciales responde 401 y sin el rol 403, ambos
 * como {@code ProblemDetail} (RFC 9457) igual que {@link RestExceptionHandler}. Contrato:
 * {@code docs/contracts/login-roles.md}.
 */
@Configuration
public class ApiSecurityConfig {

    static final String UNAUTHENTICATED_TITLE = "No autenticado";
    static final String FORBIDDEN_TITLE = "Acceso denegado";

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Realm\"");
            writeProblem(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_TITLE,
                    "Hace falta autenticarse para usar la API");
        };
        AccessDeniedHandler forbidden = (request, response, exception) -> writeProblem(response,
                HttpStatus.FORBIDDEN, FORBIDDEN_TITLE, "Solo un administrador puede abrir cuentas");
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/accounts").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthenticated))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
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
