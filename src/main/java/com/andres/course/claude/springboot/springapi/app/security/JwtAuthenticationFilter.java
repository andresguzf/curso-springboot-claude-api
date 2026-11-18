package com.andres.course.claude.springboot.springapi.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.List;

/**
 * Lee el JWT de la cookie HttpOnly access_token y, si es válido, autentica la petición.
 *
 * Es stateless: el email y el rol salen del token, sin consultar la base de datos.
 * Si el token falta, es inválido o expiró, NO corta la petición: sigue sin autenticar.
 * Así las rutas públicas (login, refresh) funcionan aunque llegue una cookie vencida,
 * y en las protegidas responde 401 el AuthenticationEntryPoint.
 *
 * No es un @Component a propósito: Spring Boot registraría el filtro también como filtro
 * de servlet y se ejecutaría dos veces. Se crea en SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Atributo de la petición con el motivo del rechazo, para el mensaje del 401. */
    public static final String JWT_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";
    public static final String JWT_EXPIRED = "expired";
    public static final String JWT_INVALID = "invalid";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, AuthCookies.ACCESS_TOKEN);
        if (cookie != null && !cookie.getValue().isBlank()) {
            try {
                Claims claims = jwtService.parse(cookie.getValue());
                String role = claims.get(JwtService.ROLE_CLAIM, String.class);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        claims.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (ExpiredJwtException ex) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, JWT_EXPIRED);
            } catch (JwtException | IllegalArgumentException ex) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, JWT_INVALID);
            }
        }
        chain.doFilter(request, response);
    }
}
