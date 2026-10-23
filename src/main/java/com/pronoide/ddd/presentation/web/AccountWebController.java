package com.pronoide.ddd.presentation.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.pronoide.ddd.application.OpenAccountCommand;
import com.pronoide.ddd.domain.model.DomainException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Pantallas web de las cuentas corrientes: listado y alta. */
@Controller
@RequiredArgsConstructor
public class AccountWebController {

    private static final String FORM_VIEW = "accounts/new";

    private static final String DETAIL_VIEW = "accounts/detail";

    private final CheckingAccountService service;

    @GetMapping("/")
    public String list(Model model) {
        model.addAttribute("accounts", service.listAccounts());
        return "accounts/list";
    }

    @GetMapping("/accounts/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("account", service.findAccount(id));
        return DETAIL_VIEW;
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
