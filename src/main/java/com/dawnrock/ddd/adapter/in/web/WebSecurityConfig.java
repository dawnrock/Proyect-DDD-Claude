package com.dawnrock.ddd.adapter.in.web;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import com.dawnrock.ddd.adapter.in.ClientIpAuthenticationDetailsSource;
import com.dawnrock.ddd.adapter.in.LinkedOidcUserService;
import com.dawnrock.ddd.adapter.in.TooManyLoginAttemptsException;

/**
 * Seguridad de la web (ADR 0008): la cadena de reserva, para todo lo que no es {@code /api/**} (va después de la de
 * la API). Inicio de sesión con formulario propio y sesión, CSRF activo, {@code /accounts/new} solo para
 * administradores y la página 403 propia. A htmx, una sesión caducada le responde 401 con {@code HX-Redirect}
 * al login ({@link HtmxLoginRedirect}, #125).
 *
 * <p>Públicos: {@code /login}, los estilos, los scripts y las librerías del frontend (ADR 0016), los logos de la
 * marca ({@code /brand/**}, ADR 0022), la salud del actuator y las páginas de error. El resto exige sesión.
 */
@Configuration
public class WebSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurity(HttpSecurity http, ClientIpAuthenticationDetailsSource clientIp,
                                           ObjectProvider<ClientRegistrationRepository> registrations,
                                           ObjectProvider<LinkedOidcUserService> oidcUsers) throws Exception {
        ClientRegistrationRepository auth0 = registrations.getIfAvailable();
        if (auth0 != null) {
            oauth2Login(http, auth0, oidcUsers.getObject());
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
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .formLogin(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler(loginFailure())
                        // La IP del cliente detrás de Render y Cloudflare, para el límite de intentos (#133)
                        .authenticationDetailsSource(clientIp)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
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
     * El inicio de sesión con Auth0 (#196, ADR 0024), solo si hay un registro de cliente. Spring Security no aplica PKCE
     * por defecto a un cliente confidencial ({@code client_secret_basic}): se activa en el resolvedor. El usuario lo da
     * {@link LinkedOidcUserService} (el usuario local vinculado al {@code sub}); si no hay vínculo, el manejador de
     * fallos lleva a {@code /login/link}.
     */
    private static void oauth2Login(HttpSecurity http, ClientRegistrationRepository registrations,
                                    LinkedOidcUserService oidcUsers) throws Exception {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        http.oauth2Login(login -> login
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUsers))
                .failureHandler(new OAuth2LoginFailureHandler()));
    }

    /** Un bloqueo por demasiados intentos (#130) va a {@code /login?blocked}; el resto de fallos, a {@code /login?error}. */
    private static AuthenticationFailureHandler loginFailure() {
        ExceptionMappingAuthenticationFailureHandler handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setDefaultFailureUrl("/login?error");
        handler.setExceptionMappings(Map.of(TooManyLoginAttemptsException.class.getName(), "/login?blocked"));
        return handler;
    }
}
