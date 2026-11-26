package com.dawnrock.ddd.adapter.in.web;

import java.time.Clock;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.dawnrock.ddd.adapter.in.ClientIpResolver;
import com.dawnrock.ddd.adapter.in.Durations;
import com.dawnrock.ddd.adapter.in.ExternalIdentityLinking;
import com.dawnrock.ddd.adapter.in.IdentityAlreadyLinkedException;
import com.dawnrock.ddd.adapter.in.LoginAttemptsProperties;
import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.TooManyLoginAttemptsException;

/**
 * Vinculación de la identidad de Auth0 con el usuario local (#196, ADR 0024). Tras volver de Auth0 sin usuario
 * vinculado, el manejador de fallos guarda la {@link PendingIdentity} en la sesión y lleva aquí: el cliente escribe una
 * sola vez su usuario y su contraseña, y al vincularlos vuelve a Auth0, que ya tiene su sesión, para entrar. Sin
 * identidad pendiente, o caducada, lleva a {@code /login?link-expired}. Contrato:
 * {@code docs/contracts/auth0-web-login.md}.
 */
@Controller
public class IdentityLinkController {

    private static final String VIEW = "login-link";

    private static final String LINK_EXPIRED = "redirect:/login?link-expired";

    private final ExternalIdentityLinking linking;

    private final ClientIpResolver clientIpResolver;

    private final ObjectProvider<Clock> clock;

    /** Minutos del bloqueo por demasiados intentos, los mismos que dice el login: al menos 1. */
    private final long lockoutMinutes;

    public IdentityLinkController(ExternalIdentityLinking linking, ClientIpResolver clientIpResolver,
                                  ObjectProvider<Clock> clock, LoginAttemptsProperties loginAttempts) {
        Duration lockout = loginAttempts.lockout();
        long minutes = Durations.minutesRoundedUp(lockout);
        this.linking = linking;
        this.clientIpResolver = clientIpResolver;
        this.clock = clock;
        this.lockoutMinutes = minutes;
    }

    @ModelAttribute("identityLinkForm")
    public IdentityLinkForm newForm() {
        return new IdentityLinkForm();
    }

    @GetMapping("/login/link")
    public String form(HttpSession session, Model model) {
        PendingIdentity pending = pending(session);
        if (pending == null) {
            return LINK_EXPIRED;
        }
        model.addAttribute("email", pending.email());
        return VIEW;
    }

    @PostMapping("/login/link")
    public String link(@Validated @ModelAttribute("identityLinkForm") IdentityLinkForm form, BindingResult result,
                       HttpSession session, HttpServletRequest request, Model model) {
        PendingIdentity pending = pending(session);
        if (pending == null) {
            return LINK_EXPIRED;
        }
        model.addAttribute("email", pending.email());
        if (result.hasErrors()) {
            return VIEW;
        }
        try {
            linking.link(pending, form.getUsername(), form.getPassword(), clientIpResolver.resolve(request));
        } catch (TooManyLoginAttemptsException e) {
            model.addAttribute("linkError",
                    "Demasiados intentos fallidos. Vuelve a intentarlo dentro de %d minutos.".formatted(lockoutMinutes));
            return VIEW;
        } catch (BadCredentialsException e) {
            model.addAttribute("linkError", "Usuario o contraseña incorrectos");
            return VIEW;
        } catch (IdentityAlreadyLinkedException e) {
            model.addAttribute("linkError", "Ese usuario ya está vinculado a otra cuenta de Auth0");
            return VIEW;
        } catch (IllegalStateException e) {
            // La identidad pendiente caducó mientras el cliente escribía
            session.removeAttribute(PendingIdentity.SESSION_ATTRIBUTE);
            return LINK_EXPIRED;
        }
        session.removeAttribute(PendingIdentity.SESSION_ATTRIBUTE);
        // Auth0 conserva su sesión: el viaje de ida y vuelta es silencioso y ahora encuentra la vinculación
        return "redirect:/oauth2/authorization/auth0";
    }

    /** La identidad pendiente de la sesión, o {@code null} si no hay o ha caducado (en ese caso la quita). */
    private PendingIdentity pending(HttpSession session) {
        Object attribute = session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE);
        if (!(attribute instanceof PendingIdentity pending)) {
            return null;
        }
        if (pending.isExpired(clock.getIfAvailable(Clock::systemUTC))) {
            session.removeAttribute(PendingIdentity.SESSION_ATTRIBUTE);
            return null;
        }
        return pending;
    }
}
