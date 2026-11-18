package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.dto.LoginDto;
import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.InvalidTokenException;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.security.AuthTokens;
import com.andres.course.claude.springboot.springapi.app.security.JwtService;
import com.andres.course.claude.springboot.springapi.app.security.SecurityTestConfig;
import com.andres.course.claude.springboot.springapi.app.services.AuthService;
import com.andres.course.claude.springboot.springapi.app.services.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.accessCookie;
import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.withCsrf;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    private static final UserDto ADMIN = new UserDto(1L, "admin@ejemplo.com", null, Role.ADMIN, true);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @Test
    void loginPoneLasCookiesHttpOnlyYNoDevuelveLosTokens() throws Exception {
        when(authService.login(any())).thenReturn(new AuthTokens("jwt-acceso", "token-refresh", ADMIN));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@ejemplo.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@ejemplo.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(content().string(allOf(not(containsString("jwt-acceso")), not(containsString("token-refresh")))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
                        containsString("access_token=jwt-acceso"), containsString("Path=/;"),
                        containsString("HttpOnly"), containsString("Secure"), containsString("SameSite=Strict")))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
                        containsString("refresh_token=token-refresh"), containsString("Path=/api/auth"),
                        containsString("HttpOnly"), containsString("SameSite=Strict")))))
                // La cookie CSRF queda creada desde la primera respuesta y NO es HttpOnly (el cliente la lee).
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
                        containsString("XSRF-TOKEN="), not(containsString("HttpOnly"))))));
        verify(authService).login(new LoginDto("admin@ejemplo.com", "password123"));
    }

    @Test
    void loginConCredencialesMalasResponde401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@ejemplo.com","password":"mala"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"));
    }

    @Test
    void refreshSinTokenCsrfResponde403() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "viejo")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void refreshRotaLasCookies() throws Exception {
        when(authService.refresh("viejo")).thenReturn(new AuthTokens("jwt-nuevo", "refresh-nuevo", ADMIN));

        mockMvc.perform(withCsrf(post("/api/auth/refresh"), new Cookie("refresh_token", "viejo")))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("access_token=jwt-nuevo"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("refresh_token=refresh-nuevo"))));
    }

    @Test
    void refreshInvalidoResponde401() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidTokenException("El refresh token ya fue usado: inicia sesión de nuevo"));

        mockMvc.perform(withCsrf(post("/api/auth/refresh"), new Cookie("refresh_token", "robado")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Token inválido"));
    }

    @Test
    void logoutBorraLasCookies() throws Exception {
        mockMvc.perform(withCsrf(post("/api/auth/logout"), new Cookie("refresh_token", "actual")))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
                        containsString("access_token=;"), containsString("Max-Age=0")))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
                        containsString("refresh_token=;"), containsString("Max-Age=0")))));
        verify(authService).logout("actual");
    }

    @Test
    void meDevuelveElUsuarioAutenticado() throws Exception {
        when(userService.findByEmail("admin@ejemplo.com")).thenReturn(ADMIN);

        mockMvc.perform(get("/api/auth/me").cookie(accessCookie(jwtService, "admin@ejemplo.com", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@ejemplo.com"));
    }

    @Test
    void meSinSesionResponde401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfDevuelveElTokenYLaCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("XSRF-TOKEN="))));
    }
}
