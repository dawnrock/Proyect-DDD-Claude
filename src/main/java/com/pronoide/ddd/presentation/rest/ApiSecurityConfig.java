package com.pronoide.ddd.presentation.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad de la API REST ({@code /api/**}, ADR 0008): HTTP Basic, sin sesión y sin CSRF. Va antes que la de la web.
 *
 * <p>Base de la #50 (T1): todavía deja pasar todo. Las reglas (autenticación obligatoria, {@code POST /api/accounts}
 * solo para administradores, 401/403 como {@code ProblemDetail}) llegan con la tarea B6.
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
