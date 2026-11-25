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
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.TransactionSummary;
import com.dawnrock.ddd.application.WithdrawCommand;
import com.dawnrock.ddd.domain.model.DomainException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Retirada desde el detalle de la cuenta (#177). Una retirada rechazada por el saldo no es un error: se avisa como
 * rechazo. Con htmx responde el panel; sin htmx, Post/Redirect/Get (#125).
 */
@Controller
@RequiredArgsConstructor
public class WithdrawalWebController {

    private final MoneyMovementCommands movements;

    private final WebFormat fmt;

    private final AccountDetailView detailView;

    @PostMapping("/accounts/{id}/withdrawals")
    public String withdraw(@PathVariable UUID id, @Valid @ModelAttribute("withdrawalForm") MovementForm form,
                           BindingResult result, Model model, RedirectAttributes redirect, Authentication authentication,
                           @RequestHeader(name = HtmxLoginRedirect.HX_REQUEST, defaultValue = "false") boolean htmx) {
        Caller caller = CurrentCaller.from(authentication);
        WithdrawCommand command = result.hasErrors() ? null : toCommand(id, form, result);
        if (command != null) {
            try {
                TransactionSummary withdrawal = movements.withdraw(caller, command);
                String amount = fmt.money(withdrawal.amount(), withdrawal.currency());
                if (withdrawal.rejected()) {
                    return detailView.notice(id, htmx, AccountDetailView.REJECTION,
                            "Retirada de %s rechazada: el saldo resultante no está permitido para esta cuenta"
                                    .formatted(amount), caller, model, redirect);
                }
                return detailView.notice(id, htmx, AccountDetailView.MESSAGE,
                        "Retirada de %s aprobada".formatted(amount), caller, model, redirect);
            } catch (DomainException e) {
                result.reject("domain", e.getMessage());
            }
        }
        return detailView.render(id, caller, model, htmx);
    }

    /** El command, o {@code null} con el error en el formulario si lo rechaza (red de seguridad de la validación). */
    private static WithdrawCommand toCommand(UUID id, MovementForm form, BindingResult result) {
        try {
            return WithdrawCommand.builder().accountId(id).amount(form.getAmount()).currency(form.getCurrency()).build();
        } catch (IllegalArgumentException e) {
            result.reject("invalid", e.getMessage());
            return null;
        }
    }
}
