package com.pronoide.ddd.presentation;

import org.springframework.stereotype.Component;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.SignUpCommand;

/**
 * Alta de un cliente con su usuario, compartida por la web y la API (D5). La aplicación no conoce Spring Security, así
 * que las credenciales se quedan aquí: comprueba que el usuario está libre, pide el alta al caso de uso y, si va bien,
 * crea el usuario con rol {@code USER} y su contraseña cifrada. Si el caso de uso falla, no se crea ningún usuario.
 * Contrato: {@code docs/contracts/customer-sign-up.md}.
 */
@Component
public class CustomerRegistration {

    /** Contraseña mínima del alta. */
    public static final int MINIMUM_PASSWORD_LENGTH = 8;

    /**
     * Da de alta al cliente, le abre su primera cuenta y crea su usuario.
     *
     * @return el resumen de la cuenta abierta, con el IBAN asignado
     * @throws UsernameTakenException si el usuario ya existe (no se abre nada)
     * @throws com.pronoide.ddd.domain.model.DuplicateCustomerException si ya hay un cliente con ese documento
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws IllegalArgumentException si la contraseña tiene menos de {@value #MINIMUM_PASSWORD_LENGTH} caracteres
     */
    public AccountSummary register(SignUpCommand command, String rawPassword) {
        throw new UnsupportedOperationException("pendiente: backend-dev");
    }
}
