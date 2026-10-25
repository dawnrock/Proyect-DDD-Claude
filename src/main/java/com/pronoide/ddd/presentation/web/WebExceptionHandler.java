package com.pronoide.ddd.presentation.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.pronoide.ddd.application.NotFoundException;
import com.pronoide.ddd.application.OperationNotAllowedException;

/**
 * Traduce los errores de la aplicación a las páginas de error de la web: «no existe» (incluida una cuenta ajena) a la
 * 404 y «no puedes hacer esto» a la 403.
 *
 * <p>Se acota con {@code basePackageClasses} y no con {@code annotations = Controller.class}: un
 * {@code @RestController} también es {@code @Controller} y este advice competiría con el de REST.
 */
@ControllerAdvice(basePackageClasses = WebExceptionHandler.class)
public class WebExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Authentication authentication, Model model) {
        model.addAttribute(WebModelAdvice.CURRENT_USER, WebModelAdvice.usernameOf(authentication));
        return "error/404";
    }

    @ExceptionHandler(OperationNotAllowedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleNotAllowed(Authentication authentication, Model model) {
        model.addAttribute(WebModelAdvice.CURRENT_USER, WebModelAdvice.usernameOf(authentication));
        return "error/403";
    }
}
