package com.dawnrock.ddd.adapter.in.web;


import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dawnrock.ddd.adapter.in.CurrentCaller;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.domain.model.DomainException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Alta de cuenta por el administrador (#177): para un cliente nuevo o para uno que ya existe, por su documento. Al
 * abrirla vuelve al listado con el IBAN y el segmento que han decidido el banco y la agencia tributaria.
 */
@Controller
@RequiredArgsConstructor
public class OpenAccountWebController {

    private static final String FORM_VIEW = "accounts/new";

    private final AccountOpeningCommands commands;

    private final WebFormat fmt;

    @GetMapping("/accounts/new")
    public String newAccountForm(Model model) {
        model.addAttribute("form", new OpenAccountForm());
        model.addAttribute("currencies", AccountDetailView.CURRENCIES);
        return FORM_VIEW;
    }

    @PostMapping("/accounts/new")
    public String openAccount(@Valid @ModelAttribute("form") OpenAccountForm form, BindingResult result,
                              Model model, RedirectAttributes redirect, Authentication authentication) {
        OpenAccountCommand command = result.hasErrors() ? null : toCommand(form, result);
        if (command != null) {
            try {
                AccountSummary account = commands.openAccount(CurrentCaller.from(authentication), command);
                redirect.addFlashAttribute(AccountDetailView.MESSAGE, "Cuenta %s abierta correctamente como %s"
                        .formatted(account.accountNumber(), fmt.segment(account.segment())));
                return "redirect:/";
            } catch (DomainException | IllegalArgumentException e) {
                result.reject("domain", e.getMessage());
            }
        }
        model.addAttribute("currencies", AccountDetailView.CURRENCIES);
        return FORM_VIEW;
    }

    /**
     * Construye el command, o deja el error en el formulario y devuelve {@code null} si lo rechaza (por ejemplo,
     * una divisa con formato válido pero que no existe): es la red de seguridad de la validación del formulario.
     */
    private static OpenAccountCommand toCommand(OpenAccountForm form, BindingResult result) {
        try {
            // Los campos del titular que falten quedan vacíos y la divisa que falte es EUR: lo hace el command
            return new OpenAccountCommand(form.getHolderName(), form.getHolderTaxId(), form.getHolderEmail(),
                    form.getInitialDeposit(), form.getOwnerUsername(), form.getCurrency());
        } catch (IllegalArgumentException e) {
            result.reject("invalid", e.getMessage());
            return null;
        }
    }
}
