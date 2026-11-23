package com.dawnrock.ddd.adapter.in;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

/**
 * Almacén de los usuarios (#150, ADR 0019): las tablas estándar de Spring Security en la base de datos, así que los
 * clientes que se dan de alta conservan su usuario tras un reinicio. Vive aparte de {@link SecuritySupport} para que
 * los tests de los controladores, sin base de datos, importen la seguridad con un almacén en memoria.
 */
@Configuration
public class UserStore {

    /**
     * Los usuarios configurados ({@link SecuritySupport#configuredUsers}) se crean al arrancar si no existen y, si
     * existen, toman la contraseña y el rol configurados: la configuración manda sobre ellos (así se cambia la
     * contraseña del administrador en Render). Los demás usuarios no se tocan. Espera a que Flyway haya creado las
     * tablas.
     */
    @Bean
    @DependsOnDatabaseInitialization
    public UserDetailsManager userDetailsService(DataSource dataSource, SecurityUsersProperties properties,
                                                 PasswordEncoder encoder,
                                                 @Value("${APP_ADMIN_USERNAME:}") String adminUsername,
                                                 @Value("${APP_ADMIN_PASSWORD:}") String adminPassword) {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
        for (UserDetails user : SecuritySupport.configuredUsers(properties, encoder, adminUsername, adminPassword)) {
            if (manager.userExists(user.getUsername())) {
                manager.updateUser(user);
            } else {
                manager.createUser(user);
            }
        }
        return manager;
    }
}
