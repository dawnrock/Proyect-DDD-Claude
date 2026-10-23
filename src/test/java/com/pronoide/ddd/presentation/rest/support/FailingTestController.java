package com.pronoide.ddd.presentation.rest.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pronoide.ddd.application.NotFoundException;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.MonetaryAmount;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Controlador solo para tests: lanza cada tipo de error para comprobar su traducción a HTTP. */
@RestController
@RequestMapping("/test/errors")
public class FailingTestController {

    public record SampleRequest(@NotBlank String holderName, @Positive int amount) {
    }

    static class SampleNotFoundException extends NotFoundException {
        SampleNotFoundException() {
            super("No existe el recurso de prueba 42");
        }
    }

    @GetMapping("/business-rule")
    public void businessRule() {
        throw new InsufficientInitialDepositException(MonetaryAmount.euros("10.00"), MonetaryAmount.euros("50.00"));
    }

    @GetMapping("/not-found")
    public void notFound() {
        throw new SampleNotFoundException();
    }

    @GetMapping("/invalid-argument")
    public void invalidArgument() {
        new AccountNumber("ES00");
    }

    @PostMapping("/validation")
    public void validation(@Valid @RequestBody SampleRequest request) {
    }
}
