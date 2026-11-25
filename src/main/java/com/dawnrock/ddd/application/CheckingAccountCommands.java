package com.dawnrock.ddd.application;

import java.util.UUID;

/**
 * Puerto de entrada de los casos de uso que abren cuentas: apertura del administrador, alta de clientes, otra cuenta
 * del cliente y regeneración del IBAN. Los adaptadores de
 * entrada (REST, web, demo) dependen de este puerto y no de la clase que lo implementa ({@code CheckingAccountService}).
 * Cada caso de uso recibe quién lo pide ({@link Caller}) y aplica la propiedad de las cuentas (ADR 0008).
 */
public interface CheckingAccountCommands {

    /**
     * Abre una cuenta con su depósito inicial para {@code command.ownerUsername()}, que será su propietario. Solo un
     * administrador.
     *
     * @throws ForbiddenOperationException si quien lo pide no es administrador
     * @throws IllegalArgumentException si el command no indica el usuario propietario
     * @throws com.dawnrock.ddd.domain.model.DuplicateAccountNumberException si el número de cuenta ya está en uso
     * @throws com.dawnrock.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.dawnrock.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    AccountSummary openAccount(Caller caller, OpenAccountCommand command);

    /**
     * Da de alta a un cliente nuevo de Mon-A Bank y le abre su primera cuenta, a nombre de {@code command.username()},
     * con un IBAN que asigna el banco. El segmento lo deciden sus ingresos verificados (#98). Lo pide un visitante sin
     * sesión a través de {@code adapter.in.CustomerRegistration}, que crea después su usuario. Todo o nada, en una
     * unidad de trabajo: si la apertura falla no queda ni el cliente ni la propiedad. Contrato:
     * {@code docs/contracts/customer-sign-up.md}.
     *
     * @throws com.dawnrock.ddd.domain.model.DuplicateCustomerException si ya hay un cliente con ese documento (R30)
     * @throws IllegalArgumentException si la letra del documento no es la suya (R28) o el usuario ya es de otro cliente
     *         (R30)
     * @throws com.dawnrock.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.dawnrock.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    AccountSummary signUp(SignUpCommand command);

    /**
     * Abre otra cuenta al cliente que lo pide, indicando solo el depósito: el titular sale de sus datos de cliente.
     *
     * @throws NotACustomerException si quien lo pide no es un cliente registrado (también un administrador)
     * @throws com.dawnrock.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.dawnrock.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    AccountSummary openAnotherAccount(Caller caller, OpenAnotherAccountCommand command);

    /**
     * Sustituye el IBAN de la cuenta por otro que genera el banco (R32). Solo un administrador.
     *
     * @return el resumen de la cuenta con el IBAN nuevo
     * @throws ForbiddenOperationException si quien lo pide no es administrador
     * @throws AccountNotFoundException si la cuenta no existe
     */
    AccountSummary regenerateAccountNumber(Caller caller, UUID id);
}
