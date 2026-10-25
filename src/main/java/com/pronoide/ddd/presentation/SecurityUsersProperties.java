package com.pronoide.ddd.presentation;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Usuarios que pueden entrar en la aplicación ({@code app.security.users[*]}): nombre, contraseña cifrada con BCrypt y
 * rol ({@code ADMIN} o {@code USER}). El perfil {@code demo} los define en {@code application-demo.properties}; sin él
 * no hay ninguno, salvo el administrador de las variables de entorno (ver {@link SecuritySupport}).
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityUsersProperties(List<User> users) {

    public SecurityUsersProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    /** Un usuario configurado; {@code passwordHash} es un hash BCrypt ({@code $2a$...}), nunca la contraseña. */
    public record User(String username, String passwordHash, String role) {
    }
}
