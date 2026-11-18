package com.andres.course.claude.springboot.springapi.app.config;

import com.andres.course.claude.springboot.springapi.app.security.JwtAuthenticationFilter;
import com.andres.course.claude.springboot.springapi.app.security.JwtProperties;
import com.andres.course.claude.springboot.springapi.app.security.JwtService;
import com.andres.course.claude.springboot.springapi.app.security.ProblemDetailAccessDeniedHandler;
import com.andres.course.claude.springboot.springapi.app.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Seguridad stateless con JWT en cookies HttpOnly.
 *
 * - Sin sesión HTTP: cada petición se autentica con la cookie access_token (JwtAuthenticationFilter).
 * - CSRF activo: como el navegador envía las cookies solo, las peticiones que modifican datos deben
 *   traer la cabecera X-XSRF-TOKEN con el valor de la cookie XSRF-TOKEN (legible por JavaScript).
 *   Un sitio malicioso no puede leer esa cookie, así que no puede armar la cabecera.
 * - Roles: USER solo lee clientes; ADMIN administra clientes y usuarios.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, AdminProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   JwtProperties jwtProperties,
                                                   ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                                   ProblemDetailAccessDeniedHandler accessDeniedHandler) {
        // Cookie XSRF-TOKEN legible por JavaScript (HttpOnly=false) para que el cliente la copie en la cabecera.
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .sameSite("Strict")
                .secure(jwtProperties.cookieSecure()));
        // Sin nombre de atributo, el token se carga en cada petición: la cookie XSRF-TOKEN queda creada
        // desde la primera respuesta (incluido el login), sin esperar a que alguien lo pida.
        CsrfTokenRequestAttributeHandler csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfTokenRequestHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler)
                        // El login no lleva CSRF: aún no hay sesión que proteger y la cookie es SameSite=Strict.
                        .ignoringRequestMatchers("/api/auth/login"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Públicos
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        // Clientes: USER y ADMIN leen; solo ADMIN crea, edita y borra
                        .requestMatchers(HttpMethod.GET, "/api/customers/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/customers/**").hasRole("ADMIN")
                        // Usuarios: solo ADMIN
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        // Todo lo demás (p. ej. /api/auth/me) requiere sesión
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationManager que usa el login: busca el usuario por email y compara el password con BCrypt.
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
