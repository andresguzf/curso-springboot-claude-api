package com.andres.course.claude.springboot.springapi.app.converters;

import org.springframework.http.MediaType;

/**
 * Media types de exportación que no trae {@link MediaType}. Las constantes
 * {@code *_VALUE} se usan en {@code @GetMapping(produces = ...)}.
 */
public final class ExportMediaTypes {

    public static final String TEXT_CSV_VALUE = "text/csv";
    public static final MediaType TEXT_CSV = MediaType.parseMediaType(TEXT_CSV_VALUE);

    public static final String XLSX_VALUE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final MediaType XLSX = MediaType.parseMediaType(XLSX_VALUE);

    private ExportMediaTypes() {
    }
}
