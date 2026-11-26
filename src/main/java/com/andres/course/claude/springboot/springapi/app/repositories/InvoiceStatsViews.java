package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;

import java.math.BigDecimal;

/**
 * Proyecciones (interfaces) de las consultas de agregación de {@link InvoiceRepository}. Spring Data
 * llena cada getter con la columna del mismo alias ({@code ... as status}). Solo viven entre el
 * repositorio y el service, que arma el {@code InvoiceStatsDto}.
 */
public final class InvoiceStatsViews {

    private InvoiceStatsViews() {
    }

    /** Cantidad e importe por estado. */
    public interface StatusTotalView {
        InvoiceStatus getStatus();

        long getCount();

        BigDecimal getTotal();
    }

    /** Importe de un estado en un mes (año y mes de la fecha de emisión). */
    public interface MonthStatusTotalView {
        int getYear();

        int getMonth();

        InvoiceStatus getStatus();

        BigDecimal getTotal();
    }

    /** Cantidad e importe de un conjunto de facturas (las vencidas). */
    public interface CountTotalView {
        long getCount();

        BigDecimal getTotal();
    }

    /** Facturado por cliente. */
    public interface CustomerTotalView {
        Long getCustomerId();

        String getName();

        String getLastName();

        long getInvoiceCount();

        BigDecimal getTotal();
    }
}
