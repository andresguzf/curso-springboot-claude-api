package com.andres.course.claude.springboot.springapi.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

// El perfil prod se comprueba leyendo el archivo, sin levantar Spring ni base de datos:
// así nadie reactiva el seed, Swagger o el SQL en el log de producción sin que falle un test.
class ProdProfileTest {

    private static Properties prod;

    @BeforeAll
    static void loadProdProperties() throws IOException {
        prod = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
    }

    @Test
    void noEjecutaElSeedNiModificaElEsquema() {
        assertThat(prod.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(prod.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void noEscribeElSqlEnElLog() {
        assertThat(prod.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        assertThat(prod.getProperty("spring.jpa.properties.hibernate.format_sql")).isEqualTo("false");
    }

    @Test
    void desactivaSwagger() {
        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void laBaseDeDatosNoTieneValoresPorDefecto() {
        assertThat(prod.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(prod.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(prod.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
    }

    @Test
    void respetaLasCabecerasDelProxyConHttps() {
        assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
    }
}
