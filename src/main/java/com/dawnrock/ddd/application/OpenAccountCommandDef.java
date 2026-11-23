package com.dawnrock.ddd.application;

import java.math.BigDecimal;

import org.immutables.value.Value;

/**
 * Orden del administrador de abrir una cuenta corriente. Valida sus campos al construirse
 * ({@code IllegalArgumentException}), antes de llegar al dominio. Contratos: {@code docs/contracts/account-factory.md}
 * y {@code customer-sign-up.md}.
 *
 * <p>El IBAN lo genera la factoría (R31) y el segmento lo deciden los ingresos verificados del titular. El titular se
 * identifica por su documento ({@code holderTaxId}): si ya es cliente, la cuenta se abre a su nombre y para su
 * usuario, y {@code holderName}, {@code holderEmail} y {@code ownerUsername} van en blanco o coinciden con los suyos;
 * si no lo es, esos tres campos son obligatorios y se registra como cliente. {@code currency} es un código ISO 4217
 * ({@code EUR} por defecto).
 */
@ApplicationValue
@Value.Immutable
public interface OpenAccountCommandDef {

    /** Formato de un nombre de usuario: 3-30 minúsculas, dígitos, punto, guion o guion bajo. */
    String USERNAME_PATTERN = "[a-z0-9._-]{3,30}";

    /** {@link #USERNAME_PATTERN} en palabras, con todos los caracteres que admite (#152). */
    String USERNAME_FORMAT = "de 3 a 30 caracteres: minúsculas sin tildes ni ñ, dígitos, puntos, guiones bajos o guiones";

    /** Mensaje de un usuario mal formado en el alta. */
    String USERNAME_MESSAGE = "El usuario debe tener " + USERNAME_FORMAT;

    /** Mensaje de un usuario del titular mal formado en la apertura del administrador. */
    String OWNER_USERNAME_MESSAGE = "El usuario del titular debe tener " + USERNAME_FORMAT;

    /**
     * Formato de un DNI (8 dígitos y una letra) o un NIE (X, Y o Z, 7 dígitos y una letra), con espacios alrededor o
     * entre medias; lo normaliza {@code TaxId}, que además comprueba la letra de control.
     */
    String TAX_ID_PATTERN = "^ *[XYZxyz0-9]\\d{7} *[A-Za-z] *$";

    /** Mensaje de un documento mal formado; lo comparten los commands, la API REST y la web. */
    String TAX_ID_MESSAGE = "El documento debe ser un DNI o un NIE";

    /** Documento (DNI o NIE) del titular; lo normaliza {@code TaxId}. */
    String holderTaxId();

    BigDecimal initialDeposit();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    /** Nombre del titular; obligatorio solo si el documento aún no es de un cliente. */
    @Value.Default
    default String holderName() {
        return "";
    }

    /** Correo del titular; obligatorio solo si el documento aún no es de un cliente. */
    @Value.Default
    default String holderEmail() {
        return "";
    }

    /**
     * Usuario (cliente) propietario de la cuenta: solo él y los administradores podrán verla y operarla. Obligatorio
     * solo si el documento aún no es de un cliente; si lo es, la cuenta es para su usuario.
     */
    @Value.Default
    default String ownerUsername() {
        return "";
    }

    @Value.Check
    default void validate() {
        if (!holderTaxId().matches(TAX_ID_PATTERN)) {
            throw new IllegalArgumentException(TAX_ID_MESSAGE);
        }
        if (!holderEmail().isBlank() && !holderEmail().matches(SignUpCommandDef.EMAIL_PATTERN)) {
            throw new IllegalArgumentException("El correo electrónico no es válido");
        }
        if (!ownerUsername().isEmpty() && !ownerUsername().matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException(OWNER_USERNAME_MESSAGE);
        }
        if (!CommandValidation.isIsoCurrency(currency())) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(currency()));
        }
        CommandValidation.requireValidInitialDeposit(initialDeposit(), currency());
    }
}
