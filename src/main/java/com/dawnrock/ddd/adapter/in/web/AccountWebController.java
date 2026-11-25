package com.dawnrock.ddd.adapter.in.web;

import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.DepositCommand;
import com.dawnrock.ddd.application.NotACustomerException;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.application.OpenAnotherAccountCommand;
import com.dawnrock.ddd.application.TargetAccountNotFoundException;
import com.dawnrock.ddd.application.TransactionSummary;
import com.dawnrock.ddd.application.TransferCommand;
import com.dawnrock.ddd.application.TransferSummary;
import com.dawnrock.ddd.application.WithdrawCommand;
import com.dawnrock.ddd.domain.model.DomainException;
import com.dawnrock.ddd.adapter.in.CurrentCaller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Pantallas web de las cuentas corrientes: listado, alta, detalle, ingresos y retiradas. */
@Controller
@RequiredArgsConstructor
public class AccountWebController {

    private static final String ANOTHER_VIEW = "accounts/another";

    private final AccountOpeningCommands commands;

    private final MoneyMovementCommands movements;

    private final CheckingAccountQueries queries;

    private final WebFormat fmt;

    private final AccountDetailView detailView;

    @GetMapping("/accounts/another")
    public String anotherAccountForm(Model model, Authentication authentication) {
        Caller caller = CurrentCaller.from(authentication);
        if (!detailView.isCustomer(caller)) {
            throw new NotACustomerException(caller.username());
        }
        model.addAttribute("form", new AnotherAccountForm());
        addFormOptions(model);
        return ANOTHER_VIEW;
    }

    @PostMapping("/accounts/another")
    public String openAnotherAccount(@Valid @ModelAttribute("form") AnotherAccountForm form, BindingResult result,
                                     Model model, RedirectAttributes redirect, Authentication authentication) {
        Caller caller = CurrentCaller.from(authentication);
        if (caller.admin()) {
            throw new NotACustomerException(caller.username());
        }
        OpenAnotherAccountCommand command = null;
        if (!result.hasErrors()) {
            try {
                command = OpenAnotherAccountCommand.builder()
                        .initialDeposit(form.getInitialDeposit()).currency(form.getCurrency()).build();
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
        }
        if (command != null) {
            try {
                AccountSummary account = commands.openAnotherAccount(caller, command);
                redirect.addFlashAttribute("message", "Nueva cuenta abierta. Tu IBAN es " + account.accountNumber());
                return AccountDetailView.redirectToDetail(account.id());
            } catch (DomainException e) {
                result.reject("domain", e.getMessage());
            }
        }
        addFormOptions(model);
        return ANOTHER_VIEW;
    }

    private void addFormOptions(Model model) {
        model.addAttribute("currencies", AccountDetailView.CURRENCIES);
    }
}
