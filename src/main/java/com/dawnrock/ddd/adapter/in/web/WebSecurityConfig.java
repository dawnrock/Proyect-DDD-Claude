package com.dawnrock.ddd.adapter.in.web;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import com.dawnrock.ddd.adapter.in.Auth0Properties;
import com.dawnrock.ddd.adapter.in.AuthProperties;
import com.dawnrock.ddd.adapter.in.ClientIpAuthenticationDetailsSource;
import com.dawnrock.ddd.adapter.in.ContentSecurityPolicies;
import com.dawnrock.ddd.adapter.in.LinkedOidcUserService;
import com.dawnrock.ddd.adapter.in.TooManyLoginAttemptsException;

/**
 * Seguridad de la web (ADR 0008): la cadena de reserva, para todo lo que no es {@code /api/**} (va después de la de
 * la API). Inicio de sesión con formulario propio (si {@code app.auth.local}, que por defecto sí), con Auth0 (si hay
 * registro de cliente, ADR 0024) y sesión, CSRF activo, {@code /accounts/new} solo para
 * administradores y la página 403 propia. A htmx, una sesión caducada le responde 401 con {@code HX-Redirect}
 * al login ({@link HtmxLoginRedirect}, #125).
 *
 * <p>Públicos: {@code /login}, los estilos, los scripts y las librerías del frontend (ADR 0016), los logos de la
 * marca ({@code /brand/**}, ADR 0022), la salud del actuator y las páginas de error. El resto exige sesión.
 */
@Configuration
public class WebSecurityConfig {

    private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    private static final String PERMISSIONS_POLICY_HEADER = "Permissions-Policy";
    private static final String H2_CONSOLE = "/h2-console";
    private static final String API_DOCS = "/api-docs";

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurity(HttpSecurity http, ClientIpAuthenticationDetailsSource clientIp,
                                           ObjectProvider<ClientRegistrationRepository> registrations,
                                           ObjectProvider<LinkedOidcUserService> oidcUsers, AuthProperties auth,
                                           Auth0Properties auth0Properties)
            throws Exception {
        ClientRegistrationRepository auth0 = registrations.getIfAvailable();
        if (auth0 != null) {
            oauth2Login(http, auth0, oidcUsers.getObject());
        }
        if (auth.local()) {
            // La contraseña local (#196): con app.auth.local=false solo se entra con Auth0
            http.formLogin(login -> login
                    .loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .failureHandler(loginFailure())
                    // La IP del cliente detrás de Render y Cloudflare, para el límite de intentos (#133)
                    .authenticationDetailsSource(clientIp)
                    .permitAll());
        }
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/login", "/login/link", "/signup/**", "/css/**", "/js/**", "/webjars/**", "/vendor/**", "/brand/**", "/actuator/health", "/error", "/error/**",
                                "/openapi.yaml", "/api-docs", "/api-docs/**").permitAll()
                        // /accounts/another: cualquier sesión; el administrador recibe 403 del propio controlador
                        .requestMatchers("/accounts/new").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/accounts/*/account-number").hasRole("ADMIN")
                        // Consola H2: solo administradores; usa marcos del mismo origen y sus propios formularios sin CSRF
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter(PERMISSIONS_POLICY_HEADER,
                                ContentSecurityPolicies.PERMISSIONS_POLICY))
                        .addHeaderWriter(contentSecurityPolicy(auth0Origin(auth0Properties))))
                .logout(logout -> logout.logoutSuccessHandler(logoutSuccess(auth0)))
                .exceptionHandling(errors -> {
                    // htmx (#125): una sesión caducada lleva al login con HX-Redirect en vez de pintarlo en el panel
                    AccessDeniedHandlerImpl errorPage = new AccessDeniedHandlerImpl();
                    errorPage.setErrorPage("/error/403");
                    HtmxLoginRedirect htmx = new HtmxLoginRedirect("/login",
                            new LoginUrlAuthenticationEntryPoint("/login"), errorPage);
                    errors.authenticationEntryPoint(htmx).accessDeniedHandler(htmx);
                })
                .build();
    }

    /**
     * La Content-Security-Policy de cada respuesta de la cadena web (#206, ADR 0025), según la ruta: Swagger UI
     * ({@code /api-docs/**}) tiene la suya; la consola H2, ninguna (sus scripts van en línea y solo existe en local, para
     * administradores); el resto, la de la web con el nonce de la petición. {@link CspNonce} guarda el nonce en la
     * petición, así que es el mismo que pintó la plantilla: las cabeceras se escriben al enviar la respuesta.
     */
    static HeaderWriter contentSecurityPolicy(Optional<String> auth0Origin) {
        return (request, response) -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith(H2_CONSOLE)) {
                return;
            }
            response.setHeader(CONTENT_SECURITY_POLICY_HEADER, path.startsWith(API_DOCS)
                    ? ContentSecurityPolicies.apiDocs(auth0Origin)
                    : ContentSecurityPolicies.web(CspNonce.of(request), auth0Origin));
        };
    }

    /** El origen del tenant de Auth0, si está configurado: logout, alta y PKCE de Swagger UI acaban en él. */
    static Optional<String> auth0Origin(Auth0Properties auth0) {
        return auth0.domain().isEmpty() ? Optional.empty() : Optional.of(auth0.baseUrl());
    }

    /**
     * El inicio de sesión con Auth0 (#196, ADR 0024), solo si hay un registro de cliente. PKCE lo declara el propio
     * registro ({@code requireProofKey}, en {@code Auth0ClientRegistration}), y Spring lo aplica. El usuario lo da
     * {@link LinkedOidcUserService} (el usuario local vinculado al {@code sub}); si no hay vínculo, el manejador de
     * fallos lleva a {@code /login/link}.
     */
    private static void oauth2Login(HttpSecurity http, ClientRegistrationRepository registrations,
                                    LinkedOidcUserService oidcUsers) throws Exception {
        http.oauth2Login(login -> login
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUsers))
                .failureHandler(new OAuth2LoginFailureHandler()));
    }

    /**
     * Tras cerrar la sesión: la de Auth0 ({@link OAuth2AuthenticationToken}) también cierra la sesión en Auth0, que
     * vuelve a {@code {baseUrl}/login?logout}; cualquier otra va directa a {@code /login?logout}.
     */
    private static LogoutSuccessHandler logoutSuccess(ClientRegistrationRepository registrations) {
        SimpleUrlLogoutSuccessHandler local = new SimpleUrlLogoutSuccessHandler();
        local.setDefaultTargetUrl("/login?logout");
        if (registrations == null) {
            return local;
        }
        OidcClientInitiatedLogoutSuccessHandler auth0 = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        auth0.setPostLogoutRedirectUri("{baseUrl}/login?logout");
        return (request, response, authentication) -> {
            if (authentication instanceof OAuth2AuthenticationToken) {
                auth0.onLogoutSuccess(request, response, authentication);
            } else {
                local.onLogoutSuccess(request, response, authentication);
            }
        };
    }

    /** Un bloqueo por demasiados intentos (#130) va a {@code /login?blocked}; el resto de fallos, a {@code /login?error}. */
    private static AuthenticationFailureHandler loginFailure() {
        ExceptionMappingAuthenticationFailureHandler handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setDefaultFailureUrl("/login?error");
        handler.setExceptionMappings(Map.of(TooManyLoginAttemptsException.class.getName(), "/login?blocked"));
        return handler;
    }
}
