package com.dawnrock.ddd.adapter.in.web;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dawnrock.ddd.adapter.in.ClientIpResolver;
import com.dawnrock.ddd.adapter.in.CustomerRegistration;
import com.dawnrock.ddd.adapter.in.Durations;
import com.dawnrock.ddd.adapter.in.IdentityAlreadyLinkedException;
import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.PendingIdentityExpiredException;
import com.dawnrock.ddd.adapter.in.SignUpLimit;
import com.dawnrock.ddd.adapter.in.TooManySignUpsException;
import com.dawnrock.ddd.adapter.in.UsernameTakenException;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.SignUpCommand;
import com.dawnrock.ddd.domain.model.DomainException;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;

/**
 * Alta de cliente de monA Bank en tres pasos (#95, #103). El estado del asistente es un {@link SignUpForm} en la
 * sesión del servidor; entrar en un paso sin haber completado los anteriores lleva a {@code /signup}. Contrato:
 * {@code docs/contracts/customer-sign-up.md}.
 *
 * <p>Con una identidad de Auth0 pendiente en la sesión, {@code GET /signup?auth0} arranca el mismo asistente en modo
 * Auth0 (#198, ADR 0024): el paso 2 pide solo el usuario y cada paso comprueba la identidad pendiente, que se renueva
 * al completarlo. Contrato: {@code docs/contracts/auth0-sign-up.md}.
 *
 * <p>Sin autenticación local ({@code app.auth.local=false}, #199) no hay alta con contraseña: cualquier paso del
 * asistente clásico lleva a Auth0, que devuelve a quien aún no es cliente a {@code /login/link} y su «Date de alta».
 */
@Controller
@SessionAttributes(SignUpController.FORM)
public class SignUpController {

    /** Lo más que puede durar un alta en modo Auth0, aunque la identidad pendiente se renueve en cada paso. */
    static final Duration AUTH0_WIZARD_LIMIT = Duration.ofMinutes(30);

    static final String FORM = "signUpForm";

    private static final String DATA_VIEW = "signup/data";

    private static final String ACCESS_VIEW = "signup/access";

    private static final String ACCOUNT_VIEW = "signup/account";

    private static final List<String> CURRENCIES = List.of("EUR", "USD", "GBP");

    private static final String REDIRECT_DATA = "redirect:/signup";

    private static final String LINK_EXPIRED = "redirect:/login?link-expired";

    private static final String AUTH0_LOGIN = "redirect:/oauth2/authorization/auth0";

    private final CustomerRegistration registration;

    private final ClientIpResolver clientIpResolver;

    private final SignUpLimit signUpLimit;

    private final SmartValidator validator;

    private final PendingIdentitySession pendingIdentities;

    private final ProgrammaticSignIn signIn = new ProgrammaticSignIn();

    /**
     * Si hay alta con contraseña: solo con la autenticación local ({@code app.auth.local}, #199). Se lee el valor y no
     * {@code AuthProperties}, igual que las condiciones de {@code TokenController} y {@code CustomerController}.
     */
    private final boolean localSignUp;

    public SignUpController(CustomerRegistration registration, ClientIpResolver clientIpResolver,
                            SignUpLimit signUpLimit, SmartValidator validator, ObjectProvider<Clock> clock,
                            @Value("${app.auth.local:true}") boolean localSignUp) {
        this.localSignUp = localSignUp;
        this.registration = registration;
        this.clientIpResolver = clientIpResolver;
        this.signUpLimit = signUpLimit;
        this.validator = validator;
        this.pendingIdentities = new PendingIdentitySession(clock);
    }

    @ModelAttribute(FORM)
    public SignUpForm newForm() {
        return new SignUpForm();
    }

    @GetMapping("/signup")
    public String data(@ModelAttribute(FORM) SignUpForm form, HttpServletRequest request, HttpSession session,
                       Model model) {
        SignUpForm current = form;
        if (pendingMissing(form, session)) {
            // Un asistente Auth0 abandonado, sin identidad vigente, no deja atrapado al cliente: paso 1 limpio
            current = new SignUpForm();
            model.addAttribute(FORM, current);
        }
        if (classicSignUpOff(current)) {
            return AUTH0_LOGIN;
        }
        warnIfLimitReached(request, model);
        return DATA_VIEW;
    }

    /**
     * Arranca el asistente en modo Auth0 (#198): un formulario nuevo (no el de la fábrica), con el correo que da Auth0.
     * Sin identidad pendiente vigente, vuelve a entrar con Auth0.
     */
    @GetMapping(value = "/signup", params = "auth0")
    public String startAuth0(HttpServletRequest request, HttpSession session, Model model) {
        PendingIdentity pending = pendingIdentities.current(session);
        if (pending == null) {
            return LINK_EXPIRED;
        }
        SignUpForm form = new SignUpForm();
        form.startExternalIdentity(pending.subject(), pending.email(), pendingIdentities.instant());
        model.addAttribute(FORM, form);
        warnIfLimitReached(request, model);
        return DATA_VIEW;
    }

    @PostMapping("/signup")
    public String submitData(@Validated(SignUpForm.PersonalData.class) @ModelAttribute(FORM) SignUpForm form,
                             BindingResult result, HttpServletRequest request, HttpSession session, Model model) {
        if (classicSignUpOff(form)) {
            return AUTH0_LOGIN;
        }
        if (pendingMissing(form, session)) {
            return LINK_EXPIRED;
        }
        if (warnIfLimitReached(request, model)) {
            form.resetProgress();
            return DATA_VIEW;
        }
        if (result.hasErrors()) {
            form.resetProgress();
            return DATA_VIEW;
        }
        form.completePersonalData();
        renewPending(form, session);
        return "redirect:/signup/access";
    }

    @GetMapping("/signup/access")
    public String access(@ModelAttribute(FORM) SignUpForm form, HttpSession session) {
        if (classicSignUpOff(form)) {
            return AUTH0_LOGIN;
        }
        if (pendingMissing(form, session)) {
            return LINK_EXPIRED;
        }
        return form.isPersonalDataDone() ? ACCESS_VIEW : REDIRECT_DATA;
    }

    @PostMapping("/signup/access")
    public String submitAccess(@ModelAttribute(FORM) SignUpForm form, BindingResult result, HttpSession session) {
        if (classicSignUpOff(form)) {
            return AUTH0_LOGIN;
        }
        if (pendingMissing(form, session)) {
            return LINK_EXPIRED;
        }
        if (!form.isPersonalDataDone()) {
            return REDIRECT_DATA;
        }
        // El grupo depende del modo, que vive en la sesión y no en la petición: se valida a mano
        if (form.isExternalIdentity()) {
            validator.validate(form, result, SignUpForm.ExternalAccess.class);
        } else {
            validator.validate(form, result, SignUpForm.Access.class, SignUpForm.LocalPassword.class);
        }
        if (form.isExternalIdentity()) {
            // En modo Auth0 no hay contraseña local: si el navegador manda una, no se guarda
            form.clearPassword();
        } else if (!result.hasErrors() && passwordsDiffer(form)) {
            result.rejectValue("passwordRepetition", "mismatch", "Las contraseñas no coinciden");
        }
        if (result.hasErrors()) {
            form.resetAccess();
            return ACCESS_VIEW;
        }
        form.completeAccess();
        renewPending(form, session);
        return "redirect:/signup/account";
    }

    @GetMapping("/signup/account")
    public String account(@ModelAttribute(FORM) SignUpForm form, HttpSession session, Model model) {
        if (classicSignUpOff(form)) {
            return AUTH0_LOGIN;
        }
        if (pendingMissing(form, session)) {
            return LINK_EXPIRED;
        }
        if (!form.isAccessDone()) {
            return REDIRECT_DATA;
        }
        model.addAttribute("currencies", CURRENCIES);
        return ACCOUNT_VIEW;
    }

    @PostMapping("/signup/account")
    public String submitAccount(@Validated(SignUpForm.AccountData.class) @ModelAttribute(FORM) SignUpForm form,
                                BindingResult result, Model model, RedirectAttributes redirect,
                                HttpServletRequest request, HttpServletResponse response, HttpSession session,
                                SessionStatus status) {
        if (classicSignUpOff(form)) {
            return AUTH0_LOGIN;
        }
        if (pendingMissing(form, session)) {
            return LINK_EXPIRED;
        }
        if (!form.isAccessDone()) {
            return REDIRECT_DATA;
        }
        model.addAttribute("currencies", CURRENCIES);
        if (result.hasErrors()) {
            return ACCOUNT_VIEW;
        }
        SignUpCommand command;
        try {
            command = toCommand(form);
        } catch (IllegalArgumentException e) {
            return backToData(form, redirect, e.getMessage(), false);
        }
        return register(form, command, result, redirect, request, response, session, status);
    }

    /**
     * Da de alta al cliente y lleva a donde toca según el resultado; los errores del dominio vuelven al paso que los
     * arregla. En modo Auth0 (#198) el alta no tiene contraseña ni inicio de sesión programático.
     */
    private String register(SignUpForm form, SignUpCommand command, BindingResult result,
                            RedirectAttributes redirect, HttpServletRequest request, HttpServletResponse response,
                            HttpSession session, SessionStatus status) {
        try {
            if (form.isExternalIdentity()) {
                return registerWithAuth0(command, request, session, status);
            }
            AccountSummary account = registration.register(command, form.getPassword(),
                    clientIpResolver.resolve(request));
            String username = form.getUsername();
            status.setComplete();
            signIn.signIn(username, request, response);
            redirect.addFlashAttribute("message",
                    "Te damos la bienvenida a monA Bank. Tu IBAN es " + account.accountNumber());
            return "redirect:/accounts/" + account.id();
        } catch (TooManySignUpsException e) {
            result.reject("tooManySignUps", tooManySignUps(e.retryAfter()));
            return ACCOUNT_VIEW;
        } catch (DuplicateCustomerException e) {
            return backToData(form, redirect, e.getMessage(), true);
        } catch (UsernameTakenException e) {
            form.resetAccess();
            redirect.addFlashAttribute("signUpError", e.getMessage());
            return "redirect:/signup/access";
        } catch (IllegalArgumentException e) {
            return backToData(form, redirect, e.getMessage(), false);
        } catch (PendingIdentityExpiredException e) {
            // La identidad pendiente caducó durante el alta: no se creó nada
            pendingIdentities.remove(session);
            return LINK_EXPIRED;
        } catch (IdentityAlreadyLinkedException e) {
            // Otra pestaña la vinculó entretanto: el vínculo existe, así que Auth0 ya inicia sesión con el usuario
            status.setComplete();
            return goToAuth0(session);
        } catch (DomainException e) {
            result.reject("domain", e.getMessage());
            return ACCOUNT_VIEW;
        }
    }

    /**
     * Alta con la identidad de Auth0: sin contraseña local ni inicio de sesión programático (la sesión solo la crea el
     * flujo OAuth2). Al terminar, vuelve a Auth0, que conserva su sesión: el viaje es silencioso y ahora encuentra el
     * vínculo. No hay mensaje de bienvenida: no sobreviviría al viaje; la página de la cuenta, con su IBAN, lo confirma.
     */
    private String registerWithAuth0(SignUpCommand command, HttpServletRequest request, HttpSession session,
                                     SessionStatus status) {
        registration.registerWithExternalIdentity(command, pendingIdentities.current(session),
                clientIpResolver.resolve(request));
        status.setComplete();
        return goToAuth0(session);
    }

    /** Quita la identidad pendiente y vuelve a Auth0 para entrar con la sesión del cliente ya vinculado. */
    private String goToAuth0(HttpSession session) {
        pendingIdentities.remove(session);
        return AUTH0_LOGIN;
    }

    /** Si el asistente es el clásico, con contraseña, y no se ofrece: sin autenticación local (#199). */
    private boolean classicSignUpOff(SignUpForm form) {
        return !localSignUp && !form.isExternalIdentity();
    }

    private static boolean passwordsDiffer(SignUpForm form) {
        return !Objects.equals(form.getPassword(), form.getPasswordRepetition());
    }

    /**
     * Si el asistente está en modo Auth0 y ya no puede seguir: no hay identidad pendiente vigente (la caducada se
     * quita), es de otra identidad que la que empezó el alta, o el alta pasó de {@link #AUTH0_WIZARD_LIMIT} aunque la
     * identidad se haya ido renovando (#198, revisión).
     */
    private boolean pendingMissing(SignUpForm form, HttpSession session) {
        if (!form.isExternalIdentity()) {
            return false;
        }
        PendingIdentity pending = pendingIdentities.current(session);
        return pending == null || !pending.subject().equals(form.getExternalSubject())
                || pendingIdentities.instant().isAfter(form.getExternalStartedAt().plus(AUTH0_WIZARD_LIMIT));
    }

    /** Completar un paso del modo Auth0 renueva la identidad pendiente: la ventana de 10 minutos se desliza. */
    private void renewPending(SignUpForm form, HttpSession session) {
        if (form.isExternalIdentity()) {
            pendingIdentities.renew(session, pendingIdentities.current(session));
        }
    }

    /**
     * Comprueba el límite de altas en el paso 1 (#131, ADR 0018), sin reservar nada: así nadie rellena los tres pasos
     * para nada. El último paso vuelve a comprobarlo al reservar, porque el límite puede llenarse entretanto.
     *
     * @return si el límite está lleno; entonces deja el aviso en {@code signUpError}
     */
    private boolean warnIfLimitReached(HttpServletRequest request, Model model) {
        return signUpLimit.lockedFor(clientIpResolver.resolve(request))
                .map(retryAfter -> model.addAttribute("signUpError", tooManySignUps(retryAfter)))
                .isPresent();
    }

    /** Aviso del límite de altas (#131, ADR 0018) con los minutos que faltan, redondeados hacia arriba. */
    static String tooManySignUps(Duration retryAfter) {
        long minutes = Durations.minutesRoundedUp(retryAfter);
        return "Demasiadas altas de clientes en este momento. Inténtalo de nuevo dentro de %d %s."
                .formatted(minutes, minutes == 1 ? "minuto" : "minutos");
    }

    /** Vuelve al paso 1 con el mensaje (PRG); el paso 1 pinta el enlace para entrar si el cliente ya existe. */
    private static String backToData(SignUpForm form, RedirectAttributes redirect, String message, boolean duplicate) {
        form.resetProgress();
        form.clearPassword();
        redirect.addFlashAttribute("signUpError", message);
        redirect.addFlashAttribute("duplicateCustomer", duplicate);
        return REDIRECT_DATA;
    }

    private static SignUpCommand toCommand(SignUpForm form) {
        return new SignUpCommand(form.getFullName(), form.getTaxId(), form.getEmail(), form.getUsername(),
                form.getInitialDeposit(), form.getCurrency());
    }
}
