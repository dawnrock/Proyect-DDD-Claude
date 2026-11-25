package com.dawnrock.ddd.adapter.in.web;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dawnrock.ddd.adapter.in.CurrentCaller;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.DepositCommand;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.TransactionSummary;
import com.dawnrock.ddd.domain.model.DomainException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Ingreso desde el detalle de la cuenta (#177). Con htmx responde el panel con el aviso; sin htmx, Post/Redirect/Get
 * (#125). Un error del formulario o del dominio vuelve a pintar el detalle con el mensaje en el formulario.
 */
@Controller
@RequiredArgsConstructor
public class DepositWebController {

    private final MoneyMovementCommands movements;

    private final WebFormat fmt;

    private final AccountDetailView detailView;

    @PostMapping("/accounts/{id}/deposits")
    public String deposit(@PathVariable UUID id, @Valid @ModelAttribute("depositForm") MovementForm form,
                          BindingResult result, Model model, RedirectAttributes redirect, Authentication authentication,
                          @RequestHeader(name = HtmxLoginRedirect.HX_REQUEST, defaultValue = "false") boolean htmx) {
        Caller caller = CurrentCaller.from(authentication);
        if (!result.hasErrors()) {
            DepositCommand command = null;
            try {
                command = DepositCommand.builder()
                        .accountId(id).amount(form.getAmount()).currency(form.getCurrency()).build();
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
            if (command != null) {
                try {
                    TransactionSummary deposit = movements.deposit(caller, command);
                    return detailView.notice(id, htmx, AccountDetailView.MESSAGE,
                            "Ingreso de %s aprobado".formatted(fmt.money(deposit.amount(), deposit.currency())),
                            caller, model, redirect);
                } catch (DomainException e) {
                    result.reject("domain", e.getMessage());
                }
            }
        }
        return detailView.render(id, caller, model, htmx);
    }
}
