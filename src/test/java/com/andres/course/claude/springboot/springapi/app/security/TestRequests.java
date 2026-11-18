package com.andres.course.claude.springboot.springapi.app.security;

import com.andres.course.claude.springboot.springapi.app.models.Role;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ayudas para armar peticiones autenticadas como lo haría un navegador (cookies + cabecera CSRF). */
public final class TestRequests {

    public static final String CSRF_TOKEN = "test-csrf-token";

    private TestRequests() {
    }

    /** Cookie access_token con un JWT real firmado con la clave de test. */
    public static Cookie accessCookie(JwtService jwtService, String email, Role role) {
        return new Cookie(AuthCookies.ACCESS_TOKEN, jwtService.generateAccessToken(email, role));
    }

    /** Cookie XSRF-TOKEN + la misma valor en la cabecera X-XSRF-TOKEN (double submit). */
    public static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie... cookies) {
        Cookie[] all = new Cookie[cookies.length + 1];
        System.arraycopy(cookies, 0, all, 0, cookies.length);
        all[cookies.length] = new Cookie("XSRF-TOKEN", CSRF_TOKEN);
        return request.cookie(all).header("X-XSRF-TOKEN", CSRF_TOKEN);
    }
}
