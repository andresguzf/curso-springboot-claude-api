package com.andres.course.claude.springboot.springapi.app.security;

import com.andres.course.claude.springboot.springapi.app.config.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Carga la configuración de seguridad real en los tests @WebMvcTest
 * (el slice no escanea @Component de los packages config/ ni security/).
 */
@TestConfiguration
@Import({
        SecurityConfig.class,
        JwtService.class,
        AuthCookies.class,
        ProblemDetailResponseWriter.class,
        ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class
})
public class SecurityTestConfig {

    // El AuthenticationManager de SecurityConfig lo necesita; en estos tests el login está mockeado.
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
