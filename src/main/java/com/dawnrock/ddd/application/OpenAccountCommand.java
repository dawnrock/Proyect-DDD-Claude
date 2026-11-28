package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Orden del administrador de abrir una cuenta corriente. Valida sus campos al construirse
 * ({@code IllegalArgumentException}), antes de llegar al dominio, en este orden: la primera regla que falla es la que
 * se informa. Contratos: {@code docs/contracts/account-factory.md} y {@code customer-sign-up.md}.
 *
 * <p>El IBAN lo genera la factoría (R31) y el segmento lo deciden los ingresos verificados del titular. El titular se
 * identifica por su documento ({@code holderTaxId}): si ya es cliente, la cuenta se abre a su nombre y para su
 * usuario, y {@code holderName}, {@code holderEmail} y {@code ownerUsername} van en blanco o coinciden con los suyos;
 * si no lo es, esos tres campos son obligatorios y se registra como cliente.
 *
 * @param holderName    nombre del titular; obligatorio solo si el documento aún no es de un cliente (vacío si es
 *                      {@code null})
 * @param holderTaxId   documento (DNI o NIE) del titular; lo normaliza {@code TaxId}
 * @param holderEmail   correo del titular; obligatorio solo si el documento aún no es de un cliente (vacío si es
 *                      {@code null})
 * @param ownerUsername usuario (cliente) propietario de la cuenta: solo él y los administradores podrán verla y
 *                      operarla. Obligatorio solo si el documento aún no es de un cliente; si lo es, la cuenta es para
 *                      su usuario (vacío si es {@code null})
 * @param currency      código ISO 4217 ({@code EUR} si es {@code null})
 */
public record OpenAccountCommand(String holderName, String holderTaxId, String holderEmail, BigDecimal initialDeposit,
                                 String ownerUsername, String currency) {

    /** Formato de un nombre de usuario: 3-30 minúsculas, dígitos, punto, guion o guion bajo. */
    public static final String USERNAME_PATTERN = "[a-z0-9._-]{3,30}";
    /** {@link #USERNAME_PATTERN} en palabras, con todos los caracteres que admite (#152). */
    public static final String USERNAME_FORMAT =
            "de 3 a 30 caracteres: minúsculas sin tildes ni ñ, dígitos, puntos, guiones bajos o guiones";
    /** Mensaje de un usuario mal formado en el alta. */
    public static final String USERNAME_MESSAGE = "El usuario debe tener " + USERNAME_FORMAT;
    /** Mensaje de un usuario del titular mal formado en la apertura del administrador. */
    public static final String OWNER_USERNAME_MESSAGE = "El usuario del titular debe tener " + USERNAME_FORMAT;
    /**
     * Formato de un DNI (8 dígitos y una letra) o un NIE (X, Y o Z, 7 dígitos y una letra), con espacios alrededor o
     * entre medias; lo normaliza {@code TaxId}, que además comprueba la letra de control.
     */
    public static final String TAX_ID_PATTERN = "^ *[XYZxyz0-9]\\d{7} *[A-Za-z] *$";
    /** Mensaje de un documento mal formado; lo comparten los commands, la API REST y la web. */
    public static final String TAX_ID_MESSAGE = "El documento debe ser un DNI o un NIE";

    public OpenAccountCommand {
        holderName = Objects.requireNonNullElse(holderName, "");
        Objects.requireNonNull(holderTaxId, "holderTaxId");
        holderEmail = Objects.requireNonNullElse(holderEmail, "");
        Objects.requireNonNull(initialDeposit, "initialDeposit");
        ownerUsername = Objects.requireNonNullElse(ownerUsername, "");
        currency = Objects.requireNonNullElse(currency, CommandValidation.DEFAULT_CURRENCY);
        CommandValidation.requireMatching(holderTaxId, TAX_ID_PATTERN, TAX_ID_MESSAGE);
        CommandValidation.requireMatchingUnlessBlank(holderEmail, SignUpCommand.EMAIL_PATTERN,
                SignUpCommand.EMAIL_MESSAGE);
        CommandValidation.requireMatchingIfPresent(ownerUsername, USERNAME_PATTERN, OWNER_USERNAME_MESSAGE);
        CommandValidation.requireIsoCurrency(currency);
        CommandValidation.requireValidInitialDeposit(initialDeposit, currency);
    }

    /** La apertura para quien ya es cliente: solo su documento y el depósito, en euros. */
    public static OpenAccountCommand forHolder(String holderTaxId, BigDecimal initialDeposit) {
        return new OpenAccountCommand(null, holderTaxId, null, initialDeposit, null, null);
    }

    /** La apertura con todos los datos del titular, en euros. */
    public static OpenAccountCommand of(String holderName, String holderTaxId, String holderEmail,
                                        BigDecimal initialDeposit, String ownerUsername) {
        return new OpenAccountCommand(holderName, holderTaxId, holderEmail, initialDeposit, ownerUsername, null);
    }
}
