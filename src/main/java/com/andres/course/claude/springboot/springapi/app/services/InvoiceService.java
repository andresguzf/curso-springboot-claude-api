package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceFilter;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceStatsDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InvoiceService {

    /**
     * Página de facturas que cumplen el filtro, con su cliente y sus líneas.
     *
     * @throws IllegalArgumentException si el orden no está permitido o el rango de fechas está invertido (400)
     */
    Page<InvoiceDto> findPage(InvoiceFilter filter, Pageable pageable);

    /** @throws com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException (404) */
    InvoiceDto findById(Long id);

    /**
     * Crea la factura: asigna el número, numera las líneas y calcula los importes.
     *
     * @throws com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException si el cliente no existe (404)
     * @throws IllegalArgumentException si el vencimiento es anterior a la emisión o el total supera el máximo (400)
     */
    InvoiceDto create(InvoiceDto dto);

    /**
     * Reemplaza fechas, estado, notas y líneas, y recalcula los importes. El número no cambia.
     *
     * @throws com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException si la factura o el cliente no existen (404)
     * @throws IllegalArgumentException si el vencimiento es anterior a la emisión o el total supera el máximo (400)
     */
    InvoiceDto update(Long id, InvoiceDto dto);

    /** Elimina la factura y sus líneas. @throws com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException (404) */
    void delete(Long id);

    /** Estadísticas de facturación del resumen: totales, por estado, últimos 12 meses y top 5 clientes. */
    InvoiceStatsDto stats();
}
