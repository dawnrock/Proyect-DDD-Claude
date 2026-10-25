package com.pronoide.ddd.presentation.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Página 403. Spring Security reenvía aquí (conservando el método de la petición) cuando un usuario sin el rol
 * pide una ruta restringida, por eso acepta cualquier método.
 */
@Controller
public class AccessDeniedController {

    @RequestMapping("/error/403")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden() {
        return "error/403";
    }
}
