package com.pronoide.ddd.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de las cuentas corrientes: abrir, consultar y listar. Carga y guarda el agregado; las
 * reglas de negocio siguen en {@code CheckingAccount}. Contrato: {@code docs/contracts/open-query-list-accounts.md}.
 */
@Service
@RequiredArgsConstructor
public class CheckingAccountService {

    private final InMemoryCheckingAccountRepository repository;

    /**
     * Abre una cuenta con su depósito inicial y la guarda.
     *
     * @throws com.pronoide.ddd.domain.model.DuplicateAccountNumberException si el número de cuenta ya está en uso
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    public AccountSummary openAccount(OpenAccountCommand command) {
        throw new UnsupportedOperationException("pendiente: backend-dev");
    }

    /**
     * Detalle de una cuenta, con sus movimientos.
     *
     * @throws AccountNotFoundException si no existe ninguna cuenta con ese id
     */
    public AccountDetails findAccount(UUID id) {
        throw new UnsupportedOperationException("pendiente: backend-dev");
    }

    /** Todas las cuentas, ordenadas por número de cuenta. */
    public List<AccountSummary> listAccounts() {
        throw new UnsupportedOperationException("pendiente: backend-dev");
    }
}
