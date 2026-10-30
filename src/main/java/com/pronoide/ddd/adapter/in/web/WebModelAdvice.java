package com.pronoide.ddd.adapter.in.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Datos comunes de todas las pantallas web: el usuario de la sesión, para la cabecera. Sin sesión (o anónimo, como
 * en las páginas de error) vale {@code null} y la cabecera no muestra el botón de cerrar sesión. Así no hace falta
 * {@code thymeleaf-extras-springsecurity} (ADR 0008).
 *
 * <p>Se acota con {@code basePackageClasses}, como {@link WebExceptionHandler}, para no tocar los controladores REST.
 */
@ControllerAdvice(basePackageClasses = WebModelAdvice.class)
public class WebModelAdvice {

    /** Nombre del atributo del modelo que usa la cabecera. */
    public static final String CURRENT_USER = "currentUser";

    @ModelAttribute(CURRENT_USER)
    public String currentUser(Authentication authentication) {
        return usernameOf(authentication);
    }

    /**
     * El usuario de la sesión, o {@code null} si no hay. Los {@code @ExceptionHandler} no reciben los
     * {@code @ModelAttribute} del advice: las páginas de error lo añaden con este método.
     */
    public static String usernameOf(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }
}
