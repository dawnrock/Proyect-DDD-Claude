package com.pronoide.ddd.presentation.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.pronoide.ddd.application.CheckingAccountService;

import lombok.RequiredArgsConstructor;

/** Pantallas web de las cuentas corrientes: listado y alta. */
@Controller
@RequiredArgsConstructor
public class AccountWebController {

    private final CheckingAccountService service;

    @GetMapping("/")
    public String list(Model model) {
        model.addAttribute("accounts", service.listAccounts());
        return "accounts/list";
    }
}
