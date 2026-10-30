package com.pronoide.ddd.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.TransferCommand;
import com.pronoide.ddd.application.TransferSummary;
import com.pronoide.ddd.adapter.in.CurrentCaller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * API REST de transferencias: {@code POST /api/transfers}. No cuelga de {@code /api/accounts} porque la transferencia
 * no es un recurso de una cuenta ni tiene {@code Location}. Una transferencia rechazada por el saldo del origen responde
 * 201 con estado {@code REJECTED}. Contrato: {@code docs/contracts/money-transfers.md}.
 */
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final CheckingAccountService service;

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            Authentication authentication, @Valid @RequestBody TransferRequest request) {
        TransferCommand.Builder command = TransferCommand.builder()
                .sourceAccountId(request.sourceAccountId())
                .targetAccountNumber(request.targetAccountNumber())
                .amount(request.amount());
        if (request.currency() != null) {
            command.currency(request.currency());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(service.transfer(CurrentCaller.from(authentication), command.build())));
    }

    private static TransferResponse toResponse(TransferSummary transfer) {
        return TransferResponse.builder()
                .sourceAccountId(transfer.sourceAccountId())
                .sourceAccountNumber(transfer.sourceAccountNumber())
                .targetAccountNumber(transfer.targetAccountNumber())
                .amount(transfer.amount())
                .currency(transfer.currency())
                .occurredOn(transfer.occurredOn())
                .status(transfer.status())
                .build();
    }
}
