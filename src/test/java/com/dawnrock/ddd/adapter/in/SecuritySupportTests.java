package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;

import com.dawnrock.ddd.application.Caller;

class SecuritySupportTests {

    private final SecuritySupport support = new SecuritySupport();
    private final PasswordEncoder encoder = support.passwordEncoder();

    /** Una base H2 vacía con el esquema de las migraciones (#150): los usuarios viven en ella. */
    private final DataSource database = migratedDatabase();

    private static DataSource migratedDatabase() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        return dataSource;
    }

    private UserDetailsManager users(List<SecurityUsersProperties.User> users, String admin, String password) {
        return new UserStore().userDetailsService(database, new SecurityUsersProperties(users), encoder, admin, password);
    }

    @Test
    @DisplayName("Un usuario creado en el alta sigue existiendo tras un reinicio sobre la misma base (#150)")
    void aUserCreatedOnSignUpSurvivesARestart() {
        users(List.of(), "", "").createUser(User.withUsername("lin").password(encoder.encode("contraseña"))
                .roles("USER").build());

        UserDetailsService afterRestart = users(List.of(), "", "");

        UserDetails lin = afterRestart.loadUserByUsername("lin");
        assertThat(encoder.matches("contraseña", lin.getPassword())).isTrue();
        assertThat(lin.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Arrancar dos veces no duplica los usuarios configurados y aplica su contraseña y su rol actuales")
    void startingTwiceDoesNotDuplicateTheConfiguredUsers() {
        users(List.of(new SecurityUsersProperties.User("ada", encoder.encode("vieja"), "USER")), "jefa", "secreta");

        UserDetailsService secondStart = users(
                List.of(new SecurityUsersProperties.User("ada", encoder.encode("nueva"), "ADMIN")), "jefa", "secreta");

        UserDetails ada = secondStart.loadUserByUsername("ada");
        assertThat(encoder.matches("nueva", ada.getPassword())).isTrue();
        assertThat(ada.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(encoder.matches("secreta", secondStart.loadUserByUsername("jefa").getPassword())).isTrue();
    }

    @Test
    @DisplayName("Los usuarios configurados entran con su contraseña y su rol")
    void theConfiguredUsersLogInWithTheirPasswordAndRole() {
        UserDetails ada = users(List.of(new SecurityUsersProperties.User("ada", encoder.encode("demo"), "USER")), "", "")
                .loadUserByUsername("ada");

        assertThat(encoder.matches("demo", ada.getPassword())).isTrue();
        assertThat(ada.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Las variables de entorno añaden un administrador")
    void theEnvironmentVariablesAddAnAdministrator() {
        UserDetails admin = users(List.of(), "jefa", "secreta").loadUserByUsername("jefa");

        assertThat(encoder.matches("secreta", admin.getPassword())).isTrue();
        assertThat(admin.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Sin perfil demo ni variables no hay usuarios")
    void withoutDemoProfileOrVariablesThereAreNoUsers() {
        assertThatThrownBy(() -> users(List.of(), "", "").loadUserByUsername("admin"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Un usuario que no cumple el formato no deja arrancar la aplicación")
    void aUsernameWithTheWrongFormatStopsTheStartup() {
        assertThatIllegalStateException()
                .isThrownBy(() -> users(List.of(new SecurityUsersProperties.User("Ada", "x", "USER")), "", ""))
                .withMessageContaining("Ada");
    }

    @Test
    @DisplayName("Quien pide es administrador si tiene el rol ADMIN")
    void theCallerIsAnAdministratorWhenTheyHaveTheAdminRole() {
        Caller admin = CurrentCaller.from(UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        Caller ada = CurrentCaller.from(UsernamePasswordAuthenticationToken.authenticated(
                "ada", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        assertThat(admin).isEqualTo(Caller.builder().username("admin").admin(true).build());
        assertThat(ada).isEqualTo(Caller.builder().username("ada").admin(false).build());
    }
}
