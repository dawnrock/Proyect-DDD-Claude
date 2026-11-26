package com.dawnrock.ddd.adapter.in;

import java.io.Serial;
import java.time.Clock;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Traduce la identidad de Auth0 al usuario local (ADR 0024): si el {@code sub} está vinculado, devuelve un
 * {@link OidcUser} que conserva el token de identidad y sus claims, pero cuyo {@code getName()} es el usuario local y
 * cuyas autoridades son sus {@code ROLE_*} de la base de datos. Si no está vinculado, lanza
 * {@link UnlinkedIdentityException}. Es un bean solo cuando Auth0 está configurado. Contrato:
 * {@code docs/contracts/auth0-web-login.md}.
 */
public class LinkedOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    /** Código del error OAuth2 cuando el usuario local vinculado no existe o está deshabilitado. */
    public static final String INVALID_USER = "invalid_user";

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final ExternalIdentities identities;
    private final UserDetailsService users;
    private final ObjectProvider<Clock> clock;

    /**
     * @param delegate el servicio de Spring que valida el usuario OIDC (en producción, {@code OidcUserService})
     * @param clock    el reloj del núcleo; sin él, el del sistema
     */
    public LinkedOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate, ExternalIdentities identities,
                                 UserDetailsService users, ObjectProvider<Clock> clock) {
        this.delegate = delegate;
        this.identities = identities;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser external = delegate.loadUser(userRequest);
        if (external == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(INVALID_USER), "Auth0 no ha devuelto ningún usuario");
        }
        String provider = userRequest.getClientRegistration().getRegistrationId();
        Optional<String> username = identities.usernameOf(provider, external.getSubject());
        if (username.isEmpty()) {
            throw new UnlinkedIdentityException(new PendingIdentity(provider, external.getSubject(), external.getEmail(),
                    clock.getIfAvailable(Clock::systemUTC).instant()));
        }
        UserDetails local = localUser(username.get());
        return new LocalOidcUser(local.getUsername(), local.getAuthorities(), external.getIdToken(),
                external.getUserInfo());
    }

    private UserDetails localUser(String username) {
        UserDetails local;
        try {
            local = users.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            throw invalidUser(username, e);
        }
        if (!local.isEnabled() || !local.isAccountNonLocked() || !local.isAccountNonExpired()) {
            throw invalidUser(username, null);
        }
        return local;
    }

    private static OAuth2AuthenticationException invalidUser(String username, Throwable cause) {
        return new OAuth2AuthenticationException(new OAuth2Error(INVALID_USER),
                "El usuario local %s no existe o está deshabilitado".formatted(username), cause);
    }

    /** El usuario OIDC de Auth0 con el nombre local: sus claims y su token son los de Auth0, y el nombre el del usuario. */
    private static final class LocalOidcUser extends DefaultOidcUser {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String username;

        LocalOidcUser(String username, Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken,
                      OidcUserInfo userInfo) {
            super(authorities, idToken, userInfo);
            this.username = username;
        }

        @Override
        public String getName() {
            return username;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof LocalOidcUser that && super.equals(that)
                    && username.equals(that.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), username);
        }
    }
}
