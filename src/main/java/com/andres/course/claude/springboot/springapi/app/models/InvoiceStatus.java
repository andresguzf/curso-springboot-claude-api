package com.andres.course.claude.springboot.springapi.app.models;

/**
 * Estado de una factura. Se guarda como texto en la columna status (varchar(20)), que en
 * PostgreSQL tiene un CHECK con estos mismos tres valores.
 */
public enum InvoiceStatus {
    /** Emitida y aún sin pagar (valor por defecto). */
    PENDING,
    /** Pagada. */
    PAID,
    /** Anulada. */
    CANCELLED
}
