package com.pronoide.ddd.presentation.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad de la web (ADR 0008): la cadena de reserva, para todo lo que no es {@code /api/**}.
 *
 * <p>Base de la #50 (T1): todavía deja pasar todo y sin CSRF. El login de formulario, el cierre de sesión, el CSRF,
 * {@code /accounts/new} solo para administradores y la página 403 llegan con la tarea W1.
 */
@Configuration
public class WebSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
