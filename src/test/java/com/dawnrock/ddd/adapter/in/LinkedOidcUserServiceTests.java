package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

class LinkedOidcUserServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final String SUB = "auth0|abc123";

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate = mockDelegate();
    private final InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
    private InMemoryExternalIdentities identities;
    private LinkedOidcUserService service;
    private OidcUserRequest request;
    private OidcIdToken idToken;

    @SuppressWarnings("unchecked")
    private static OAuth2UserService<OidcUserRequest, OidcUser> mockDelegate() {
        return mock(OAuth2UserService.class);
    }

    @BeforeEach
    void setUp() {
        identities = new InMemoryExternalIdentities(users::userExists);
        ObjectProvider<Clock> clock = mock(ObjectProvider.class);
        when(clock.getIfAvailable(any())).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        service = new LinkedOidcUserService(delegate, identities, users, clock);
        idToken = OidcIdToken.withTokenValue("id-token").subject(SUB).issuer("https://tenant.example.auth0.com/")
                .claim("email", "ada@example.com").issuedAt(NOW).expiresAt(NOW.plus(Duration.ofHours(1))).build();
        request = new OidcUserRequest(
                Auth0ClientRegistration.registration(new Auth0Properties("tenant.example.auth0.com", "id", "secret", null, null)),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access", NOW, NOW.plusSeconds(60)),
                idToken);
        when(delegate.loadUser(request)).thenReturn(new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken));
    }

    private void createUser(String username, String role, boolean enabled) {
        users.createUser(User.withUsername(username).password("{noop}x").roles(role).disabled(!enabled).build());
    }

    @Test
    @DisplayName("Una identidad vinculada entra con el nombre y el rol del usuario local")
    void aLinkedIdentityGetsTheLocalNameAndRole() {
        createUser("ada", "USER", true);
        identities.link(ExternalIdentities.AUTH0, SUB, "ada");

        OidcUser user = service.loadUser(request);

        assertThat(user.getName()).isEqualTo("ada");
        assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Un administrador vinculado conserva el rol ADMIN de la base de datos")
    void aLinkedAdminKeepsTheRoleFromTheDatabase() {
        createUser("admin", "ADMIN", true);
        identities.link(ExternalIdentities.AUTH0, SUB, "admin");

        assertThat(service.loadUser(request).getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Conserva el token de identidad y los claims de Auth0, y el sub sigue siendo el de Auth0")
    void itKeepsTheIdTokenAndTheAuth0Claims() {
        createUser("ada", "USER", true);
        identities.link(ExternalIdentities.AUTH0, SUB, "ada");

        OidcUser user = service.loadUser(request);

        assertThat(user.getIdToken()).isSameAs(idToken);
        assertThat(user.getSubject()).isEqualTo(SUB);
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("Una identidad desconocida lanza UnlinkedIdentityException con su sub y su correo")
    void anUnknownIdentityThrowsUnlinkedIdentityException() {
        assertThatExceptionOfType(UnlinkedIdentityException.class).isThrownBy(() -> service.loadUser(request))
                .satisfies(e -> {
                    PendingIdentity pending = e.pending();
                    assertThat(pending.provider()).isEqualTo(ExternalIdentities.AUTH0);
                    assertThat(pending.subject()).isEqualTo(SUB);
                    assertThat(pending.email()).isEqualTo("ada@example.com");
                    assertThat(pending.createdAt()).isEqualTo(NOW);
                });
    }

    @Test
    @DisplayName("Un usuario local deshabilitado se rechaza con invalid_user")
    void aDisabledLocalUserIsRejected() {
        createUser("ada", "USER", true);
        identities.link(ExternalIdentities.AUTH0, SUB, "ada");
        users.updateUser(User.withUsername("ada").password("{noop}x").roles("USER").disabled(true).build());

        assertThatExceptionOfType(OAuth2AuthenticationException.class).isThrownBy(() -> service.loadUser(request))
                .satisfies(e -> assertThat(e.getError().getErrorCode()).isEqualTo("invalid_user"));
    }

    @Test
    @DisplayName("Un usuario local que ya no existe se rechaza con invalid_user")
    void aMissingLocalUserIsRejected() {
        createUser("ada", "USER", true);
        identities.link(ExternalIdentities.AUTH0, SUB, "ada");
        users.deleteUser("ada");

        assertThatExceptionOfType(OAuth2AuthenticationException.class).isThrownBy(() -> service.loadUser(request))
                .satisfies(e -> assertThat(e.getError().getErrorCode()).isEqualTo("invalid_user"));
    }
}
