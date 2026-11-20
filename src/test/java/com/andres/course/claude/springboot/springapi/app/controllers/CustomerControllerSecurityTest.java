package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.security.JwtService;
import com.andres.course.claude.springboot.springapi.app.security.SecurityTestConfig;
import com.andres.course.claude.springboot.springapi.app.services.CustomerService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.accessCookie;
import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class CustomerControllerSecurityTest {

    private static final String BODY = """
            {"name":"Ana","lastName":"Pérez","email":"ana@ejemplo.com","phone":"+56 9 1000 0001",
             "address":"Av. Siempre Viva 101","city":"Santiago","birthDate":"1990-05-20"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CustomerService customerService;

    private Cookie user() {
        return accessCookie(jwtService, "user@ejemplo.com", Role.USER);
    }

    private Cookie admin() {
        return accessCookie(jwtService, "admin@ejemplo.com", Role.ADMIN);
    }

    private static CustomerDto customer() {
        return new CustomerDto(1L, "Ana", "Pérez", "ana@ejemplo.com", "+56 9 1000 0001",
                "Av. Siempre Viva 101", "Santiago", LocalDate.of(1990, 5, 20), true,
                LocalDateTime.of(2026, 11, 19, 10, 15, 30), null);
    }

    @Test
    void sinCookieResponde401ConProblemDetail() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.instance").value("/api/customers"));
        verifyNoInteractions(customerService);
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        mockMvc.perform(get("/api/customers").cookie(new Cookie("access_token", "no-es-un-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("El token de acceso no es válido: inicia sesión de nuevo"));
    }

    @Test
    void userPuedeListarYBuscar() throws Exception {
        when(customerService.findAll("ana", true)).thenReturn(List.of(customer()));

        mockMvc.perform(get("/api/customers").param("search", "ana").param("active", "true").cookie(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ana@ejemplo.com"));
        verify(customerService).findAll("ana", true);
    }

    @Test
    void userNoPuedeCrear() throws Exception {
        mockMvc.perform(withCsrf(post("/api/customers"), user())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        verifyNoInteractions(customerService);
    }

    @Test
    void adminPuedeCrearConTokenCsrf() throws Exception {
        when(customerService.create(any())).thenReturn(customer());

        mockMvc.perform(withCsrf(post("/api/customers"), admin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void lasFechasDeAuditoriaSonSoloLectura() throws Exception {
        when(customerService.findById(1L)).thenReturn(customer());
        when(customerService.create(any())).thenReturn(customer());

        // En la respuesta salen las fechas de auditoría (updatedAt null si nunca se modificó)
        mockMvc.perform(get("/api/customers/1").cookie(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value("2026-11-19T10:15:30"))
                .andExpect(jsonPath("$.updatedAt").isEmpty());

        // En el request se ignoran: el service recibe createdAt y updatedAt en null
        String bodyConFechas = BODY.replace("\"birthDate\":\"1990-05-20\"",
                "\"birthDate\":\"1990-05-20\",\"createdAt\":\"2000-01-01T00:00:00\",\"updatedAt\":\"2000-01-01T00:00:00\"");
        mockMvc.perform(withCsrf(post("/api/customers"), admin())
                        .contentType(MediaType.APPLICATION_JSON).content(bodyConFechas))
                .andExpect(status().isCreated());

        ArgumentCaptor<CustomerDto> captor = ArgumentCaptor.forClass(CustomerDto.class);
        verify(customerService).create(captor.capture());
        assertThat(captor.getValue().createdAt()).isNull();
        assertThat(captor.getValue().updatedAt()).isNull();
        assertThat(captor.getValue().email()).isEqualTo("ana@ejemplo.com");
    }

    @Test
    void adminSinTokenCsrfResponde403() throws Exception {
        mockMvc.perform(post("/api/customers").cookie(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Token CSRF inválido"));
        verifyNoInteractions(customerService);
    }

    @Test
    void adminPuedeBorrar() throws Exception {
        mockMvc.perform(withCsrf(delete("/api/customers/1"), admin()))
                .andExpect(status().isNoContent());
        verify(customerService).delete(1L);
    }
}
