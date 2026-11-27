package com.dawnrock.ddd.adapter.in;

import java.util.List;

import org.springframework.context.annotation.Configuration;

/**
 * El modo realista ({@code app.auth.local=false}, #199, ADR 0024) solo arranca con la configuración completa de Auth0:
 * sin autenticación local, Auth0 es la única forma de entrar en la web (dominio, cliente y secreto) y en la API
 * (audiencia), y Swagger UI necesita su cliente público. Sin alguno de los cinco valores, la aplicación no arranca y el
 * error nombra las variables que faltan, en lugar de arrancar sin que nadie pueda entrar. Con la autenticación local
 * (por defecto) no comprueba nada: valen las reglas de todo o nada de {@link Auth0Properties}.
 */
@Configuration(proxyBeanMethods = false)
public final class RealisticModeCheck {

    public RealisticModeCheck(AuthProperties auth, Auth0Properties auth0) {
        List<String> missing = auth0.missingForRealisticMode();
        if (!auth.local() && !missing.isEmpty()) {
            throw new IllegalStateException("Sin autenticación local (app.auth.local=false) hace falta Auth0: faltan "
                    + String.join(", ", missing));
        }
    }
}
