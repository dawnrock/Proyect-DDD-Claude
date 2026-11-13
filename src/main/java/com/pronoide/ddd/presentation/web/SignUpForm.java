package com.pronoide.ddd.presentation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.pronoide.ddd.application.OpenAccountCommandDef;
import com.pronoide.ddd.application.SignUpCommandDef;
import com.pronoide.ddd.presentation.CustomerRegistration;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Formulario del alta de cliente en tres pasos. Vive en la sesión del servidor ({@code @SessionAttributes}) mientras
 * dura el alta; cada paso valida solo su grupo. Misma validación que {@code SignUpRequest} (REST), según "Shared
 * presentation validation" de {@code docs/contracts/customer-sign-up.md}.
 *
 * <p>Los avances de paso no tienen setter: el navegador no puede marcarlos enviando un parámetro.
 */
@Getter
@Setter
@Accessors(fluent = false)
public class SignUpForm {

    /** Paso 1: datos personales. */
    public interface PersonalData {
    }

    /** Paso 2: usuario y contraseña. */
    public interface Access {
    }

    @NotBlank(groups = PersonalData.class, message = "El nombre y apellidos son obligatorios")
    private String fullName;

    @NotBlank(groups = PersonalData.class, message = "El documento es obligatorio")
    @Pattern(groups = PersonalData.class, regexp = OpenAccountCommandDef.TAX_ID_PATTERN,
            message = OpenAccountCommandDef.TAX_ID_MESSAGE)
    private String taxId;

    @NotBlank(groups = PersonalData.class, message = "El correo electrónico es obligatorio")
    @Pattern(groups = PersonalData.class, regexp = SignUpCommandDef.EMAIL_PATTERN,
            message = "El correo electrónico no es válido")
    private String email;

    /** Usuario con el que entrará; mismo formato que el command. */
    @NotBlank(groups = Access.class, message = "El usuario es obligatorio")
    @Pattern(groups = Access.class, regexp = OpenAccountCommandDef.USERNAME_PATTERN,
            message = "El usuario debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones")
    private String username;

    /** Solo vive en la sesión del servidor entre los pasos 2 y 3; ninguna plantilla la pinta. */
    @NotBlank(groups = Access.class, message = "La contraseña es obligatoria")
    @Size(groups = Access.class, min = CustomerRegistration.MINIMUM_PASSWORD_LENGTH,
            message = "La contraseña debe tener al menos " + CustomerRegistration.MINIMUM_PASSWORD_LENGTH + " caracteres")
    private String password;

    private String passwordRepetition;

    @Setter(lombok.AccessLevel.NONE)
    private boolean personalDataDone;

    @Setter(lombok.AccessLevel.NONE)
    private boolean accessDone;

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
}
