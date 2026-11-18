package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.dto.LoginDto;
import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.security.AuthCookies;
import com.andres.course.claude.springboot.springapi.app.security.AuthTokens;
import com.andres.course.claude.springboot.springapi.app.services.AuthService;
import com.andres.course.claude.springboot.springapi.app.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación con cookies HttpOnly: los tokens viajan solo en Set-Cookie, nunca en el cuerpo.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final AuthCookies authCookies;

    public AuthController(AuthService authService, UserService userService, AuthCookies authCookies) {
        this.authService = authService;
        this.userService = userService;
        this.authCookies = authCookies;
    }

    // GET /api/auth/csrf -> 200 con el token CSRF (también queda en la cookie XSRF-TOKEN).
    // Se envía en la cabecera X-XSRF-TOKEN en cada POST/PUT/DELETE (salvo el login).
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    // POST /api/auth/login -> 200 con el usuario + cookies access_token y refresh_token; 401 si falla.
    @PostMapping("/login")
    public UserDto login(@Valid @RequestBody LoginDto dto, HttpServletResponse response) {
        return withTokenCookies(authService.login(dto), response);
    }

    // POST /api/auth/refresh -> 200 con cookies nuevas (rotación); 401 si el refresh token no es válido.
    @PostMapping("/refresh")
    public UserDto refresh(@CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
                           HttpServletResponse response) {
        return withTokenCookies(authService.refresh(refreshToken), response);
    }

    // POST /api/auth/logout -> 204: revoca el refresh token y borra ambas cookies.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
                                       HttpServletResponse response) {
        authService.logout(refreshToken);
        addCookies(response, authCookies.clearAccessToken(), authCookies.clearRefreshToken());
        return ResponseEntity.noContent().build();
    }

    // GET /api/auth/me -> 200 con el usuario autenticado; 401 sin sesión.
    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }

    private UserDto withTokenCookies(AuthTokens tokens, HttpServletResponse response) {
        addCookies(response, authCookies.accessToken(tokens.accessToken()), authCookies.refreshToken(tokens.refreshToken()));
        return tokens.user();
    }

    // Se usa addHeader y no ResponseEntity.header(SET_COOKIE, ...): las cabeceras de ResponseEntity
    // reemplazan los Set-Cookie que ya escribió Spring Security (la cookie XSRF-TOKEN) y el cliente
    // se quedaría sin token CSRF después del login.
    private static void addCookies(HttpServletResponse response, ResponseCookie... cookies) {
        for (ResponseCookie cookie : cookies) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
    }
}
