package sisa.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfExportStrategy implements ReportExportStrategy {

    @Override
    public byte[] export(ReportData data) {
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 32, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Image logo = loadLogo();
            if (logo != null) {
                logo.scaleToFit(170, 57);
                logo.setAlignment(Image.ALIGN_LEFT);
                document.add(logo);
                document.add(new Paragraph(" "));
            }

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            document.add(new Paragraph(data.title(), titleFont));
            document.add(new Paragraph(" "));

            int columnCount = Math.max(1, data.columns().size());
            PdfPTable table = new PdfPTable(columnCount);
            table.setWidthPercentage(100);

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            for (String column : data.columns()) {
                PdfPCell cell = new PdfPCell(new Phrase(column, headerFont));
                cell.setBackgroundColor(new Color(0x11, 0x35, 0x6F));
                cell.setPadding(6);
                table.addCell(cell);
            }

            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            for (java.util.List<String> row : data.rows()) {
                for (String cellValue : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(cellValue == null ? "" : cellValue, bodyFont));
                    cell.setPadding(5);
                    table.addCell(cell);
                }
            }
            if (data.rows().isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No data for this report.", bodyFont));
                empty.setColspan(columnCount);
                empty.setPadding(8);
                table.addCell(empty);
            }

            document.add(table);
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate PDF report", e);
        } finally {
            if (document.isOpen()) document.close();
        }
        return out.toByteArray();
    }

    /** The SISA logo from the app's static images; null (no logo) if it can't be read. */
    private static Image loadLogo() {
        try (InputStream in = PdfExportStrategy.class.getResourceAsStream("/static/images/sisa-logo.png")) {
            return in == null ? null : Image.getInstance(in.readAllBytes());
        } catch (IOException | BadElementException e) {
            return null;
        }
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public String fileExtension() {
        return "pdf";
    }
}
