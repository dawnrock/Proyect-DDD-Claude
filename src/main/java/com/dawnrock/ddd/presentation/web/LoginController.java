package com.dawnrock.ddd.presentation.web;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Página de acceso. Sin parámetros enseña las dos puertas («Ya soy cliente» y «Quiero ser cliente»); con {@code ?form}
 * o {@code ?error} (el destino del fallo de Spring Security) enseña el formulario. El envío ({@code POST /login}) y el
 * cierre de sesión los gestiona Spring Security.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam Map<String, String> params, Model model) {
        model.addAttribute("showForm", params.containsKey("form") || params.containsKey("error"));
        return "login";
    }
}
