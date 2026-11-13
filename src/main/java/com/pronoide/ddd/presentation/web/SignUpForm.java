package com.pronoide.ddd.presentation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.pronoide.ddd.application.OpenAccountCommandDef;
import com.pronoide.ddd.application.SignUpCommandDef;

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

    @Setter(lombok.AccessLevel.NONE)
    private boolean personalDataDone;

    public void completePersonalData() {
        personalDataDone = true;
    }

    /** Un paso con errores deja de dar por hecho lo que viene detrás. */
    public void resetProgress() {
        personalDataDone = false;
    }
}
