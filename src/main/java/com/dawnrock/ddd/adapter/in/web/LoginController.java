package com.dawnrock.ddd.adapter.in.web;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dawnrock.ddd.adapter.in.AuthProperties;
import com.dawnrock.ddd.adapter.in.Durations;
import com.dawnrock.ddd.adapter.in.LoginAttemptsProperties;

/**
 * Página de acceso. Sin parámetros enseña las dos puertas («Ya soy cliente» y «Quiero ser cliente»); con {@code ?form}
 * o {@code ?error} (el destino del fallo de Spring Security) enseña el formulario, y con {@code ?blocked} también,
 * con el aviso de bloqueo por demasiados intentos (#130). El envío ({@code POST /login}) y el
 * cierre de sesión los gestiona Spring Security.
 *
 * <p>Con Auth0 configurado (ADR 0024) el formulario trae el botón «Entrar con Auth0» ({@code auth0Enabled}); con
 * {@code app.auth.local=false} ({@code localEnabled}) solo ese botón, y la puerta «Ya soy cliente» va directa a Auth0.
 * {@code ?oauth-error} y {@code ?link-expired} (un fallo del retorno de Auth0 y una vinculación caducada) enseñan el
 * formulario con su aviso.
 */
@Controller
public class LoginController {

    /** Minutos del bloqueo por demasiados intentos fallidos (#130), para decírselos a quien lo sufre: al menos 1. */
    private final long lockoutMinutes;

    private final ObjectProvider<ClientRegistrationRepository> registrations;

    private final boolean localEnabled;

    public LoginController(LoginAttemptsProperties loginAttempts, AuthProperties auth,
                           ObjectProvider<ClientRegistrationRepository> registrations) {
        Duration lockout = loginAttempts.lockout();
        long minutes = Durations.minutesRoundedUp(lockout);
        boolean local = auth.local();
        this.lockoutMinutes = minutes;
        this.registrations = registrations;
        this.localEnabled = local;
    }

    @GetMapping("/login")
    public String login(@RequestParam Map<String, String> params, Model model) {
        model.addAttribute("showForm", params.containsKey("form") || params.containsKey("error")
                || params.containsKey("blocked") || params.containsKey("oauth-error")
                || params.containsKey("link-expired"));
        model.addAttribute("auth0Enabled", registrations.getIfAvailable() != null);
        model.addAttribute("localEnabled", localEnabled);
        model.addAttribute("lockoutMinutes", lockoutMinutes);
        return "login";
    }
}
