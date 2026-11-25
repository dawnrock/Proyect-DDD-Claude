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
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.NotACustomerException;
import com.dawnrock.ddd.application.OpenAnotherAccountCommand;
import com.dawnrock.ddd.domain.model.DomainException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Otra cuenta para el cliente que la pide, indicando solo el depósito (#103, #177): el titular sale de sus datos de
 * cliente. Quien no es cliente, también el administrador, recibe la página 403.
 */
@Controller
@RequiredArgsConstructor
public class AnotherAccountWebController {

    private static final String ANOTHER_VIEW = "accounts/another";

    private final AccountOpeningCommands commands;

    private final AccountDetailView detailView;

    @GetMapping("/accounts/another")
    public String anotherAccountForm(Model model, Authentication authentication) {
        Caller caller = CurrentCaller.from(authentication);
        if (!detailView.isCustomer(caller)) {
            throw new NotACustomerException(caller.username());
        }
        model.addAttribute("form", new AnotherAccountForm());
        model.addAttribute("currencies", AccountDetailView.CURRENCIES);
        return ANOTHER_VIEW;
    }

    @PostMapping("/accounts/another")
    public String openAnotherAccount(@Valid @ModelAttribute("form") AnotherAccountForm form, BindingResult result,
                                     Model model, RedirectAttributes redirect, Authentication authentication) {
        Caller caller = CurrentCaller.from(authentication);
        if (caller.admin()) {
            throw new NotACustomerException(caller.username());
        }
        OpenAnotherAccountCommand command = result.hasErrors() ? null : toCommand(form, result);
        if (command != null) {
            try {
                AccountSummary account = commands.openAnotherAccount(caller, command);
                redirect.addFlashAttribute(AccountDetailView.MESSAGE,
                        "Nueva cuenta abierta. Tu IBAN es " + account.accountNumber());
                return AccountDetailView.redirectToDetail(account.id());
            } catch (DomainException e) {
                result.reject("domain", e.getMessage());
            }
        }
        model.addAttribute("currencies", AccountDetailView.CURRENCIES);
        return ANOTHER_VIEW;
    }

    /** El command, o {@code null} con el error en el formulario si lo rechaza (red de seguridad de la validación). */
    private static OpenAnotherAccountCommand toCommand(AnotherAccountForm form, BindingResult result) {
        try {
            return OpenAnotherAccountCommand.builder()
                    .initialDeposit(form.getInitialDeposit()).currency(form.getCurrency()).build();
        } catch (IllegalArgumentException e) {
            result.reject("invalid", e.getMessage());
            return null;
        }
    }
}
