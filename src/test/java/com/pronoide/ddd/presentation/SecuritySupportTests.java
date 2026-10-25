package com.pronoide.ddd.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pronoide.ddd.application.Caller;

class SecuritySupportTests {

    private final SecuritySupport support = new SecuritySupport();
    private final PasswordEncoder encoder = support.passwordEncoder();

    private UserDetailsService users(List<SecurityUsersProperties.User> users, String admin, String password) {
        return support.userDetailsService(new SecurityUsersProperties(users), encoder, admin, password);
    }

    @Test
    @DisplayName("Los usuarios configurados entran con su contraseña y su rol")
    void los_usuarios_configurados_entran_con_su_contrasena_y_su_rol() {
        UserDetails ada = users(List.of(new SecurityUsersProperties.User("ada", encoder.encode("demo"), "USER")), "", "")
                .loadUserByUsername("ada");

        assertThat(encoder.matches("demo", ada.getPassword())).isTrue();
        assertThat(ada.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Las variables de entorno añaden un administrador")
    void las_variables_de_entorno_anaden_un_administrador() {
        UserDetails admin = users(List.of(), "jefa", "secreta").loadUserByUsername("jefa");

        assertThat(encoder.matches("secreta", admin.getPassword())).isTrue();
        assertThat(admin.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Sin perfil demo ni variables no hay usuarios")
    void sin_perfil_demo_ni_variables_no_hay_usuarios() {
        assertThatThrownBy(() -> users(List.of(), "", "").loadUserByUsername("admin"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Un usuario que no cumple el formato no deja arrancar la aplicación")
    void un_usuario_que_no_cumple_el_formato_no_deja_arrancar() {
        assertThatIllegalStateException()
                .isThrownBy(() -> users(List.of(new SecurityUsersProperties.User("Ada", "x", "USER")), "", ""))
                .withMessageContaining("Ada");
    }

    @Test
    @DisplayName("Quien pide es administrador si tiene el rol ADMIN")
    void quien_pide_es_administrador_si_tiene_el_rol_admin() {
        Caller admin = CurrentCaller.from(UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        Caller ada = CurrentCaller.from(UsernamePasswordAuthenticationToken.authenticated(
                "ada", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThat(admin).isEqualTo(Caller.builder().username("admin").admin(true).build());
        assertThat(ada).isEqualTo(Caller.builder().username("ada").admin(false).build());
    }
}
