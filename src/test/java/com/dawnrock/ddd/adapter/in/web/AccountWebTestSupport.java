package com.dawnrock.ddd.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.dawnrock.ddd.adapter.in.SecuritySupport;
import com.dawnrock.ddd.adapter.in.TestUsers;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.MoneyMovementCommands;

/**
 * Lo que repiten los tests de las pantallas de cuentas (#177): los puertos de entrada simulados, {@code MockMvc}, la
 * configuración web que importan y el POST con token CSRF. Cada test nombra en {@code @WebMvcTest} los controladores
 * que alcanza.
 */
@Import({WebFormat.class, WebSecurityConfig.class, SecuritySupport.class, TestUsers.class})
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
}
