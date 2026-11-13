package com.pronoide.ddd.presentation.web;

import java.util.Objects;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.SessionAttributes;

import lombok.RequiredArgsConstructor;

/**
 * Alta de cliente de Mon-A Bank en tres pasos (#95). El estado del asistente es un {@link SignUpForm} en la sesión del
 * servidor; entrar en un paso sin haber completado los anteriores lleva a {@code /signup}. Contrato:
 * {@code docs/contracts/customer-sign-up.md}.
 */
@Controller
@RequiredArgsConstructor
@SessionAttributes(SignUpController.FORM)
public class SignUpController {

    static final String FORM = "signUpForm";

    private static final String DATA_VIEW = "signup/data";

    private static final String ACCESS_VIEW = "signup/access";

    private static final String REDIRECT_DATA = "redirect:/signup";

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
}
