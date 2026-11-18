package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.OperationNotAllowedException;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.security.JwtService;
import com.andres.course.claude.springboot.springapi.app.security.SecurityTestConfig;
import com.andres.course.claude.springboot.springapi.app.services.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.accessCookie;
import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.withCsrf;
import static org.mockito.ArgumentMatchers.any;
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
    void elPasswordNuncaSaleEnLaRespuesta() throws Exception {
        // Aunque el DTO trajera password, Jackson no lo escribe (WRITE_ONLY).
        when(userService.findAll()).thenReturn(List.of(
                new UserDto(1L, "ana@ejemplo.com", "secreto123", Role.USER, true)));

        mockMvc.perform(get("/api/users").cookie(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ana@ejemplo.com"))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void adminCreaUsuarioYElPasswordSeLeeDelRequest() throws Exception {
        when(userService.create(any())).thenReturn(new UserDto(2L, "nuevo@ejemplo.com", null, Role.USER, true));

        mockMvc.perform(withCsrf(post("/api/users"), admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nuevo@ejemplo.com","password":"password123","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/users/2"))
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(userService).create(new UserDto(null, "nuevo@ejemplo.com", "password123", Role.USER, null));
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
    void adminNoPuedeBorrarseASiMismo() throws Exception {
        doThrow(new OperationNotAllowedException("No puedes borrar tu propio usuario"))
                .when(userService).delete(1L, "admin@ejemplo.com");

        mockMvc.perform(withCsrf(delete("/api/users/1"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operación no permitida"));
    }
}
