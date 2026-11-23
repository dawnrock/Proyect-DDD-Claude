package com.dawnrock.ddd.adapter.in;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

/**
 * Los usuarios configurados en memoria, para los tests de los controladores ({@code @WebMvcTest}), que no tienen base
 * de datos (#150). La aplicación los guarda con {@link UserStore}.
 */
@TestConfiguration
public class TestUsers {

    @Bean
    public UserDetailsManager userDetailsService(SecurityUsersProperties properties, PasswordEncoder encoder,
                                                 @Value("${APP_ADMIN_USERNAME:}") String adminUsername,
                                                 @Value("${APP_ADMIN_PASSWORD:}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                SecuritySupport.configuredUsers(properties, encoder, adminUsername, adminPassword));
    }
}
