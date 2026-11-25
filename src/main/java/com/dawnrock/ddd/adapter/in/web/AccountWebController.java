package com.dawnrock.ddd.adapter.in.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

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

import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountCommands;
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

    private static final String DETAIL_VIEW = "accounts/detail";

    /** Fragmento del detalle que htmx sustituye tras cada operación (#125). */
    private static final String DETAIL_PANEL = DETAIL_VIEW + " :: panel";

    /** Aviso de una operación aprobada (alerta de éxito en la plantilla). */
    private static final String MESSAGE = "message";

    /** Aviso de una operación rechazada (alerta de advertencia en la plantilla). */
    private static final String REJECTION = "rejection";

    private static final String ANOTHER_VIEW = "accounts/another";

    private static final List<String> CURRENCIES = List.of("EUR", "USD", "GBP");

    private final CheckingAccountCommands commands;

    private final MoneyMovementCommands movements;

    private final CheckingAccountQueries queries;

    private final WebFormat fmt;

    @GetMapping("/")
    public String list(Authentication authentication, Model model) {
        Caller caller = CurrentCaller.from(authentication);
        List<AccountSummary> accounts = queries.listAccounts(caller);
        if (!caller.admin() && accounts.size() == 1) {
            return redirectToDetail(accounts.get(0).id());
        }
        model.addAttribute("admin", caller.admin());
        model.addAttribute("customer", isCustomer(caller, accounts));
        model.addAttribute("accounts", accounts);
        model.addAttribute("totals", totalsByCurrency(accounts));
        return "accounts/list";
    }

    /** Saldo total de las cuentas, sumado por divisa (no se mezclan divisas). */
    private static Map<String, BigDecimal> totalsByCurrency(List<AccountSummary> accounts) {
        return accounts.stream().collect(Collectors.groupingBy(AccountSummary::currency, TreeMap::new,
                Collectors.reducing(BigDecimal.ZERO, AccountSummary::balance, BigDecimal::add)));
    }

    @GetMapping("/accounts/{id}")
    public String detail(@PathVariable UUID id, Authentication authentication, Model model) {
        return renderDetail(id, CurrentCaller.from(authentication), model, false);
    }

    @PostMapping("/accounts/{id}/account-number")
    public String regenerateAccountNumber(@PathVariable UUID id, Model model, RedirectAttributes redirect,
                                          Authentication authentication,
                                          @RequestHeader(name = HtmxLoginRedirect.HX_REQUEST, defaultValue = "false") boolean htmx) {
        Caller caller = CurrentCaller.from(authentication);
        AccountSummary account = commands.regenerateAccountNumber(caller, id);
        return notice(id, htmx, MESSAGE, "Nuevo IBAN asignado: " + account.accountNumber(), caller, model, redirect);
    }

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
                    return notice(id, htmx, MESSAGE,
                            "Ingreso de %s aprobado".formatted(fmt.money(deposit.amount(), deposit.currency())),
                            caller, model, redirect);
                } catch (DomainException e) {
                    result.reject("domain", e.getMessage());
                }
            }
        }
        return renderDetail(id, caller, model, htmx);
    }

    @PostMapping("/accounts/{id}/withdrawals")
    public String withdraw(@PathVariable UUID id, @Valid @ModelAttribute("withdrawalForm") MovementForm form,
                           BindingResult result, Model model, RedirectAttributes redirect, Authentication authentication,
                           @RequestHeader(name = HtmxLoginRedirect.HX_REQUEST, defaultValue = "false") boolean htmx) {
        Caller caller = CurrentCaller.from(authentication);
        if (!result.hasErrors()) {
            WithdrawCommand command = null;
            try {
                command = WithdrawCommand.builder()
                        .accountId(id).amount(form.getAmount()).currency(form.getCurrency()).build();
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
            if (command != null) {
                try {
                    TransactionSummary withdrawal = movements.withdraw(caller, command);
                    String amount = fmt.money(withdrawal.amount(), withdrawal.currency());
                    if (withdrawal.rejected()) {
                        return notice(id, htmx, REJECTION,
                                "Retirada de %s rechazada: el saldo resultante no está permitido para esta cuenta"
                                        .formatted(amount), caller, model, redirect);
                    }
                    return notice(id, htmx, MESSAGE, "Retirada de %s aprobada".formatted(amount),
                            caller, model, redirect);
                } catch (DomainException e) {
                    result.reject("domain", e.getMessage());
                }
            }
        }
        return renderDetail(id, caller, model, htmx);
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
                        return notice(id, htmx, REJECTION,
                                ("Transferencia de %s a %s rechazada: el saldo resultante no está permitido "
                                        + "para la cuenta de origen").formatted(amount, target),
                                caller, model, redirect);
                    }
                    return notice(id, htmx, MESSAGE, "Transferencia de %s a %s aprobada".formatted(amount, target),
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
        return renderDetail(id, caller, model, htmx);
    }

    /**
     * Resultado de una operación correcta (#125). A htmx se le responde el panel con el aviso en el modelo y los
     * formularios vacíos; sin htmx, el aviso va como atributo flash y se redirige al detalle (Post/Redirect/Get).
     */
    private String notice(UUID id, boolean htmx, String kind, String text, Caller caller, Model model,
                          RedirectAttributes redirect) {
        if (!htmx) {
            redirect.addFlashAttribute(kind, text);
            return redirectToDetail(id);
        }
        // El formulario enviado y su BindingResult repintarían los valores escritos: se quitan para empezar de cero
        for (String form : List.of("depositForm", "withdrawalForm", "transferForm")) {
            model.asMap().remove(form);
            model.asMap().remove(BindingResult.MODEL_KEY_PREFIX + form);
        }
        model.addAttribute(kind, text);
        return renderDetail(id, caller, model, true);
    }

    /**
     * Pinta el detalle; los formularios que no vienen del envío actual se crean vacíos. A htmx solo se le devuelve el
     * panel de la cuenta, que sustituye al que tiene la página (#125).
     */
    private String renderDetail(UUID id, Caller caller, Model model, boolean htmx) {
        if (!model.containsAttribute("depositForm")) {
            model.addAttribute("depositForm", new MovementForm());
        }
        if (!model.containsAttribute("withdrawalForm")) {
            model.addAttribute("withdrawalForm", new MovementForm());
        }
        if (!model.containsAttribute("transferForm")) {
            model.addAttribute("transferForm", new TransferForm());
        }
        // Primero la cuenta: una ajena es un 404 sin consultar nada más
        AccountDetails account = queries.findAccount(caller, id);
        // Una consulta más al listado para saber si es cliente (contrato customer-sign-up): el administrador no la hace
        List<AccountSummary> own = caller.admin() ? List.of() : queries.listAccounts(caller);
        model.addAttribute("account", account);
        // application devuelve los movimientos del más antiguo al más reciente; la web los pinta al revés. Se invierte
        // una copia, sin ordenar por fecha: dos movimientos pueden compartir instante.
        List<TransactionSummary> newestFirst = new ArrayList<>(List.copyOf(account.transactions()));
        Collections.reverse(newestFirst);
        model.addAttribute("transactions", newestFirst);
        model.addAttribute("admin", caller.admin());
        model.addAttribute("backLabel", backLabel(caller, own));
        model.addAttribute("customer", isCustomer(caller, own));
        model.addAttribute("currencies", CURRENCIES);
        return htmx ? DETAIL_PANEL : DETAIL_VIEW;
    }

    /**
     * Texto del enlace de vuelta al inicio: «Cuentas» para el administrador, «Mis cuentas» para un usuario con varias
     * cuentas y ninguno con una sola (su inicio es este mismo detalle).
     */
    private String backLabel(Caller caller, List<AccountSummary> own) {
        if (caller.admin()) {
            return "Cuentas";
        }
        return own.size() > 1 ? "Mis cuentas" : null;
    }

    /** Un cliente es quien no es administrador y tiene alguna cuenta (contrato customer-sign-up). */
    private static boolean isCustomer(Caller caller, List<AccountSummary> accounts) {
        return !caller.admin() && !accounts.isEmpty();
    }

    private static String redirectToDetail(UUID id) {
        return "redirect:/accounts/" + id;
    }

    @GetMapping("/accounts/another")
    public String anotherAccountForm(Model model, Authentication authentication) {
        Caller caller = CurrentCaller.from(authentication);
        if (!isCustomer(caller, caller.admin() ? List.of() : queries.listAccounts(caller))) {
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
                return redirectToDetail(account.id());
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
        model.addAttribute("currencies", List.of("EUR", "USD", "GBP"));
    }
}
