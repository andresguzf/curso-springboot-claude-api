package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceFilter;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import com.andres.course.claude.springboot.springapi.app.models.Role;
import com.andres.course.claude.springboot.springapi.app.security.JwtService;
import com.andres.course.claude.springboot.springapi.app.security.SecurityTestConfig;
import com.andres.course.claude.springboot.springapi.app.services.InvoiceService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.accessCookie;
import static com.andres.course.claude.springboot.springapi.app.security.TestRequests.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class InvoiceControllerTest {

    private static final String BODY = """
            {"customerId":1,"issueDate":"2026-11-20","dueDate":"2026-12-20","status":"PENDING",
             "notes":"Soporte","items":[{"description":"Soporte mensual","quantity":1,"unitPrice":60000}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private InvoiceService invoiceService;

    private Cookie user() {
        return accessCookie(jwtService, "user@ejemplo.com", Role.USER);
    }

    private Cookie admin() {
        return accessCookie(jwtService, "admin@ejemplo.com", Role.ADMIN);
    }

    private static InvoiceDto invoice() {
        return new InvoiceDto(7L, "FAC-2026-0007", 1L, "Ana Pérez",
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20), InvoiceStatus.PENDING, "Soporte",
                List.of(new InvoiceItemDto(1, "Soporte mensual", new BigDecimal("1.00"),
                        new BigDecimal("60000.00"), new BigDecimal("60000.00"))),
                new BigDecimal("60000.00"), new BigDecimal("11400.00"), new BigDecimal("71400.00"),
                LocalDateTime.of(2026, 11, 20, 10, 0), null);
    }

    @Test
    void sinCookieResponde401() throws Exception {
        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(invoiceService);
    }

    @Test
    void userPuedeListarConFiltrosYOrdenPorDefecto() throws Exception {
        when(invoiceService.findPage(any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(invoice()), invocation.getArgument(1), 1));

        mockMvc.perform(get("/api/invoices").cookie(user())
                        .param("search", "fac").param("customerId", "1").param("status", "PENDING")
                        .param("issuedFrom", "2026-11-01").param("issuedTo", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].invoiceNumber").value("FAC-2026-0007"))
                .andExpect(jsonPath("$.content[0].customerName").value("Ana Pérez"))
                .andExpect(jsonPath("$.content[0].items[0].lineTotal").value(60000.00))
                .andExpect(jsonPath("$.content[0].total").value(71400.00))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(invoiceService).findPage(eq(new InvoiceFilter("fac", 1L, InvoiceStatus.PENDING,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))), captor.capture());
        assertThat(captor.getValue().getSort()).containsExactly(Sort.Order.desc("issueDate"));
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void unEstadoInexistenteResponde400PorCampo() throws Exception {
        mockMvc.perform(get("/api/invoices").param("status", "PAGADA").cookie(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("El valor 'PAGADA' no tiene un formato válido"));
        verifyNoInteractions(invoiceService);
    }

    @Test
    void userPuedeVerElDetalleY404SiNoExiste() throws Exception {
        when(invoiceService.findById(7L)).thenReturn(invoice());
        when(invoiceService.findById(99L)).thenThrow(new ResourceNotFoundException("Invoice", 99L));

        mockMvc.perform(get("/api/invoices/7").cookie(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lineNumber").value(1))
                .andExpect(jsonPath("$.tax").value(11400.00))
                .andExpect(jsonPath("$.updatedAt").isEmpty());
        mockMvc.perform(get("/api/invoices/99").cookie(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Invoice con id 99 no encontrado"));
    }

    @Test
    void userNoPuedeCrearEditarNiBorrar() throws Exception {
        mockMvc.perform(withCsrf(post("/api/invoices"), user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        mockMvc.perform(withCsrf(put("/api/invoices/7"), user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(withCsrf(delete("/api/invoices/7"), user()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(invoiceService);
    }

    @Test
    void adminSinTokenCsrfResponde403() throws Exception {
        mockMvc.perform(post("/api/invoices").cookie(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Token CSRF inválido"));
        verifyNoInteractions(invoiceService);
    }

    @Test
    void adminCreaConLocationEIgnoraLosCamposDeSoloLectura() throws Exception {
        when(invoiceService.create(any())).thenReturn(invoice());
        String body = BODY.replace("\"customerId\":1,",
                "\"customerId\":1,\"invoiceNumber\":\"FAC-1999-9999\",\"customerName\":\"Otro\",\"total\":1,\"subtotal\":1,\"tax\":1,");

        mockMvc.perform(withCsrf(post("/api/invoices"), admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/invoices/7"))
                .andExpect(jsonPath("$.invoiceNumber").value("FAC-2026-0007"));

        ArgumentCaptor<InvoiceDto> captor = ArgumentCaptor.forClass(InvoiceDto.class);
        verify(invoiceService).create(captor.capture());
        InvoiceDto sent = captor.getValue();
        assertThat(sent.invoiceNumber()).isNull();
        assertThat(sent.customerName()).isNull();
        assertThat(sent.subtotal()).isNull();
        assertThat(sent.tax()).isNull();
        assertThat(sent.total()).isNull();
        assertThat(sent.items()).singleElement()
                .satisfies(item -> assertThat(item.description()).isEqualTo("Soporte mensual"));
    }

    @Test
    void laValidacionIncluyeLosErroresDeCadaLinea() throws Exception {
        String body = """
                {"issueDate":"2026-11-20","notes":"%s",
                 "items":[{"description":"Ok","quantity":1,"unitPrice":10},
                          {"description":" ","quantity":0,"unitPrice":-1}]}
                """.formatted("x".repeat(256));

        mockMvc.perform(withCsrf(post("/api/invoices"), admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerId").value("El cliente es obligatorio"))
                .andExpect(jsonPath("$.errors.notes").value("Las notas no pueden superar los 255 caracteres"))
                .andExpect(jsonPath("$.errors['items[1].description']").value("La descripción es obligatoria"))
                .andExpect(jsonPath("$.errors['items[1].quantity']").value("La cantidad debe ser mayor que 0"))
                .andExpect(jsonPath("$.errors['items[1].unitPrice']").value("El precio unitario no puede ser negativo"));
        verifyNoInteractions(invoiceService);
    }

    @Test
    void unaFacturaSinLineasResponde400() throws Exception {
        String body = """
                {"customerId":1,"issueDate":"2026-11-20","items":[]}
                """;

        mockMvc.perform(withCsrf(post("/api/invoices"), admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").value("La factura debe tener al menos una línea"));
    }

    @Test
    void unVencimientoAnteriorResponde400ConElMensajeDelService() throws Exception {
        when(invoiceService.update(eq(7L), any()))
                .thenThrow(new IllegalArgumentException("La fecha de vencimiento (2026-11-01) no puede ser anterior a la fecha de emisión (2026-11-20)"));

        mockMvc.perform(withCsrf(put("/api/invoices/7"), admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida"));
    }

    @Test
    void adminEditaYBorra() throws Exception {
        when(invoiceService.update(eq(7L), any())).thenReturn(invoice());

        mockMvc.perform(withCsrf(put("/api/invoices/7"), admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
        mockMvc.perform(withCsrf(delete("/api/invoices/7"), admin()))
                .andExpect(status().isNoContent());
        verify(invoiceService).delete(7L);
    }

    @Test
    void unOrdenNoPermitidoResponde400() throws Exception {
        when(invoiceService.findPage(any(), any()))
                .thenThrow(new IllegalArgumentException("No se puede ordenar por 'notes'. Campos permitidos: id"));

        mockMvc.perform(get("/api/invoices").param("sort", "notes").cookie(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No se puede ordenar por 'notes'. Campos permitidos: id"));
    }
}
