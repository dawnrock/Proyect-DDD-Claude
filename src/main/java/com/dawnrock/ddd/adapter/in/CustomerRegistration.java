package com.dawnrock.ddd.adapter.in;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.SignUpCommand;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;

import lombok.RequiredArgsConstructor;

/**
 * Alta de un cliente con su usuario, compartida por los adaptadores web y REST. La aplicación no conoce Spring
 * Security, así que las credenciales se quedan en los adaptadores de entrada: comprueba que el usuario está libre (en
 * los usuarios y con {@code CheckingAccountQueries.isUsernameTaken}), pide el alta al puerto
 * {@code AccountOpeningCommands.signUp} y, si va bien, crea el usuario con rol {@code USER} y su contraseña cifrada. Si
 * el caso de uso falla, no se crea ningún usuario. Antes de nada reserva un hueco en {@link SignUpLimit} para la IP del
 * cliente (#131, ADR 0018) y lo devuelve si el alta falla: solo cuentan las altas que crean un cliente. Un alta que
 * falla porque el usuario o el documento ya existen cuenta, además, para el límite de sondeo (#139). Contrato:
 * {@code docs/contracts/customer-sign-up.md}.
 */
@Component
@RequiredArgsConstructor
public class CustomerRegistration {

    /** Contraseña mínima del alta. */
    public static final int MINIMUM_PASSWORD_LENGTH = 8;

    private final AccountOpeningCommands commands;
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
     * @throws DuplicateCustomerException si ya hay un cliente con ese documento
     * @throws com.dawnrock.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws IllegalArgumentException si la contraseña tiene menos de {@value #MINIMUM_PASSWORD_LENGTH} caracteres
     */
    public synchronized AccountSummary register(SignUpCommand command, String rawPassword, String clientIp) {
        signUpLimit.tryReserve(clientIp).ifPresent(retryAfter -> {
            throw new TooManySignUpsException(retryAfter);
        });
        AccountSummary account;
        try {
            account = openAccount(command, rawPassword);
        } catch (RuntimeException e) {
            // No se creó ningún cliente: la reserva se devuelve. Si el cliente ya existe y falla después la creación
            // del usuario, el alta sigue contando, porque ocupa memoria
            signUpLimit.release(clientIp);
            if (e instanceof UsernameTakenException || e instanceof DuplicateCustomerException) {
                // Revela que el usuario o el documento existen: cuenta para el límite de sondeo (#139)
                signUpLimit.recordFailure(clientIp);
            }
            throw e;
        }
        createUser(command, rawPassword);
        return account;
    }

    /**
     * Alta de un cliente con una identidad de Auth0 ya verificada y sin contraseña local (#198, ADR 0024): los mismos
     * pasos y reglas que {@link #register}, pero el usuario se crea con una contraseña inutilizable y queda vinculado a
     * la identidad ({@link ExternalIdentities}). Contrato: {@code docs/contracts/auth0-sign-up.md}.
     *
     * @param pending  la identidad de Auth0 pendiente de la sesión
     * @param clientIp IP del cliente, para el límite de altas
     * @return el resumen de la cuenta abierta, con el IBAN asignado
     * @throws PendingIdentityExpiredException si la identidad pendiente caducó (no se crea nada)
     * @throws IdentityAlreadyLinkedException si la identidad ya está vinculada a otro usuario (no se crea nada)
     * @throws TooManySignUpsException si la IP o el total de altas llegó al límite (no se abre nada)
     * @throws UsernameTakenException si el usuario ya existe (no se abre nada)
     * @throws DuplicateCustomerException si ya hay un cliente con ese documento
     * @throws com.dawnrock.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     */
    public AccountSummary registerWithExternalIdentity(SignUpCommand command, PendingIdentity pending,
                                                       String clientIp) {
        throw new UnsupportedOperationException("pendiente: backend-dev");
    }

    private AccountSummary openAccount(SignUpCommand command, String rawPassword) {
        if (users.userExists(command.username()) || queries.isUsernameTaken(command.username())) {
            throw new UsernameTakenException(command.username());
        }
        if (rawPassword == null || rawPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos %d caracteres".formatted(MINIMUM_PASSWORD_LENGTH));
        }
        return commands.signUp(command);
    }

    private void createUser(SignUpCommand command, String rawPassword) {
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
    }
}
