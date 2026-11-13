package com.pronoide.ddd.presentation;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.SignUpCommand;

import lombok.RequiredArgsConstructor;

/**
 * Alta de un cliente con su usuario, compartida por la web y la API (D5). La aplicación no conoce Spring Security, así
 * que las credenciales se quedan aquí: comprueba que el usuario está libre, pide el alta al caso de uso y, si va bien,
 * crea el usuario con rol {@code USER} y su contraseña cifrada. Si el caso de uso falla, no se crea ningún usuario.
 * Contrato: {@code docs/contracts/customer-sign-up.md}.
 */
@Component
@RequiredArgsConstructor
public class CustomerRegistration {

    /** Contraseña mínima del alta. */
    public static final int MINIMUM_PASSWORD_LENGTH = 8;

    private final CheckingAccountService service;
    private final InMemoryUserDetailsManager users;
    private final PasswordEncoder passwordEncoder;

    /**
     * Da de alta al cliente, le abre su primera cuenta y crea su usuario. Los cuatro pasos van bajo un mismo cierre,
     * así que dos altas con el mismo usuario no pueden pasar a la vez la comprobación del primero.
     *
     * @return el resumen de la cuenta abierta, con el IBAN asignado
     * @throws UsernameTakenException si el usuario ya existe (no se abre nada)
     * @throws com.pronoide.ddd.domain.model.DuplicateCustomerException si ya hay un cliente con ese documento
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws IllegalArgumentException si la contraseña tiene menos de {@value #MINIMUM_PASSWORD_LENGTH} caracteres
     */
    public synchronized AccountSummary register(SignUpCommand command, String rawPassword) {
        if (users.userExists(command.username())) {
            throw new UsernameTakenException(command.username());
        }
        if (rawPassword == null || rawPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos %d caracteres".formatted(MINIMUM_PASSWORD_LENGTH));
        }
        AccountSummary account = service.signUp(command);
        try {
            users.createUser(User.withUsername(command.username())
                    .password(passwordEncoder.encode(rawPassword))
                    .roles("USER")
                    .build());
        } catch (IllegalArgumentException e) {
            if (users.userExists(command.username())) {
                throw new UsernameTakenException(command.username());
            }
            throw e;
        }
        return account;
    }
}
