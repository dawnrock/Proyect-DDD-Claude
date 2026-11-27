package com.dawnrock.ddd.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

import com.dawnrock.ddd.adapter.in.Usernames;

import lombok.Getter;
import lombok.Setter;

/**
 * Formulario de {@code /login/link} (#196): el usuario y la contraseña locales con los que se vincula la identidad de
 * Auth0. La contraseña nunca se pinta de vuelta.
 */
@Getter
@Setter
public class IdentityLinkForm {

    @NotBlank(message = "El usuario es obligatorio")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    /** El usuario se guarda en minúsculas y sin espacios alrededor, como en el alta (#152). */
    public void setUsername(String username) {
        this.username = Usernames.normalize(username);
    }
}
