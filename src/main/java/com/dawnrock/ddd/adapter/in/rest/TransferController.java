package com.dawnrock.ddd.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.TransferCommand;
import com.dawnrock.ddd.application.TransferSummary;
import com.dawnrock.ddd.adapter.in.CurrentCaller;

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

    private final MoneyMovementCommands movements;

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            Authentication authentication, @Valid @RequestBody TransferRequest request) {
        // Sin divisa en la petición, el command usa EUR
        TransferCommand command = new TransferCommand(request.sourceAccountId(), request.targetAccountNumber(),
                request.amount(), request.currency());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(movements.transfer(CurrentCaller.from(authentication), command)));
    }

    private static TransferResponse toResponse(TransferSummary transfer) {
        return new TransferResponse(transfer.sourceAccountId(), transfer.sourceAccountNumber(),
                transfer.targetAccountNumber(), transfer.amount(), transfer.currency(), transfer.occurredOn(),
                transfer.status());
    }
}
