package com.pronoide.ddd.adapter.in.rest;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pronoide.ddd.application.SignUpCommand;
import com.pronoide.ddd.adapter.in.CustomerRegistration;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Alta pública de clientes: {@code POST /api/customers}, sin credenciales. Mapea la petición a un command y delega en
 * {@link CustomerRegistration}, que abre la cuenta y crea el usuario. Los errores los traduce
 * {@link RestExceptionHandler}. Contrato: {@code docs/contracts/customer-sign-up.md}.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRegistration registration;

    @PostMapping
    public ResponseEntity<AccountSummaryResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpCommand.Builder command = SignUpCommand.builder()
                .fullName(request.fullName())
                .taxId(request.taxId())
                .email(request.email())
                .username(request.username())
                .initialDeposit(request.initialDeposit());
        if (request.currency() != null) {
            command.currency(request.currency());
        }

        AccountSummaryResponse body = AccountController.toResponse(registration.register(command.build(), request.password()));
        return ResponseEntity.created(URI.create("/api/accounts/" + body.id())).body(body);
    }
}
