package com.andres.course.claude.springboot.springapi.app.converters;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Listado de clientes en CSV: separador punto y coma (el que usa Excel con configuración
 * regional en español), UTF-8 y una fila de encabezado con los mismos nombres de campo
 * del JSON. Las comillas siguen las reglas de RFC 4180. Solo exporta el listado.
 */
public class CustomerCsvHttpMessageConverter extends AbstractCustomerHttpMessageConverter {

    private static final String HEADER = "id;name;lastName;email;phone;address;city;birthDate;active";
    // BOM: hace que Excel reconozca el archivo como UTF-8 y muestre bien los acentos.
    private static final String UTF8_BOM = "\uFEFF";

    public CustomerCsvHttpMessageConverter() {
        super(new MediaType(ExportMediaTypes.TEXT_CSV, StandardCharsets.UTF_8), "csv", false, false);
    }

    @Override
    protected void writeList(List<CustomerDto> customers, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(UTF8_BOM);
        writer.write(HEADER);
        writer.write("\r\n");
        for (CustomerDto c : customers) {
            writer.write(String.join(
                    ";",
                    String.valueOf(c.id()),
                    escape(c.name()),
                    escape(c.lastName()),
                    escape(c.email()),
                    escape(c.phone()),
                    escape(c.address()),
                    escape(c.city()),
                    c.birthDate() == null ? "" : c.birthDate().toString(), // ISO yyyy-MM-dd, igual que el JSON
                    String.valueOf(Boolean.TRUE.equals(c.active()))));
            writer.write("\r\n");
        }
        writer.flush();
    }

    /** Encierra entre comillas los valores con punto y coma, comillas o saltos de línea (RFC 4180). */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
