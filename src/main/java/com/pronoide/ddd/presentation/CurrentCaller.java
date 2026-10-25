package com.pronoide.ddd.presentation;

import org.springframework.security.core.Authentication;

import com.pronoide.ddd.application.Caller;

/**
 * Convierte la autenticación de Spring Security en el {@link Caller} de la aplicación: el usuario y si tiene el rol
 * {@code ADMIN}. Lo usan los controladores web y REST (vive en la raíz de {@code presentation} para que los dos canales
 * lo compartan sin conocerse, regla D5).
 */
public final class CurrentCaller {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private CurrentCaller() {
    }

    /** @throws IllegalStateException si no hay nadie autenticado (las cadenas de seguridad lo impiden antes) */
    public static Caller from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No hay ningún usuario autenticado");
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return Caller.builder().username(authentication.getName()).admin(admin).build();
    }
}
