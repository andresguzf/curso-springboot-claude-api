package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;

import java.math.BigDecimal;

/**
 * Filas de las consultas de agregación de {@link InvoiceStatsRepository}. Son records porque la Criteria
 * API las arma con {@code cb.construct(...)}, que llama a su constructor: por eso los tipos son los que
 * devuelve la base (Long para count, Integer para extract, BigDecimal para sum). Solo viven entre el
 * repositorio y el service, que arma el {@code InvoiceStatsDto}.
 */
public final class InvoiceStatsViews {

    private InvoiceStatsViews() {
    }

    /** Cantidad e importe por estado. */
    public record StatusTotalView(InvoiceStatus status, Long count, BigDecimal total) {
    }

    /** Importe de un estado en un mes (año y mes de la fecha de emisión). */
    public record MonthStatusTotalView(Integer year, Integer month, InvoiceStatus status, BigDecimal total) {
    }

    /** Cantidad e importe de un conjunto de facturas (las vencidas). */
    public record CountTotalView(Long count, BigDecimal total) {
    }

    /** Facturado por cliente. */
    public record CustomerTotalView(Long customerId, String name, String lastName, Long invoiceCount, BigDecimal total) {
    }
}
