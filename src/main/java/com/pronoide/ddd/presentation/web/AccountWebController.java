package com.pronoide.ddd.presentation.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.DepositCommand;
import com.pronoide.ddd.application.OpenAccountCommand;
import com.pronoide.ddd.application.TransactionSummary;
import com.pronoide.ddd.application.TransferCommand;
import com.pronoide.ddd.application.TransferSummary;
import com.pronoide.ddd.application.WithdrawCommand;
import com.pronoide.ddd.domain.model.DomainException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Pantallas web de las cuentas corrientes: listado, alta, detalle, ingresos y retiradas. */
@Controller
@RequiredArgsConstructor
public class AccountWebController {

    private static final String FORM_VIEW = "accounts/new";

    private static final String DETAIL_VIEW = "accounts/detail";

    private static final List<String> CURRENCIES = List.of("EUR", "USD", "GBP");

    private final CheckingAccountService service;

    private final WebFormat fmt;

    @GetMapping("/")
    public String list(Model model) {
        List<AccountSummary> accounts = service.listAccounts();
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
    public String detail(@PathVariable UUID id, Model model) {
        return renderDetail(id, model);
    }

    @PostMapping("/accounts/{id}/deposits")
    public String deposit(@PathVariable UUID id, @Valid @ModelAttribute("depositForm") MovementForm form,
                          BindingResult result, Model model, RedirectAttributes redirect) {
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
                    TransactionSummary deposit = service.deposit(command);
                    redirect.addFlashAttribute("message",
                            "Ingreso de %s aprobado".formatted(fmt.money(deposit.amount(), deposit.currency())));
                    return redirectToDetail(id);
                } catch (DomainException e) {
                    result.reject("domain", e.getMessage());
                }
            }
        }
        return renderDetail(id, model);
    }

    @PostMapping("/accounts/{id}/withdrawals")
    public String withdraw(@PathVariable UUID id, @Valid @ModelAttribute("withdrawalForm") MovementForm form,
                           BindingResult result, Model model, RedirectAttributes redirect) {
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
                    TransactionSummary withdrawal = service.withdraw(command);
                    String amount = fmt.money(withdrawal.amount(), withdrawal.currency());
                    if ("REJECTED".equals(withdrawal.status())) {
                        redirect.addFlashAttribute("rejection",
                                "Retirada de %s rechazada: el saldo resultante no está permitido para esta cuenta"
                                        .formatted(amount));
                    } else {
                        redirect.addFlashAttribute("message", "Retirada de %s aprobada".formatted(amount));
                    }
                    return redirectToDetail(id);
                } catch (DomainException e) {
                    result.reject("domain", e.getMessage());
                }
            }
        }
        return renderDetail(id, model);
    }

    @PostMapping("/accounts/{id}/transfers")
    public String transfer(@PathVariable UUID id, @Valid @ModelAttribute("transferForm") TransferForm form,
                           BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            TransferCommand command = null;
            try {
                TransferCommand.Builder builder = TransferCommand.builder()
                        .sourceAccountId(id).targetAccountId(form.getTargetAccountId()).amount(form.getAmount());
                if (form.getCurrency() != null) {
                    builder.currency(form.getCurrency());
                }
                command = builder.build();
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
            if (command != null) {
                try {
                    TransferSummary transfer = service.transfer(command);
                    String amount = fmt.money(transfer.amount(), transfer.currency());
                    String target = fmt.maskedIban(transfer.targetAccountNumber());
                    if ("REJECTED".equals(transfer.status())) {
                        redirect.addFlashAttribute("rejection",
                                ("Transferencia de %s a %s rechazada: el saldo resultante no está permitido "
                                        + "para la cuenta de origen").formatted(amount, target));
                    } else {
                        redirect.addFlashAttribute("message", "Transferencia de %s a %s aprobada"
                                .formatted(amount, target));
                    }
                    return redirectToDetail(id);
                } catch (DomainException e) {
                    result.reject("domain", e.getMessage());
                }
            }
        }
        return renderDetail(id, model);
    }

    /** Pinta el detalle; los formularios que no vienen del envío actual se crean vacíos. */
    private String renderDetail(UUID id, Model model) {
        if (!model.containsAttribute("depositForm")) {
            model.addAttribute("depositForm", new MovementForm());
        }
        if (!model.containsAttribute("withdrawalForm")) {
            model.addAttribute("withdrawalForm", new MovementForm());
        }
        if (!model.containsAttribute("transferForm")) {
            model.addAttribute("transferForm", new TransferForm());
        }
        model.addAttribute("account", service.findAccount(id));
        model.addAttribute("targetAccounts", service.listAccounts().stream()
                .filter(other -> !other.id().equals(id))
                .toList());
        model.addAttribute("currencies", CURRENCIES);
        return DETAIL_VIEW;
    }

    private static String redirectToDetail(UUID id) {
        return "redirect:/accounts/" + id;
    }

    @GetMapping("/accounts/new")
    public String newAccountForm(Model model) {
        model.addAttribute("form", new OpenAccountForm());
        addFormOptions(model);
        return FORM_VIEW;
    }

    @PostMapping("/accounts/new")
    public String openAccount(@Valid @ModelAttribute("form") OpenAccountForm form, BindingResult result,
                              Model model, RedirectAttributes redirect) {
        OpenAccountCommand command = result.hasErrors() ? null : toCommand(form, result);
        if (command != null) {
            try {
                AccountSummary account = service.openAccount(command);
                redirect.addFlashAttribute("message",
                        "Cuenta %s abierta correctamente".formatted(account.accountNumber()));
                return "redirect:/";
            } catch (DomainException e) {
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
                    .accountNumber(form.getAccountNumber())
                    .holderName(form.getHolderName())
                    .segment(form.getSegment())
                    .initialDeposit(form.getInitialDeposit())
                    .currency(form.getCurrency())
                    .build();
        } catch (IllegalArgumentException e) {
            result.reject("invalid", e.getMessage());
            return null;
        }
    }

    private void addFormOptions(Model model) {
        Map<String, String> segments = new LinkedHashMap<>();
        segments.put("RETAIL", "Minorista");
        segments.put("HIGH_NET_WORTH", "Alto patrimonio");
        model.addAttribute("segments", segments);
        model.addAttribute("currencies", List.of("EUR", "USD", "GBP"));
    }
}
