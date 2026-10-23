package com.pronoide.ddd.presentation.rest.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.MonetaryAmount;

/** Controlador solo para tests: lanza cada tipo de error para comprobar su traducción a HTTP. */
@RestController
@RequestMapping("/test/errors")
public class FailingTestController {

    @GetMapping("/business-rule")
    public void businessRule() {
        throw new InsufficientInitialDepositException(MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00"));
    }
}
