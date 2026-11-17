package com.pronoide.ddd.adapter.in.web;

import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pronoide.ddd.adapter.in.CustomerRegistration;
import com.pronoide.ddd.adapter.in.UsernameTakenException;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.SignUpCommand;
import com.pronoide.ddd.domain.model.DomainException;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;

import lombok.RequiredArgsConstructor;

/**
 * Alta de cliente de Mon-A Bank en tres pasos (#95, #103). El estado del asistente es un {@link SignUpForm} en la
 * sesión del servidor; entrar en un paso sin haber completado los anteriores lleva a {@code /signup}. Contrato:
 * {@code docs/contracts/customer-sign-up.md}.
 */
@Controller
@RequiredArgsConstructor
@SessionAttributes(SignUpController.FORM)
public class SignUpController {

    static final String FORM = "signUpForm";

    private static final String DATA_VIEW = "signup/data";

    private static final String ACCESS_VIEW = "signup/access";

    private static final String ACCOUNT_VIEW = "signup/account";

    private static final List<String> CURRENCIES = List.of("EUR", "USD", "GBP");

    private static final String REDIRECT_DATA = "redirect:/signup";

    private final CustomerRegistration registration;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @ModelAttribute(FORM)
    public SignUpForm newForm() {
        return new SignUpForm();
    }

    @GetMapping("/signup")
    public String data() {
        return DATA_VIEW;
    }

    @PostMapping("/signup")
    public String submitData(@Validated(SignUpForm.PersonalData.class) @ModelAttribute(FORM) SignUpForm form,
                             BindingResult result) {
        if (result.hasErrors()) {
            form.resetProgress();
            return DATA_VIEW;
        }
        form.completePersonalData();
        return "redirect:/signup/access";
    }

    @GetMapping("/signup/access")
    public String access(@ModelAttribute(FORM) SignUpForm form) {
        return form.isPersonalDataDone() ? ACCESS_VIEW : REDIRECT_DATA;
    }

    @PostMapping("/signup/access")
    public String submitAccess(@Validated(SignUpForm.Access.class) @ModelAttribute(FORM) SignUpForm form,
                               BindingResult result) {
        if (!form.isPersonalDataDone()) {
            return REDIRECT_DATA;
        }
        if (!result.hasErrors() && !Objects.equals(form.getPassword(), form.getPasswordRepetition())) {
            result.rejectValue("passwordRepetition", "mismatch", "Las contraseñas no coinciden");
        }
        if (result.hasErrors()) {
            form.resetAccess();
            return ACCESS_VIEW;
        }
        form.completeAccess();
        return "redirect:/signup/account";
    }

    @GetMapping("/signup/account")
    public String account(@ModelAttribute(FORM) SignUpForm form, Model model) {
        if (!form.isAccessDone()) {
            return REDIRECT_DATA;
        }
        model.addAttribute("currencies", CURRENCIES);
        return ACCOUNT_VIEW;
    }

    @PostMapping("/signup/account")
    public String submitAccount(@Validated(SignUpForm.AccountData.class) @ModelAttribute(FORM) SignUpForm form,
                                BindingResult result, Model model, RedirectAttributes redirect,
                                HttpServletRequest request, HttpServletResponse response, SessionStatus status) {
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
        try {
            AccountSummary account = registration.register(command, form.getPassword());
            String username = form.getUsername();
            status.setComplete();
            signIn(username, request, response);
            redirect.addFlashAttribute("message",
                    "Te damos la bienvenida a Mon-A Bank. Tu IBAN es " + account.accountNumber());
            return "redirect:/accounts/" + account.id();
        } catch (DuplicateCustomerException e) {
            return backToData(form, redirect, e.getMessage(), true);
        } catch (UsernameTakenException e) {
            form.resetAccess();
            redirect.addFlashAttribute("signUpError", e.getMessage());
            return "redirect:/signup/access";
        } catch (IllegalArgumentException e) {
            return backToData(form, redirect, e.getMessage(), false);
        } catch (DomainException e) {
            result.reject("domain", e.getMessage());
            return ACCOUNT_VIEW;
        }
    }

    /**
     * Deja al cliente recién dado de alta con la sesión iniciada: autentica al usuario (rol {@code USER}) y guarda el
     * contexto en la sesión, porque Spring Security 7 no lo hace solo con una autenticación programática. Cambia el
     * identificador de sesión contra la fijación de sesión.
     */
    private void signIn(String username, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                username, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        securityContextRepository.saveContext(context, request, response);
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
        return SignUpCommand.builder()
                .fullName(form.getFullName())
                .taxId(form.getTaxId())
                .email(form.getEmail())
                .username(form.getUsername())
                .initialDeposit(form.getInitialDeposit())
                .currency(form.getCurrency())
                .build();
    }
}
