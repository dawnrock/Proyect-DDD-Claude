package com.dawnrock.ddd.adapter.in.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.hamcrest.Matcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.TransactionSummary;
import com.dawnrock.ddd.application.TransferSummary;

/**
 * Lo que repiten los tests de las pantallas de cuentas (#177): los puertos de entrada simulados, {@code MockMvc}, la
 * configuración web que importan, el POST con token CSRF y los datos de prueba compartidos. Cada test nombra en
 * {@code @WebMvcTest} los controladores que alcanza.
 */
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class, TestUsers.class, AccountDetailView.class})
abstract class AccountWebTestSupport {

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected AccountOpeningCommands commands;

    @MockitoBean
    protected MoneyMovementCommands movements;

    @MockitoBean
    protected CheckingAccountQueries queries;

    /** POST con token CSRF: la cadena web lo exige (ADR 0008). */
    protected static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }

    protected static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

    protected static final Caller ADA = Caller.builder().username("ada").admin(false).build();

    protected static AccountSummary ownAccount(UUID id, String number, String holder) {
        return AccountSummary.builder()
                .id(id)
                .accountNumber(number)
                .holderName(holder).segment("RETAIL")
                .balance(new BigDecimal("150.00"))
                .currency("EUR")
                .build();
    }

    protected static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    protected static TransactionSummary transaction(String type, String amount, String status) {
        return TransactionSummary.builder()
                .id(UUID.randomUUID())
                .type(type)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .occurredOn(Instant.parse("2026-10-23T10:15:00Z"))
                .status(status)
                .build();
    }

    protected static TransactionSummary transfer(String type, String amount, String status, String counterpart) {
        return TransactionSummary.builder()
                .id(UUID.randomUUID())
                .type(type)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .occurredOn(Instant.parse("2026-10-23T10:15:00Z"))
                .status(status)
                .counterpartAccountNumber(counterpart)
                .build();
    }

    protected static AccountDetails details(TransactionSummary... transactions) {
        return AccountDetails.builder()
                .id(ACCOUNT_ID)
                .accountNumber("ES1234567890123456789012")
                .holderName("Ana Pérez")
                .segment("HIGH_NET_WORTH")
                .balance(new BigDecimal("150.00"))
                .currency("EUR")
                .addTransactions(transactions)
                .build();
    }

    protected static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    protected static final String TARGET_IBAN = "ES9876543210987654321098";

    protected static TransferSummary transfer(String amount, String status) {
        return TransferSummary.builder()
                .sourceAccountId(ACCOUNT_ID)
                .sourceAccountNumber("ES1234567890123456789012")
                .targetAccountId(OTHER_ID)
                .targetAccountNumber(TARGET_IBAN)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .occurredOn(Instant.parse("2026-10-25T10:15:00Z"))
                .status(status)
                .build();
    }
    /** Petición como la envía htmx: con la cabecera HX-Request. */

    protected static MockHttpServletRequestBuilder htmx(MockHttpServletRequestBuilder request) {
        return request.header("HX-Request", "true");
    }
    /** Atributo que apunta a un recurso propio con el hash de su contenido en el nombre (/js/app-<md5>.js). */

    protected static Matcher<String> hashed(String attribute, String path, String extension) {
        return matchesPattern("(?s).*" + attribute + "=\"" + Pattern.quote(path) + "-[0-9a-f]{32}"
                + Pattern.quote(extension) + "\".*");
    }
    /** Atributo que apunta a un fichero de un WebJar, con la versión que añada Spring a la ruta. */

    protected static Matcher<String> webjar(String attribute, String webjar, String file) {
        return matchesPattern("(?s).*" + attribute + "=\"/webjars/" + Pattern.quote(webjar)
                + "/[^/\"]+/" + Pattern.quote(file) + "\".*");
    }
}
