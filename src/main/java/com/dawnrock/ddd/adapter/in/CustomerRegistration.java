package com.dawnrock.ddd.adapter.in;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;

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

    private static final int UNUSABLE_PASSWORD_BYTES = 32;

    private final AccountOpeningCommands commands;
    private final CheckingAccountQueries queries;
    private final UserDetailsManager users;
    private final PasswordEncoder passwordEncoder;
    private final SignUpLimit signUpLimit;
    private final ExternalIdentities identities;
    private final Clock clock;
    private final AuthProperties auth;
    private final SecureRandom random = new SecureRandom();

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
     * @throws IllegalStateException sin autenticación local ({@code app.auth.local=false}, #199): en el modo realista
     *                               solo hay alta por Auth0. Los adaptadores ya no lo ofrecen; esto es una segunda barrera
     */
    public synchronized AccountSummary register(SignUpCommand command, String rawPassword, String clientIp) {
        if (!auth.local()) {
            throw new IllegalStateException(
                    "El alta con contraseña no está disponible sin autenticación local: el alta es con Auth0");
        }
        AccountSummary account = reserveAndOpen(command, clientIp, () -> checkPassword(rawPassword));
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
    public synchronized AccountSummary registerWithExternalIdentity(SignUpCommand command, PendingIdentity pending,
                                                                    String clientIp) {
        Objects.requireNonNull(pending, "Falta la identidad de Auth0 pendiente");
        // Estas dos comprobaciones no reservan nada ni cuentan para los límites: no revelan ningún documento ni usuario
        if (pending.isExpired(clock)) {
            throw new PendingIdentityExpiredException(pending.provider());
        }
        identities.usernameOf(pending.provider(), pending.subject()).ifPresent(linked -> {
            throw new IdentityAlreadyLinkedException(pending.provider(), linked);
        });
        AccountSummary account = reserveAndOpen(command, clientIp, () -> { });
        createUser(command, unusablePassword());
        // Si otra pestaña vinculó la identidad entre la comprobación y aquí, la excepción llega al llamador: el cliente
        // y el usuario ya existen y no se deshace nada (#150), igual que si falla la creación del usuario
        identities.link(pending.provider(), pending.subject(), command.username());
        return account;
    }

    /**
     * Reserva un hueco del límite de altas, comprueba el usuario y la contraseña (si la hay) y pide el alta. Si falla,
     * devuelve el hueco y, si revela que el usuario o el documento existen, cuenta para el sondeo.
     */
    private AccountSummary reserveAndOpen(SignUpCommand command, String clientIp, Runnable passwordCheck) {
        signUpLimit.tryReserve(clientIp).ifPresent(retryAfter -> {
            throw new TooManySignUpsException(retryAfter);
        });
        try {
            if (users.userExists(command.username()) || queries.isUsernameTaken(command.username())) {
                throw new UsernameTakenException(command.username());
            }
            passwordCheck.run();
            return commands.signUp(command);
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
    }

    private static void checkPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos %d caracteres".formatted(MINIMUM_PASSWORD_LENGTH));
        }
    }

    /** 32 bytes aleatorios que nadie conserva: el usuario creado con ellos no puede entrar con contraseña. */
    private String unusablePassword() {
        byte[] secret = new byte[UNUSABLE_PASSWORD_BYTES];
        random.nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
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
