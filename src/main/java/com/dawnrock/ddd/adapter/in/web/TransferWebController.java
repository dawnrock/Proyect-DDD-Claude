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
import com.dawnrock.ddd.application.TargetAccountNotFoundException;
import com.dawnrock.ddd.application.TransferCommand;
import com.dawnrock.ddd.application.TransferSummary;
import com.dawnrock.ddd.domain.model.DomainException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Transferencia desde el detalle de la cuenta de origen (#177). Un IBAN de destino que no existe es un error del
 * formulario; una cuenta de origen ajena, un 404 (ADR 0008). Una transferencia rechazada por el saldo se avisa como
 * rechazo. Con htmx responde el panel; sin htmx, Post/Redirect/Get (#125).
 */
@Controller
@RequiredArgsConstructor
public class TransferWebController {

    private final MoneyMovementCommands movements;

    private final WebFormat fmt;

    private final AccountDetailView detailView;

    @PostMapping("/accounts/{id}/transfers")
    public String transfer(@PathVariable UUID id, @Valid @ModelAttribute("transferForm") TransferForm form,
                           BindingResult result, Model model, RedirectAttributes redirect, Authentication authentication,
                           @RequestHeader(name = HtmxLoginRedirect.HX_REQUEST, defaultValue = "false") boolean htmx) {
        Caller caller = CurrentCaller.from(authentication);
        TransferCommand command = result.hasErrors() ? null : toCommand(id, form, result);
        if (command != null) {
            try {
                TransferSummary transfer = movements.transfer(caller, command);
                String amount = fmt.money(transfer.amount(), transfer.currency());
                String target = fmt.maskedIban(transfer.targetAccountNumber());
                if (transfer.rejected()) {
                    return detailView.notice(id, htmx, AccountDetailView.REJECTION,
                            ("Transferencia de %s a %s rechazada: el saldo resultante no está permitido "
                                    + "para la cuenta de origen").formatted(amount, target),
                            caller, model, redirect);
                }
                return detailView.notice(id, htmx, AccountDetailView.MESSAGE,
                        "Transferencia de %s a %s aprobada".formatted(amount, target), caller, model, redirect);
            } catch (TargetAccountNotFoundException e) {
                // El IBAN de destino lo ha escrito el usuario: se avisa en el formulario, no con la página 404.
                // Va antes que cualquier NotFoundException: una cuenta de origen inexistente o ajena sí es 404.
                result.rejectValue("targetAccountNumber", "notFound", e.getMessage());
            } catch (DomainException | IllegalArgumentException e) {
                result.reject("domain", e.getMessage());
            }
        }
        return detailView.render(id, caller, model, htmx);
    }

    /** El command, o {@code null} con el error en el formulario si lo rechaza (red de seguridad de la validación). */
    private static TransferCommand toCommand(UUID id, TransferForm form, BindingResult result) {
        try {
            TransferCommand.Builder builder = TransferCommand.builder()
                    .sourceAccountId(id).targetAccountNumber(form.getTargetAccountNumber())
                    .amount(form.getAmount());
            if (form.getCurrency() != null) {
                builder.currency(form.getCurrency());
            }
            return builder.build();
        } catch (IllegalArgumentException e) {
            result.reject("invalid", e.getMessage());
            return null;
        }
    }
}
