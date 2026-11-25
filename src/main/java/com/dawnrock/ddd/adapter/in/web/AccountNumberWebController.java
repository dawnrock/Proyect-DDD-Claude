package com.dawnrock.ddd.adapter.in.web;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dawnrock.ddd.adapter.in.CurrentCaller;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;

import lombok.RequiredArgsConstructor;

/** Nuevo IBAN para una cuenta, desde su detalle (R32, #177). Solo un administrador; lo decide la aplicación. */
@Controller
@RequiredArgsConstructor
public class AccountNumberWebController {

    private final AccountOpeningCommands commands;

    private final AccountDetailView detailView;

    @PostMapping("/accounts/{id}/account-number")
    public String regenerateAccountNumber(
            @PathVariable UUID id, Model model, RedirectAttributes redirect, Authentication authentication,
            @RequestHeader(name = HtmxLoginRedirect.HX_REQUEST, defaultValue = "false") boolean htmx) {
        Caller caller = CurrentCaller.from(authentication);
        AccountSummary account = commands.regenerateAccountNumber(caller, id);
        return detailView.notice(id, htmx, AccountDetailView.MESSAGE, "Nuevo IBAN asignado: " + account.accountNumber(),
                caller, model, redirect);
    }
}
