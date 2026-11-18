package com.andres.course.claude.springboot.springapi.app.converters;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import org.springframework.core.ResolvableType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractSmartHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Base de los conversores de exportación de clientes (solo escritura).
 *
 * Spring elige el conversor según el formato pedido (cabecera Accept o ?format=).
 * Este conversor solo acepta escribir una lista de {@link CustomerDto} y, si el
 * formato lo permite, un {@link CustomerDto} individual. Cualquier otro cuerpo
 * (por ejemplo un ProblemDetail de error) lo deja pasar a los conversores por defecto.
 */
public abstract class AbstractCustomerHttpMessageConverter extends AbstractSmartHttpMessageConverter<Object> {

    protected static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final String extension;
    private final boolean supportsDetail;
    private final boolean inline;

    /**
     * @param mediaType      formato que produce este conversor
     * @param extension      extensión del archivo descargado (sin punto); null si no es un archivo
     * @param supportsDetail true si además del listado exporta un cliente individual
     * @param inline         true para que el navegador lo muestre (PDF); false para descargarlo
     */
    protected AbstractCustomerHttpMessageConverter(MediaType mediaType, String extension,
                                                   boolean supportsDetail, boolean inline) {
        super(mediaType);
        this.extension = extension;
        this.supportsDetail = supportsDetail;
        this.inline = inline;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true; // el tipo real (con genéricos) se valida en canWrite(ResolvableType, ...)
    }

    @Override
    public boolean canRead(ResolvableType type, MediaType mediaType) {
        return false; // solo exporta: nunca lee el cuerpo de una petición
    }

    @Override
    public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
        return canWrite(mediaType) && (isCustomerList(type) || (supportsDetail && isCustomer(type)));
    }

    @Override
    protected final void writeInternal(Object body, ResolvableType type, HttpOutputMessage outputMessage,
                                       Map<String, Object> hints) throws IOException {
        if (extension != null) {
            String filename = (body instanceof CustomerDto customer)
                    ? "cliente-" + customer.id() + "." + extension
                    : "clientes." + extension;
            ContentDisposition.Builder disposition = inline ? ContentDisposition.inline() : ContentDisposition.attachment();
            outputMessage.getHeaders().setContentDisposition(
                    disposition.filename(filename, StandardCharsets.UTF_8).build());
        }

        OutputStream out = outputMessage.getBody();
        if (body instanceof CustomerDto customer) {
            writeDetail(customer, out);
        } else {
            @SuppressWarnings("unchecked")
            List<CustomerDto> customers = (List<CustomerDto>) body;
            writeList(customers, out);
        }
    }

    @Override
    public Object read(ResolvableType type, HttpInputMessage inputMessage, Map<String, Object> hints) {
        throw new HttpMessageNotReadableException("Formato de solo exportación", inputMessage);
    }

    /** Escribe el listado completo de clientes. */
    protected abstract void writeList(List<CustomerDto> customers, OutputStream out) throws IOException;

    /** Escribe el detalle de un cliente; solo se llama si {@code supportsDetail} es true. */
    protected void writeDetail(CustomerDto customer, OutputStream out) throws IOException {
        throw new UnsupportedOperationException("Este formato no exporta un cliente individual");
    }

    protected static String format(LocalDate date) {
        return date == null ? "" : DATE_FORMAT.format(date);
    }

    protected static String activeLabel(Boolean active) {
        return Boolean.TRUE.equals(active) ? "Activo" : "Inactivo";
    }

    protected static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isCustomer(ResolvableType type) {
        return CustomerDto.class.isAssignableFrom(type.toClass());
    }

    private static boolean isCustomerList(ResolvableType type) {
        return List.class.isAssignableFrom(type.toClass())
                && CustomerDto.class.isAssignableFrom(type.as(List.class).getGeneric(0).toClass());
    }
}
