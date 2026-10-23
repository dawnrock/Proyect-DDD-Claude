package com.pronoide.ddd.presentation.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

    private final CheckingAccountService service;

    @GetMapping("/")
    public String list(Model model) {
        model.addAttribute("accounts", service.listAccounts());
        return "accounts/list";
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
        if (!result.hasErrors()) {
            try {
                AccountSummary account = service.openAccount(toCommand(form));
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

    private OpenAccountCommand toCommand(OpenAccountForm form) {
        return OpenAccountCommand.builder()
                .accountNumber(form.getAccountNumber())
                .holderName(form.getHolderName())
                .segment(form.getSegment())
                .initialDeposit(form.getInitialDeposit())
                .currency(form.getCurrency())
                .build();
    }

    private void addFormOptions(Model model) {
        Map<String, String> segments = new LinkedHashMap<>();
        segments.put("RETAIL", "Minorista");
        segments.put("HIGH_NET_WORTH", "Alto patrimonio");
        model.addAttribute("segments", segments);
        model.addAttribute("currencies", List.of("EUR", "USD", "GBP"));
    }
}
