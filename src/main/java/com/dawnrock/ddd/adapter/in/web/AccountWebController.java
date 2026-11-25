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

    private static final String FORM_VIEW = "accounts/new";

    private static final String ANOTHER_VIEW = "accounts/another";

    private final AccountOpeningCommands commands;

    private final MoneyMovementCommands movements;

    private final CheckingAccountQueries queries;

    private final WebFormat fmt;

    private final AccountDetailView detailView;

    @PostMapping("/accounts/{id}/account-number")
    public String regenerateAccountNumber(@PathVariable UUID id, Model model, RedirectAttributes redirect,
                                          Authentication authentication,
                                          @RequestHeader(name = HtmxLoginRedirect.HX_REQUEST, defaultValue = "false") boolean htmx) {
        Caller caller = CurrentCaller.from(authentication);
        AccountSummary account = commands.regenerateAccountNumber(caller, id);
        return detailView.notice(id, htmx, AccountDetailView.MESSAGE, "Nuevo IBAN asignado: " + account.accountNumber(), caller, model, redirect);
    }

    @PostMapping("/accounts/{id}/transfers")
    public String transfer(@PathVariable UUID id, @Valid @ModelAttribute("transferForm") TransferForm form,
                           BindingResult result, Model model, RedirectAttributes redirect, Authentication authentication,
                           @RequestHeader(name = HtmxLoginRedirect.HX_REQUEST, defaultValue = "false") boolean htmx) {
        Caller caller = CurrentCaller.from(authentication);
        if (!result.hasErrors()) {
            TransferCommand command = null;
            try {
                TransferCommand.Builder builder = TransferCommand.builder()
                        .sourceAccountId(id).targetAccountNumber(form.getTargetAccountNumber())
                        .amount(form.getAmount());
                if (form.getCurrency() != null) {
                    builder.currency(form.getCurrency());
                }
                command = builder.build();
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
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
                    return detailView.notice(id, htmx, AccountDetailView.MESSAGE, "Transferencia de %s a %s aprobada".formatted(amount, target),
                            caller, model, redirect);
                } catch (TargetAccountNotFoundException e) {
                    // El IBAN de destino lo ha escrito el usuario: se avisa en el formulario, no con la página 404.
                    // Va antes que cualquier NotFoundException: una cuenta de origen inexistente o ajena sí es 404.
                    result.rejectValue("targetAccountNumber", "notFound", e.getMessage());
                } catch (DomainException | IllegalArgumentException e) {
                    result.reject("domain", e.getMessage());
                }
            }
        }
        return detailView.render(id, caller, model, htmx);
    }

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

    @GetMapping("/accounts/new")
    public String newAccountForm(Model model) {
        model.addAttribute("form", new OpenAccountForm());
        addFormOptions(model);
        return FORM_VIEW;
    }

    @PostMapping("/accounts/new")
    public String openAccount(@Valid @ModelAttribute("form") OpenAccountForm form, BindingResult result,
                              Model model, RedirectAttributes redirect, Authentication authentication) {
        OpenAccountCommand command = result.hasErrors() ? null : toCommand(form, result);
        if (command != null) {
            try {
                AccountSummary account = commands.openAccount(CurrentCaller.from(authentication), command);
                redirect.addFlashAttribute("message", "Cuenta %s abierta correctamente como %s"
                        .formatted(account.accountNumber(), fmt.segment(account.segment())));
                return "redirect:/";
            } catch (DomainException | IllegalArgumentException e) {
                result.reject("domain", e.getMessage());
            }
        }
        addFormOptions(model);
        return FORM_VIEW;
    }

    /**
     * Construye el command, o deja el error en el formulario y devuelve {@code null} si lo rechaza (por ejemplo,
     * una divisa con formato válido pero que no existe): es la red de seguridad de la validación del formulario.
     */
    private OpenAccountCommand toCommand(OpenAccountForm form, BindingResult result) {
        try {
            return OpenAccountCommand.builder()
                    .holderTaxId(form.getHolderTaxId())
                    .holderName(Objects.requireNonNullElse(form.getHolderName(), ""))
                    .holderEmail(Objects.requireNonNullElse(form.getHolderEmail(), ""))
                    .initialDeposit(form.getInitialDeposit())
                    .currency(form.getCurrency())
                    .ownerUsername(Objects.requireNonNullElse(form.getOwnerUsername(), ""))
                    .build();
        } catch (IllegalArgumentException e) {
            result.reject("invalid", e.getMessage());
            return null;
        }
    }

    private void addFormOptions(Model model) {
        model.addAttribute("currencies", AccountDetailView.CURRENCIES);
    }
}
