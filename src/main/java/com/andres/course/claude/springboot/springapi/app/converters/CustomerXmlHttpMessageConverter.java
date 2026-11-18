package com.andres.course.claude.springboot.springapi.app.converters;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.springframework.http.MediaType;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.io.OutputStream;
import java.util.List;

/**
 * Listado de clientes en XML con nombres propios: {@code <customers><customer>...</customer></customers>}.
 *
 * Sin este conversor, Jackson escribe una lista como {@code <List><item>...</item></List>}.
 * El detalle de un cliente lo escribe el conversor XML de Spring Boot como {@code <customer>}
 * gracias a {@code @JsonRootName} en {@link CustomerDto}.
 */
public class CustomerXmlHttpMessageConverter extends AbstractCustomerHttpMessageConverter {

    private final XmlMapper xmlMapper = XmlMapper.builder().build();

    public CustomerXmlHttpMessageConverter() {
        super(MediaType.APPLICATION_XML, null, false, false); // se muestra como respuesta, no como descarga
    }

    @Override
    protected void writeList(List<CustomerDto> customers, OutputStream out) {
        xmlMapper.writeValue(out, new Customers(customers));
    }

    /** Envoltorio solo para dar nombre a la raíz y a cada elemento de la lista en XML. */
    @JsonRootName("customers")
    record Customers(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "customer")
            List<CustomerDto> customers
    ) {
    }
}
