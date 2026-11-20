package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.dto.UserFilter;
import com.andres.course.claude.springboot.springapi.app.exceptions.OperationNotAllowedException;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.security.JwtService;
import com.andres.course.claude.springboot.springapi.app.security.SecurityTestConfig;
import com.andres.course.claude.springboot.springapi.app.services.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.accessCookie;
import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    private Cookie admin() {
        return accessCookie(jwtService, "admin@ejemplo.com", Role.ADMIN);
    }

    @Test
    void userNoPuedeAdministrarUsuarios() throws Exception {
        mockMvc.perform(get("/api/users").cookie(accessCookie(jwtService, "user@ejemplo.com", Role.USER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    void elListadoEsPaginadoYElPasswordNuncaSaleEnLaRespuesta() throws Exception {
        // Aunque el DTO trajera password, Jackson no lo escribe (WRITE_ONLY).
        when(userService.findPage(any(), any())).thenAnswer(invocation -> new PageImpl<>(List.of(
                new UserDto(1L, "ana@ejemplo.com", "secreto123", Role.USER, true, null, null)),
                invocation.getArgument(1), 1));

        mockMvc.perform(get("/api/users").cookie(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("ana@ejemplo.com"))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).findPage(eq(UserFilter.empty()), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("email"));
    }

    @Test
    void enlazaLosFiltrosDeUsuarios() throws Exception {
        when(userService.findPage(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/users").cookie(admin())
                        .param("search", "admin").param("role", "ADMIN").param("enabled", "true")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).findPage(eq(new UserFilter("admin", Role.ADMIN, true)), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void unRolInexistenteResponde400ConMensajeEnEspanol() throws Exception {
        mockMvc.perform(get("/api/users").param("role", "FOO").cookie(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Error de validación"))
                .andExpect(jsonPath("$.errors.role").value("El valor 'FOO' no tiene un formato válido"));
        verifyNoInteractions(userService);
    }

    @Test
    void adminCreaUsuarioYElPasswordSeLeeDelRequest() throws Exception {
        when(userService.create(any())).thenReturn(new UserDto(2L, "nuevo@ejemplo.com", null, Role.USER, true, null, null));

        mockMvc.perform(withCsrf(post("/api/users"), admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nuevo@ejemplo.com","password":"password123","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/users/2"))
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(userService).create(new UserDto(null, "nuevo@ejemplo.com", "password123", Role.USER, null, null, null));
    }

    @Test
    void lasFechasDeAuditoriaSonSoloLectura() throws Exception {
        LocalDateTime creado = LocalDateTime.of(2026, 11, 19, 10, 15, 30);
        when(userService.findById(2L)).thenReturn(
                new UserDto(2L, "nuevo@ejemplo.com", null, Role.USER, true, creado, null));
        when(userService.create(any())).thenReturn(
                new UserDto(2L, "nuevo@ejemplo.com", null, Role.USER, true, creado, null));

        // En la respuesta salen las fechas de auditoría (updatedAt null si nunca se modificó)
        mockMvc.perform(get("/api/users/2").cookie(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value("2026-11-19T10:15:30"))
                .andExpect(jsonPath("$.updatedAt").isEmpty());

        // En el request se ignoran: el service las recibe en null
        mockMvc.perform(withCsrf(post("/api/users"), admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nuevo@ejemplo.com","password":"password123","role":"USER",
                                 "createdAt":"2000-01-01T00:00:00","updatedAt":"2000-01-01T00:00:00"}
                                """))
                .andExpect(status().isCreated());
        verify(userService).create(new UserDto(null, "nuevo@ejemplo.com", "password123", Role.USER, null, null, null));
    }

    @Test
    void validaLosCampos() throws Exception {
        mockMvc.perform(withCsrf(post("/api/users"), admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-es-email","password":"corta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("El email no tiene un formato válido"))
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").value("El rol es obligatorio"));
        verifyNoInteractions(userService);
    }

    @Test
    void borrarAlUltimoAdminResponde409ConProblemDetail() throws Exception {
        doThrow(new OperationNotAllowedException("Debe quedar al menos un administrador habilitado"))
                .when(userService).delete(2L, "admin@ejemplo.com");

        mockMvc.perform(withCsrf(delete("/api/users/2"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operación no permitida"))
                .andExpect(jsonPath("$.detail").value("Debe quedar al menos un administrador habilitado"));
    }

    @Test
    void adminNoPuedeBorrarseASiMismo() throws Exception {
        doThrow(new OperationNotAllowedException("No puedes borrar tu propio usuario"))
                .when(userService).delete(1L, "admin@ejemplo.com");

        mockMvc.perform(withCsrf(delete("/api/users/1"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operación no permitida"));
    }
}
