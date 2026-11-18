package com.andres.course.claude.springboot.springapi.app.converters;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.http.MediaType;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Listado y ficha de cliente en PDF, con un diseño minimalista: tipografía
 * Helvetica, una paleta de grises con un color de acento, tablas con líneas
 * finas y pie de página con fecha de generación y número de página.
 */
public class CustomerPdfHttpMessageConverter extends AbstractCustomerHttpMessageConverter {

    // Paleta
    private static final Color INK = new Color(0x11, 0x18, 0x27);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color ACCENT = new Color(0x25, 0x63, 0xEB);
    private static final Color LINE = new Color(0xE5, 0xE7, 0xEB);
    private static final Color SOFT = new Color(0xF9, 0xFA, 0xFB);
    private static final Color HEADER_BG = new Color(0x1F, 0x29, 0x37);
    private static final Color GREEN = new Color(0x04, 0x78, 0x57);
    private static final Color GREEN_BG = new Color(0xEC, 0xFD, 0xF5);
    private static final Color RED = new Color(0xB9, 0x1C, 0x1C);
    private static final Color RED_BG = new Color(0xFE, 0xF2, 0xF2);

    private static final DateTimeFormatter GENERATED_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final String[] LIST_HEADERS = {
            "ID", "Nombre", "Apellido", "Email", "Teléfono", "Dirección", "Ciudad", "Nacimiento", "Estado"
    };
    private static final float[] LIST_WIDTHS = {4, 9, 10, 20, 11, 16, 10, 9, 7};

    public CustomerPdfHttpMessageConverter() {
        super(MediaType.APPLICATION_PDF, "pdf", true, true);
    }

    @Override
    protected void writeList(List<CustomerDto> customers, OutputStream out) throws IOException {
        String generatedAt = GENERATED_FORMAT.format(LocalDateTime.now());
        // A4 horizontal con ancho y alto intercambiados (PageSize.A4.rotate() agrega una marca de
        // rotación que algunos visores aplican de nuevo y muestran la página de lado).
        Document document = new Document(new Rectangle(PageSize.A4.getHeight(), PageSize.A4.getWidth()), 36, 36, 36, 48);
        PdfWriter writer = open(document, out, "Listado de clientes", generatedAt);

        document.add(new Paragraph("Listado de clientes", font(20, Font.BOLD, INK)));
        Paragraph subtitle = new Paragraph(customers.size() + " clientes  ·  generado el " + generatedAt,
                font(9, Font.NORMAL, MUTED));
        subtitle.setSpacingAfter(6);
        document.add(subtitle);
        document.add(accentRule());

        PdfPTable table = new PdfPTable(LIST_WIDTHS);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14);
        table.setHeaderRows(1); // el encabezado se repite en cada página

        for (String header : LIST_HEADERS) {
            PdfPCell cell = cell(header, font(8.5f, Font.BOLD, Color.WHITE));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPaddingTop(7);
            cell.setPaddingBottom(7);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }

        Font body = font(8.5f, Font.NORMAL, INK);
        int index = 0;
        for (CustomerDto c : customers) {
            Color background = (index++ % 2 == 0) ? Color.WHITE : SOFT;
            String[] values = {
                    String.valueOf(c.id()), nullToEmpty(c.name()), nullToEmpty(c.lastName()), nullToEmpty(c.email()),
                    nullToEmpty(c.phone()), nullToEmpty(c.address()), nullToEmpty(c.city()), format(c.birthDate())
            };
            for (String value : values) {
                table.addCell(bodyCell(value, body, background));
            }
            boolean active = Boolean.TRUE.equals(c.active());
            table.addCell(bodyCell(activeLabel(c.active()), font(8.5f, Font.BOLD, active ? GREEN : RED), background));
        }

        document.add(table);
        document.close();
        writer.close();
    }

    @Override
    protected void writeDetail(CustomerDto customer, OutputStream out) throws IOException {
        String generatedAt = GENERATED_FORMAT.format(LocalDateTime.now());
        Document document = new Document(PageSize.A4, 56, 56, 56, 56);
        String fullName = nullToEmpty(customer.name()) + " " + nullToEmpty(customer.lastName());
        PdfWriter writer = open(document, out, "Ficha de cliente: " + fullName.trim(), generatedAt);

        document.add(new Paragraph("FICHA DE CLIENTE", font(9, Font.BOLD, ACCENT)));
        Paragraph name = new Paragraph(fullName.trim(), font(24, Font.BOLD, INK));
        name.setSpacingBefore(4);
        document.add(name);

        // Fila bajo el nombre: número de cliente a la izquierda y estado como "badge" a la derecha.
        PdfPTable meta = new PdfPTable(new float[]{70, 30});
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(6);
        meta.setSpacingAfter(10);
        PdfPCell idCell = cell("Cliente #" + customer.id(), font(10, Font.NORMAL, MUTED));
        idCell.setBorder(Rectangle.NO_BORDER);
        idCell.setPaddingLeft(0);
        meta.addCell(idCell);
        meta.addCell(statusBadge(Boolean.TRUE.equals(customer.active())));
        document.add(meta);
        document.add(accentRule());

        PdfPTable details = new PdfPTable(new float[]{32, 68});
        details.setWidthPercentage(100);
        details.setSpacingBefore(18);
        addDetailRow(details, "ID", String.valueOf(customer.id()));
        addDetailRow(details, "Nombre", customer.name());
        addDetailRow(details, "Apellido", customer.lastName());
        addDetailRow(details, "Email", customer.email());
        addDetailRow(details, "Teléfono", customer.phone());
        addDetailRow(details, "Dirección", customer.address());
        addDetailRow(details, "Ciudad", customer.city());
        addDetailRow(details, "Fecha de nacimiento", birthDateWithAge(customer.birthDate()));
        addDetailRow(details, "Estado", activeLabel(customer.active()));
        document.add(details);

        document.close();
        writer.close();
    }

    // ---------------------------------------------------------------- helpers

    /** Abre el documento con metadatos y el pie de página; no cierra el stream de la respuesta. */
    private static PdfWriter open(Document document, OutputStream out, String title, String generatedAt) {
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setCloseStream(false);
        writer.setPageEvent(new Footer(generatedAt));
        document.addTitle(title);
        document.addCreator("3-spring-api");
        document.open();
        return writer;
    }

    private static void addDetailRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = cell(label, font(9.5f, Font.BOLD, MUTED));
        labelCell.setBackgroundColor(SOFT);
        styleDetailCell(labelCell);
        table.addCell(labelCell);

        String text = (value == null || value.isBlank()) ? "—" : value;
        PdfPCell valueCell = cell(text, font(10.5f, Font.NORMAL, INK));
        styleDetailCell(valueCell);
        table.addCell(valueCell);
    }

    private static void styleDetailCell(PdfPCell cell) {
        cell.setBorder(Rectangle.BOTTOM | Rectangle.TOP);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.6f);
        cell.setPaddingTop(9);
        cell.setPaddingBottom(9);
        cell.setPaddingLeft(10);
    }

    private static PdfPCell statusBadge(boolean active) {
        PdfPTable badge = new PdfPTable(1);
        badge.setTotalWidth(70);
        badge.setLockedWidth(true);
        badge.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell label = cell(active ? "ACTIVO" : "INACTIVO", font(8, Font.BOLD, active ? GREEN : RED));
        label.setBackgroundColor(active ? GREEN_BG : RED_BG);
        label.setBorderColor(active ? GREEN : RED);
        label.setBorderWidth(0.6f);
        label.setHorizontalAlignment(Element.ALIGN_CENTER);
        label.setPaddingTop(4);
        label.setPaddingBottom(5);
        badge.addCell(label);

        PdfPCell container = new PdfPCell(badge);
        container.setBorder(Rectangle.NO_BORDER);
        container.setHorizontalAlignment(Element.ALIGN_RIGHT);
        container.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return container;
    }

    /** Línea fina del color de acento, usada como separador bajo los títulos. */
    private static PdfPTable accentRule() {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(ACCENT);
        cell.setBorderWidthBottom(1.2f);
        cell.setFixedHeight(2);
        rule.addCell(cell);
        return rule;
    }

    private static PdfPCell bodyCell(String text, Font font, Color background) {
        PdfPCell cell = cell(text, font);
        cell.setBackgroundColor(background);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(6);
        return cell;
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingLeft(6);
        cell.setPaddingRight(6);
        return cell;
    }

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private static String birthDateWithAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        return format(birthDate) + "  (" + age + " años)";
    }

    /** Pie de página: línea fina, aplicación y fecha a la izquierda, número de página a la derecha. */
    private static final class Footer extends PdfPageEventHelper {

        private final String generatedAt;

        private Footer(String generatedAt) {
            this.generatedAt = generatedAt;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float y = document.bottom() - 18;

            canvas.saveState();
            canvas.setColorStroke(LINE);
            canvas.setLineWidth(0.5f);
            canvas.moveTo(document.left(), y + 10);
            canvas.lineTo(document.right(), y + 10);
            canvas.stroke();
            canvas.restoreState();

            Font footerFont = font(7.5f, Font.NORMAL, MUTED);
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                    new Phrase("3-spring-api  ·  generado el " + generatedAt, footerFont), document.left(), y, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), footerFont), document.right(), y, 0);
        }
    }
}
