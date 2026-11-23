package com.dawnrock.ddd.presentation.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.CheckingAccountService;
import com.dawnrock.ddd.application.DepositCommand;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.application.TransactionSummary;
import com.dawnrock.ddd.application.WithdrawCommand;
import com.dawnrock.ddd.presentation.CurrentCaller;

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
    public ResponseEntity<AccountSummaryResponse> openAccount(
            Authentication authentication, @Valid @RequestBody OpenAccountRequest request) {
        OpenAccountCommand.Builder command = OpenAccountCommand.builder()
                .holderTaxId(request.holderTaxId())
                .initialDeposit(request.initialDeposit());
        if (request.currency() != null) {
            command.currency(request.currency());
        }
        if (request.holderName() != null) {
            command.holderName(request.holderName());
        }
        if (request.holderEmail() != null) {
            command.holderEmail(request.holderEmail());
        }
        if (request.ownerUsername() != null) {
            command.ownerUsername(request.ownerUsername());
        }

        AccountSummaryResponse body = toResponse(service.openAccount(CurrentCaller.from(authentication), command.build()));
        return ResponseEntity.created(URI.create("/api/accounts/" + body.id())).body(body);
    }

    /** Sustituye el IBAN de la cuenta por otro que genera el banco. Solo administradores (R31). */
    @PostMapping("/{id}/account-number")
    public AccountSummaryResponse regenerateAccountNumber(Authentication authentication, @PathVariable UUID id) {
        return toResponse(service.regenerateAccountNumber(CurrentCaller.from(authentication), id));
    }

    @GetMapping
    public List<AccountSummaryResponse> listAccounts(Authentication authentication) {
        return service.listAccounts(CurrentCaller.from(authentication)).stream().map(AccountController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public AccountDetailsResponse findAccount(Authentication authentication, @PathVariable UUID id) {
        return toResponse(service.findAccount(CurrentCaller.from(authentication), id));
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody MovementRequest request) {
        DepositCommand.Builder command = DepositCommand.builder().accountId(id).amount(request.amount());
        if (request.currency() != null) {
            command.currency(request.currency());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(service.deposit(CurrentCaller.from(authentication), command.build())));
    }

    @PostMapping("/{id}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody MovementRequest request) {
        WithdrawCommand.Builder command = WithdrawCommand.builder().accountId(id).amount(request.amount());
        if (request.currency() != null) {
            command.currency(request.currency());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(service.withdraw(CurrentCaller.from(authentication), command.build())));
    }

    static AccountSummaryResponse toResponse(AccountSummary summary) {
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
                .holderTaxId(details.holderTaxId())
                .holderEmail(details.holderEmail())
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
