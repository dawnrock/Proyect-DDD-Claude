package com.pronoide.ddd.presentation.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad de la web (ADR 0008): la cadena de reserva, para todo lo que no es {@code /api/**} (va después de la de
 * la API). Inicio de sesión con formulario propio y sesión, CSRF activo, {@code /accounts/new} solo para
 * administradores y la página 403 propia.
 *
 * <p>Públicos: {@code /login}, los estilos, la salud del actuator y las páginas de error. El resto exige sesión.
 */
@Configuration
public class WebSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/login", "/css/**", "/actuator/health", "/error", "/error/**").permitAll()
                        .requestMatchers("/accounts/new").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(errors -> errors.accessDeniedPage("/error/403"))
                .build();
    }
}
