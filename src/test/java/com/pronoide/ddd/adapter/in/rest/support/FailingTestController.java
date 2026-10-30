package com.pronoide.ddd.adapter.in.rest.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pronoide.ddd.application.NotFoundException;
import com.pronoide.ddd.domain.model.DomainException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Controlador solo para tests: lanza cada tipo de error para comprobar su traducción a HTTP. Como la
 * presentación, solo conoce las excepciones del dominio (D4), no sus agregados ni objetos de valor.
 */
@RestController
@RequestMapping("/test/errors")
public class FailingTestController {

    public record SampleRequest(@NotBlank String holderName, @Positive int amount) {
    }

    static class SampleBusinessRuleException extends DomainException {
        SampleBusinessRuleException() {
            super("Regla de prueba incumplida");
        }
    }

    static class SampleNotFoundException extends NotFoundException {
        SampleNotFoundException() {
            super("No existe el recurso de prueba 42");
        }
    }

    @GetMapping("/business-rule")
    public void businessRule() {
        throw new SampleBusinessRuleException();
    }

    @GetMapping("/not-found")
    public void notFound() {
        throw new SampleNotFoundException();
    }

    @GetMapping("/forbidden")
    public void forbidden() {
        throw new com.pronoide.ddd.application.ForbiddenOperationException("abrir cuentas");
    }

    @GetMapping("/invalid-argument")
    public void invalidArgument() {
        throw new IllegalArgumentException("El argumento de prueba no es válido");
    }

    @PostMapping("/validation")
    public void validation(@Valid @RequestBody SampleRequest request) {
    }
}
