package com.pronoide.ddd.presentation.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pronoide.ddd.application.AccountDetails;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.DepositCommand;
import com.pronoide.ddd.application.OpenAccountCommand;
import com.pronoide.ddd.application.TransactionSummary;
import com.pronoide.ddd.application.WithdrawCommand;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * API REST de cuentas: {@code /api/accounts}. Mapea la petición a un command, delega en
 * {@link CheckingAccountService} y convierte las vistas de aplicación en DTOs de respuesta. Los errores
 * los traduce {@link RestExceptionHandler}. Contrato: {@code docs/contracts/open-query-list-accounts.md}.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final CheckingAccountService service;

    @PostMapping
    public ResponseEntity<AccountSummaryResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        OpenAccountCommand.Builder command = OpenAccountCommand.builder()
                .accountNumber(request.accountNumber())
                .holderName(request.holderName())
                .segment(request.segment())
                .initialDeposit(request.initialDeposit());
        if (request.currency() != null) {
            command.currency(request.currency());
        }

        AccountSummaryResponse body = toResponse(service.openAccount(command.build()));
        return ResponseEntity.created(URI.create("/api/accounts/" + body.id())).body(body);
    }

    @GetMapping
    public List<AccountSummaryResponse> listAccounts() {
        return service.listAccounts().stream().map(AccountController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public AccountDetailsResponse findAccount(@PathVariable UUID id) {
        return toResponse(service.findAccount(id));
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID id, @Valid @RequestBody MovementRequest request) {
        DepositCommand.Builder command = DepositCommand.builder().accountId(id).amount(request.amount());
        if (request.currency() != null) {
            command.currency(request.currency());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(service.deposit(command.build())));
    }

    @PostMapping("/{id}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID id, @Valid @RequestBody MovementRequest request) {
        WithdrawCommand.Builder command = WithdrawCommand.builder().accountId(id).amount(request.amount());
        if (request.currency() != null) {
            command.currency(request.currency());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(service.withdraw(command.build())));
    }

    private static AccountSummaryResponse toResponse(AccountSummary summary) {
        return AccountSummaryResponse.builder()
                .id(summary.id())
                .accountNumber(summary.accountNumber())
                .holderName(summary.holderName())
                .balance(summary.balance())
                .currency(summary.currency())
                .build();
    }

    private static AccountDetailsResponse toResponse(AccountDetails details) {
        return AccountDetailsResponse.builder()
                .id(details.id())
                .accountNumber(details.accountNumber())
                .holderName(details.holderName())
                .segment(details.segment())
                .balance(details.balance())
                .currency(details.currency())
                .transactions(details.transactions().stream().map(AccountController::toResponse).toList())
                .build();
    }

    private static TransactionResponse toResponse(TransactionSummary transaction) {
        return TransactionResponse.builder()
                .id(transaction.id())
                .type(transaction.type())
                .amount(transaction.amount())
                .currency(transaction.currency())
                .occurredOn(transaction.occurredOn())
                .status(transaction.status())
                .build();
    }
}
