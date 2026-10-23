package com.pronoide.ddd.presentation.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.pronoide.ddd.application.NotFoundException;

/**
 * Traduce los "no existe" de la aplicación a la página 404 de la web.
 *
 * <p>Se acota con {@code basePackageClasses} y no con {@code annotations = Controller.class}: un
 * {@code @RestController} también es {@code @Controller} y este advice competiría con el de REST.
 */
@ControllerAdvice(basePackageClasses = WebExceptionHandler.class)
public class WebExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound() {
        return "error/404";
    }
}
