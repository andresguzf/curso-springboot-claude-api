package com.andres.course.claude.springboot.springapi.app.converters;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Listado de clientes en Excel (.xlsx): encabezado con estilo, fila fija,
 * autofiltro y fechas como celdas de fecha reales. Solo exporta el listado.
 */
public class CustomerExcelHttpMessageConverter extends AbstractCustomerHttpMessageConverter {

    private static final String[] HEADERS = {
            "ID", "Nombre", "Apellido", "Email", "Teléfono", "Dirección", "Ciudad", "Fecha de nacimiento", "Estado"
    };
    // Ancho de cada columna en caracteres (POI usa 1/256 de carácter).
    private static final int[] WIDTHS = {6, 14, 16, 32, 18, 26, 16, 20, 11};

    public CustomerExcelHttpMessageConverter() {
        super(ExportMediaTypes.XLSX, "xlsx", false, false);
    }

    @Override
    protected void writeList(List<CustomerDto> customers, OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Clientes");
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = bodyStyle(workbook);
            CellStyle dateStyle = bodyStyle(workbook);
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd-mm-yyyy"));
            dateStyle.setAlignment(HorizontalAlignment.CENTER);
            CellStyle centerStyle = bodyStyle(workbook);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }

            int rowIndex = 1;
            for (CustomerDto c : customers) {
                Row row = sheet.createRow(rowIndex++);
                Cell id = row.createCell(0);
                id.setCellValue(c.id());
                id.setCellStyle(centerStyle);
                text(row, 1, c.name(), textStyle);
                text(row, 2, c.lastName(), textStyle);
                text(row, 3, c.email(), textStyle);
                text(row, 4, c.phone(), textStyle);
                text(row, 5, c.address(), textStyle);
                text(row, 6, c.city(), textStyle);
                Cell birthDate = row.createCell(7);
                if (c.birthDate() != null) {
                    birthDate.setCellValue(c.birthDate());
                }
                birthDate.setCellStyle(dateStyle);
                text(row, 8, activeLabel(c.active()), centerStyle);
            }

            sheet.createFreezePane(0, 1); // el encabezado queda fijo al hacer scroll
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowIndex - 1, 0), 0, HEADERS.length - 1));
            workbook.write(out);
        }
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(nullToEmpty(value));
        cell.setCellStyle(style);
    }

    private static CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}
