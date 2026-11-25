package com.dawnrock.ddd.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.dawnrock.ddd.adapter.in.CurrentCaller;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountQueries;

import lombok.RequiredArgsConstructor;

/**
 * Inicio de la web (#177): el listado de cuentas con sus saldos totales por divisa. Un usuario con una sola cuenta va
 * directamente a su detalle.
 */
@Controller
@RequiredArgsConstructor
public class AccountListController {

    private final CheckingAccountQueries queries;

    @GetMapping("/")
    public String list(Authentication authentication, Model model) {
        Caller caller = CurrentCaller.from(authentication);
        List<AccountSummary> accounts = queries.listAccounts(caller);
        if (!caller.admin() && accounts.size() == 1) {
            return AccountDetailView.redirectToDetail(accounts.get(0).id());
        }
        model.addAttribute("admin", caller.admin());
        model.addAttribute("customer", AccountDetailView.isCustomer(caller, accounts));
        model.addAttribute("accounts", accounts);
        model.addAttribute("totals", totalsByCurrency(accounts));
        return "accounts/list";
    }

    /** Saldo total de las cuentas, sumado por divisa (no se mezclan divisas). */
    private static Map<String, BigDecimal> totalsByCurrency(List<AccountSummary> accounts) {
        return accounts.stream().collect(Collectors.groupingBy(AccountSummary::currency, TreeMap::new,
                Collectors.reducing(BigDecimal.ZERO, AccountSummary::balance, BigDecimal::add)));
    }
}
