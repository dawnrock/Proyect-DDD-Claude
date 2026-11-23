package com.pronoide.ddd.adapter.in;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CheckingAccountCommands;
import com.pronoide.ddd.application.CheckingAccountQueries;
import com.pronoide.ddd.application.SignUpCommand;

import lombok.RequiredArgsConstructor;

/**
 * Alta de un cliente con su usuario, compartida por los adaptadores web y REST. La aplicación no conoce Spring
 * Security, así que las credenciales se quedan en los adaptadores de entrada: comprueba que el usuario está libre (en
 * los usuarios y con {@code CheckingAccountQueries.isUsernameTaken}), pide el alta al puerto
 * {@code CheckingAccountCommands.signUp} y, si va bien, crea el usuario con rol {@code USER} y su contraseña cifrada. Si
 * el caso de uso falla, no se crea ningún usuario. Antes de nada reserva un hueco en {@link SignUpLimit} para la IP del
 * cliente (#131, ADR 0018) y lo devuelve si el alta falla: solo cuentan las altas que crean un cliente. Contrato:
 * {@code docs/contracts/customer-sign-up.md}.
 */
@Component
@RequiredArgsConstructor
public class CustomerRegistration {

    /** Contraseña mínima del alta. */
    public static final int MINIMUM_PASSWORD_LENGTH = 8;

    private final CheckingAccountCommands commands;
    private final CheckingAccountQueries queries;
    private final UserDetailsManager users;
    private final PasswordEncoder passwordEncoder;
    private final SignUpLimit signUpLimit;

    /**
     * Da de alta al cliente, le abre su primera cuenta y crea su usuario. Los pasos van bajo un mismo cierre, así que
     * dos altas con el mismo usuario no pueden pasar a la vez la comprobación del primero.
     *
     * @param clientIp IP del cliente, para el límite de altas (la da {@link ClientIpResolver})
     * @return el resumen de la cuenta abierta, con el IBAN asignado
     * @throws TooManySignUpsException si la IP o el total de altas llegó al límite (no se abre nada)
     * @throws UsernameTakenException si el usuario ya existe (no se abre nada)
     * @throws com.pronoide.ddd.domain.model.DuplicateCustomerException si ya hay un cliente con ese documento
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws IllegalArgumentException si la contraseña tiene menos de {@value #MINIMUM_PASSWORD_LENGTH} caracteres
     */
    public synchronized AccountSummary register(SignUpCommand command, String rawPassword, String clientIp) {
        signUpLimit.tryReserve(clientIp).ifPresent(retryAfter -> {
            throw new TooManySignUpsException(retryAfter);
        });
        try {
            return signUp(command, rawPassword);
        } catch (RuntimeException e) {
            signUpLimit.release(clientIp);
            throw e;
        }
    }

    private AccountSummary signUp(SignUpCommand command, String rawPassword) {
        if (users.userExists(command.username()) || queries.isUsernameTaken(command.username())) {
            throw new UsernameTakenException(command.username());
        }
        if (rawPassword == null || rawPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos %d caracteres".formatted(MINIMUM_PASSWORD_LENGTH));
        }
        AccountSummary account = commands.signUp(command);
        try {
            users.createUser(User.withUsername(command.username())
                    .password(passwordEncoder.encode(rawPassword))
                    .roles("USER")
                    .build());
        } catch (IllegalArgumentException e) {
            if (users.userExists(command.username()) || queries.isUsernameTaken(command.username())) {
                throw new UsernameTakenException(command.username());
            }
            throw e;
        }
        return account;
    }
}
