package com.dawnrock.ddd.adapter.in.web;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.dawnrock.ddd.adapter.in.CurrentCaller;

import lombok.RequiredArgsConstructor;

/**
 * Detalle de una cuenta con sus movimientos y los formularios de sus operaciones (#177). Una cuenta ajena es un 404
 * (ADR 0008); las operaciones tienen cada una su controlador.
 */
@Controller
@RequiredArgsConstructor
public class AccountDetailController {

    private final AccountDetailView detailView;

    @GetMapping("/accounts/{id}")
    public String detail(@PathVariable UUID id, Authentication authentication, Model model) {
        return detailView.render(id, CurrentCaller.from(authentication), model, false);
    }
}
