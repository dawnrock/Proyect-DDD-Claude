package com.dawnrock.ddd.adapter.in.web;

import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dawnrock.ddd.adapter.in.LoginAttemptsProperties;

/**
 * Página de acceso. Sin parámetros enseña las dos puertas («Ya soy cliente» y «Quiero ser cliente»); con {@code ?form}
 * o {@code ?error} (el destino del fallo de Spring Security) enseña el formulario, y con {@code ?blocked} también,
 * con el aviso de bloqueo por demasiados intentos (#130). El envío ({@code POST /login}) y el
 * cierre de sesión los gestiona Spring Security.
 */
@Controller
public class LoginController {

    /** Minutos del bloqueo por demasiados intentos fallidos (#130), para decírselos a quien lo sufre: al menos 1. */
    private final long lockoutMinutes;

    public LoginController(LoginAttemptsProperties loginAttempts) {
        Duration lockout = loginAttempts.lockout();
        this.lockoutMinutes = Math.max(1, (lockout.toSeconds() + 59) / 60);
    }

    @GetMapping("/login")
    public String login(@RequestParam Map<String, String> params, Model model) {
        model.addAttribute("showForm", params.containsKey("form") || params.containsKey("error")
                || params.containsKey("blocked"));
        model.addAttribute("lockoutMinutes", lockoutMinutes);
        return "login";
    }
}
