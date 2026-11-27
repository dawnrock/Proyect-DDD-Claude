package com.dawnrock.ddd.adapter.in.rest;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dawnrock.ddd.adapter.in.ClientIpResolver;
import com.dawnrock.ddd.adapter.in.CustomerRegistration;
import com.dawnrock.ddd.application.SignUpCommand;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Alta pública de clientes: {@code POST /api/customers}, sin credenciales. Mapea la petición a un command y delega en
 * {@link CustomerRegistration} con la IP del cliente (límite de altas, #131), que abre la cuenta y crea el usuario. Los errores los traduce
 * {@link RestExceptionHandler}. Contrato: {@code docs/contracts/customer-sign-up.md}.
 * Solo existe con la autenticación local ({@code app.auth.local}, por defecto sí; #199): en el modo realista el alta es
 * por Auth0 en la web (#198) y esta ruta responde 404.
 */
@RestController
@ConditionalOnBooleanProperty(name = "app.auth.local", matchIfMissing = true)
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRegistration registration;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public ResponseEntity<AccountSummaryResponse> signUp(@Valid @RequestBody SignUpRequest request,
                                                         HttpServletRequest http) {
        SignUpCommand.Builder command = SignUpCommand.builder()
                .fullName(request.fullName())
                .taxId(request.taxId())
                .email(request.email())
                .username(request.username())
                .initialDeposit(request.initialDeposit());
        if (request.currency() != null) {
            command.currency(request.currency());
        }

        AccountSummaryResponse body = AccountController.toResponse(registration.register(command.build(), request.password(),
                clientIpResolver.resolve(http)));
        return ResponseEntity.created(URI.create("/api/accounts/" + body.id())).body(body);
    }
}
