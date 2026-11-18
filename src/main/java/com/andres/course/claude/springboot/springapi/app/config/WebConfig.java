package com.andres.course.claude.springboot.springapi.app.config;

import com.andres.course.claude.springboot.springapi.app.converters.CustomerCsvHttpMessageConverter;
import com.andres.course.claude.springboot.springapi.app.converters.CustomerExcelHttpMessageConverter;
import com.andres.course.claude.springboot.springapi.app.converters.CustomerPdfHttpMessageConverter;
import com.andres.course.claude.springboot.springapi.app.converters.CustomerXmlHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra los conversores de exportación (XML del listado, CSV, Excel y PDF) para la content negotiation.
 *
 * CSV, Excel y PDF se agregan al FINAL de la lista, después de los de Spring Boot
 * (JSON, XML...): así, cuando el cliente acepta cualquier formato, gana JSON. El formato se pide con la
 * cabecera Accept o con el parámetro ?format= (ver application.properties).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(converters -> {
            // El XML del listado va al inicio para ganarle al conversor XML genérico de Jackson
            // (solo atiende application/xml + List<CustomerDto>, así que no cambia el formato por defecto).
            converters.addFirst(new CustomerXmlHttpMessageConverter());
            converters.add(new CustomerCsvHttpMessageConverter());
            converters.add(new CustomerExcelHttpMessageConverter());
            converters.add(new CustomerPdfHttpMessageConverter());
        });
    }
}
