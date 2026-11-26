package com.dawnrock.ddd.adapter.in;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import com.dawnrock.ddd.application.OpenAccountCommandDef;

/**
 * Lo que comparten las dos cadenas de seguridad (web y API, ADR 0008): el cifrado de contraseñas, los usuarios y el
 * cortafuegos HTTP.
 *
 * <p>Los usuarios los guarda {@link UserStore} en la base de datos (#150). Los configurados salen de
 * {@link SecurityUsersProperties} y, si están definidas, de las variables de entorno {@code APP_ADMIN_USERNAME} y
 * {@code APP_ADMIN_PASSWORD} (un administrador). Sin perfil {@code demo} ni variables no
 * hay usuarios: la aplicación arranca, pero nadie puede entrar. Un nombre de usuario que no cumple
 * {@link OpenAccountCommandDef#USERNAME_PATTERN} se rechaza al arrancar, porque nunca podría ser propietario de una
 * cuenta.
 */
@Configuration
@EnableConfigurationProperties({SecurityUsersProperties.class, AuthProperties.class, LoginAttemptsProperties.class,
        TrustedProxiesProperties.class, SignUpLimitProperties.class})
public class SecuritySupport {

    private static final Set<String> ROLES = Set.of("ADMIN", "USER");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * El cortafuegos estricto de Spring Security, con sus comprobaciones por defecto, que además deja pasar el método
     * {@code QUERY} (estandarizado en 2026). Sin él, {@code QUERY} se rechazaba con un 400 antes de llegar a Spring MVC;
     * así responde 405 con la cabecera {@code Allow}, como cualquier otro método no declarado (#108).
     */
    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(List.of("DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT", "QUERY"));
        return firewall;
    }

    /** IP del cliente detrás de los proxies de confianza (#133, ADR 0017): Render y Cloudflare. */
    @Bean
    public ClientIpResolver clientIpResolver(TrustedProxiesProperties properties) {
        return new ClientIpResolver(properties.trustedProxies());
    }

    /** Detalles de autenticación con la IP del cliente, para el límite de intentos de inicio de sesión (#133). */
    @Bean
    public ClientIpAuthenticationDetailsSource clientIpDetailsSource(ClientIpResolver resolver) {
        return new ClientIpAuthenticationDetailsSource(resolver);
    }

    /** Fallos recientes de inicio de sesión (#130, ADR 0017). Sin el reloj del núcleo, como en los tests web, el del sistema. */
    @Bean
    public LoginAttempts loginAttempts(LoginAttemptsProperties properties, ObjectProvider<Clock> clock) {
        return new LoginAttempts(properties, clock.getIfAvailable(Clock::systemUTC));
    }

    /** Altas de clientes recientes por IP y en total (#131, ADR 0018). Sin el reloj del núcleo, el del sistema. */
    @Bean
    public SignUpLimit signUpLimit(SignUpLimitProperties properties, ObjectProvider<Clock> clock) {
        return new SignUpLimit(properties, clock.getIfAvailable(Clock::systemUTC));
    }

    /**
     * Autenticación con usuario y contraseña limitada en intentos (#130, ADR 0017). Spring Security la usa en el inicio
     * de sesión de la web, y la emisión de tokens de la API la reutiliza.
     */
    @Bean
    public ThrottledAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder,
                                                                  LoginAttempts attempts) {
        DaoAuthenticationProvider credentials = new DaoAuthenticationProvider(users);
        credentials.setPasswordEncoder(encoder);
        return new ThrottledAuthenticationProvider(credentials, attempts);
    }

    /**
     * Los usuarios configurados: los de {@link SecurityUsersProperties} y, si están definidas las dos variables, el
     * administrador de {@code APP_ADMIN_USERNAME} y {@code APP_ADMIN_PASSWORD}. Los guarda {@link UserStore}.
     *
     * @throws IllegalStateException si un usuario no cumple el formato o su rol no es ADMIN ni USER
     */
    public static List<UserDetails> configuredUsers(SecurityUsersProperties properties, PasswordEncoder encoder,
                                                    String adminUsername, String adminPassword) {
        List<UserDetails> users = new ArrayList<>();
        for (SecurityUsersProperties.User user : properties.users()) {
            users.add(user(user.username(), user.passwordHash(), user.role()));
        }
        if (!adminUsername.isBlank() && !adminPassword.isBlank()) {
            users.add(user(adminUsername, encoder.encode(adminPassword), "ADMIN"));
        }
        return users;
    }

    private static UserDetails user(String username, String passwordHash, String role) {
        if (username == null || !username.matches(OpenAccountCommandDef.USERNAME_PATTERN)) {
            throw new IllegalStateException("El usuario '%s' no cumple el formato %s"
                    .formatted(username, OpenAccountCommandDef.USERNAME_PATTERN));
        }
        if (!ROLES.contains(role)) {
            throw new IllegalStateException("El rol del usuario %s debe ser ADMIN o USER: %s".formatted(username, role));
        }
        return User.withUsername(username).password(passwordHash).roles(role).build();
    }
}
