package com.dawnrock.ddd.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.application.SignUpCommand;
import com.dawnrock.ddd.adapter.in.CustomerRegistration;
import com.dawnrock.ddd.adapter.in.Usernames;

import lombok.Getter;
import lombok.Setter;

/**
 * Formulario del alta de cliente en tres pasos. Vive en la sesión del servidor ({@code @SessionAttributes}) mientras
 * dura el alta; cada paso valida solo su grupo. Misma validación que {@code SignUpRequest} (REST), según "Shared
 * input-adapter validation" de {@code docs/contracts/customer-sign-up.md}.
 *
 * <p>Los avances de paso no tienen setter: el navegador no puede marcarlos enviando un parámetro.
 */
@Getter
@Setter
public class SignUpForm {

    /** Paso 1: datos personales. */
    public interface PersonalData {
    }

    /** Paso 2 en modo clásico: el usuario (la contraseña va aparte, en {@link LocalPassword}). */
    public interface Access {
    }

    /** Paso 2 en modo Auth0 (#198): solo el usuario; el cliente entrará con su cuenta de Auth0, sin contraseña local. */
    public interface ExternalAccess {
    }

    /** Contraseña local del paso 2: el controlador la valida a mano, solo en modo clásico. */
    public interface LocalPassword {
    }

    /** Paso 3: depósito inicial y aceptación de las condiciones. */
    public interface AccountData {
    }

    @NotBlank(groups = PersonalData.class, message = "El nombre y apellidos son obligatorios")
    private String fullName;

    @NotBlank(groups = PersonalData.class, message = "El documento es obligatorio")
    @Pattern(groups = PersonalData.class, regexp = OpenAccountCommand.TAX_ID_PATTERN,
            message = OpenAccountCommand.TAX_ID_MESSAGE)
    private String taxId;

    @NotBlank(groups = PersonalData.class, message = "El correo electrónico es obligatorio")
    @Pattern(groups = PersonalData.class, regexp = SignUpCommand.EMAIL_PATTERN,
            message = "El correo electrónico no es válido")
    private String email;

    /** Usuario con el que entrará; mismo formato que el command. */
    @NotBlank(groups = {Access.class, ExternalAccess.class}, message = "El usuario es obligatorio")
    @Pattern(groups = {Access.class, ExternalAccess.class}, regexp = OpenAccountCommand.USERNAME_PATTERN,
            message = OpenAccountCommand.USERNAME_MESSAGE)
    private String username;

    /** Solo vive en la sesión del servidor entre los pasos 2 y 3; ninguna plantilla la pinta. */
    @NotBlank(groups = LocalPassword.class, message = "La contraseña es obligatoria")
    @Size(groups = LocalPassword.class, min = CustomerRegistration.MINIMUM_PASSWORD_LENGTH,
            message = "La contraseña debe tener al menos " + CustomerRegistration.MINIMUM_PASSWORD_LENGTH + " caracteres")
    private String password;

    private String passwordRepetition;

    @NotNull(groups = AccountData.class, message = "El depósito inicial es obligatorio")
    @Positive(groups = AccountData.class, message = "El depósito inicial debe ser positivo")
    private BigDecimal initialDeposit;

    @Pattern(groups = AccountData.class, regexp = "[A-Z]{3}",
            message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";

    @AssertTrue(groups = AccountData.class, message = "Debes aceptar las condiciones")
    private boolean conditionsAccepted;

    /** Alta con una identidad de Auth0, sin contraseña local (#198). Sin setter: solo {@link #startExternalIdentity}. */
    @Setter(lombok.AccessLevel.NONE)
    private boolean externalIdentity;

    /** El {@code sub} de Auth0 con el que empezó el alta en modo Auth0: solo esa identidad puede continuarla. */
    @Setter(lombok.AccessLevel.NONE)
    private String externalSubject;

    /** Cuándo empezó el alta en modo Auth0: no puede durar más que {@code SignUpController.AUTH0_WIZARD_LIMIT}. */
    @Setter(lombok.AccessLevel.NONE)
    private Instant externalStartedAt;

    @Setter(lombok.AccessLevel.NONE)
    private boolean personalDataDone;

    @Setter(lombok.AccessLevel.NONE)
    private boolean accessDone;

    /**
     * Pone el formulario en modo Auth0 para la identidad {@code subject}, con el correo que da Auth0 (editable y solo
     * informativo; vacío si no hay). El navegador no puede activar el modo ni cambiar la identidad: no hay setters.
     */
    public void startExternalIdentity(String subject, String email, Instant startedAt) {
        externalIdentity = true;
        externalSubject = subject;
        externalStartedAt = startedAt;
        this.email = email == null ? "" : email;
    }

    public void completePersonalData() {
        personalDataDone = true;
    }

    public void completeAccess() {
        accessDone = true;
    }

    /** Borra la contraseña de la sesión (tras un error o al terminar). */
    public void clearPassword() {
        password = null;
        passwordRepetition = null;
    }

    /** El paso 2 con errores deja de dar por hecho el acceso (y no conserva la contraseña). */
    public void resetAccess() {
        accessDone = false;
        clearPassword();
    }

    /** Un paso con errores deja de dar por hecho lo que viene detrás. */
    public void resetProgress() {
        personalDataDone = false;
        accessDone = false;
    }

    /**
     * El usuario se guarda en minúsculas y sin espacios alrededor (#152): «Margaret» se da de alta como «margaret». Solo
     * en la web; la API exige el formato exacto. Lombok no genera este setter porque ya existe.
     */
    public void setUsername(String username) {
        this.username = Usernames.normalize(username);
    }
}
