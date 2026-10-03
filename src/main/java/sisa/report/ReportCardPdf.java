package sisa.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import sisa.entity.AcademicTerm;
import sisa.entity.Grade;
import sisa.entity.Student;
import sisa.service.MarksEntryService;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** A one-term student report card as a printable A4 PDF (SISA branding, results, summary, signatures). */
@Component
public class ReportCardPdf {

    private static final Color NAVY = new Color(0x11, 0x35, 0x6F);
    private static final Color ORANGE = new Color(0xF1, 0x85, 0x1F);
    private static final Color SOFT = new Color(0xEE, 0xF2, 0xF8);
    private static final Color LINE = new Color(0xDC, 0xE3, 0xF0);
    private static final Color MUTED = new Color(0x5B, 0x66, 0x78);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private static final Font TITLE = font(FontFactory.HELVETICA_BOLD, 18, NAVY);
    private static final Font SUBTITLE = font(FontFactory.HELVETICA, 10, MUTED);
    private static final Font LABEL = font(FontFactory.HELVETICA, 8.5f, MUTED);
    private static final Font VALUE = font(FontFactory.HELVETICA_BOLD, 10.5f, NAVY);
    private static final Font TH = font(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TD = font(FontFactory.HELVETICA, 9.5f, new Color(0x27, 0x32, 0x46));
    private static final Font TD_BOLD = font(FontFactory.HELVETICA_BOLD, 9.5f, NAVY);
    private static final Font BIG = font(FontFactory.HELVETICA_BOLD, 20, NAVY);
    private static final Font SECTION = font(FontFactory.HELVETICA_BOLD, 11, NAVY);
    private static final Font SMALL = font(FontFactory.HELVETICA, 8, MUTED);

    private static Font font(String name, float size, Color color) {
        return FontFactory.getFont(name, size, color);
    }

    public byte[] render(MarksEntryService.ReportCard card) {
        Document doc = new Document(PageSize.A4, 40, 40, 36, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer());
            doc.open();
            header(doc, card);
            studentDetails(doc, card);
            results(doc, card);
            summary(doc, card);
            gradeScale(doc);
            signatures(doc);
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate the report card PDF", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    /** "ReportCard_S2600001_Term-3-2026.pdf" */
    public static String fileName(MarksEntryService.ReportCard card) {
        String term = card.term() != null ? card.term().getName() : "All-terms";
        return "ReportCard_" + card.student().getStudentId() + "_" + term.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("-+$", "") + ".pdf";
    }

    // ---------- sections ----------

    private void header(Document doc, MarksEntryService.ReportCard card) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{1.2f, 1.8f});
        t.setWidthPercentage(100);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image logo = loadLogo();
        if (logo != null) {
            logo.scaleToFit(150, 50);
            logoCell.addElement(logo);
        } else {
            logoCell.addElement(new Paragraph("SISA", TITLE));
        }
        t.addCell(logoCell);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph title = new Paragraph("Student Report Card", TITLE);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        Paragraph term = new Paragraph(termName(card.term()) + termDates(card.term()), SUBTITLE);
        term.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(term);
        t.addCell(right);
        doc.add(t);

        // navy rule with an orange accent
        PdfPTable rule = new PdfPTable(new float[]{1f, 5f});
        rule.setWidthPercentage(100);
        rule.setSpacingBefore(10);
        rule.setSpacingAfter(16);
        rule.addCell(bar(ORANGE));
        rule.addCell(bar(NAVY));
        doc.add(rule);
    }

    private void studentDetails(Document doc, MarksEntryService.ReportCard card) throws DocumentException {
        Student s = card.student();
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingAfter(18);
        t.addCell(detail("STUDENT NAME", s.getUser() != null ? s.getUser().getFullName() : "—"));
        t.addCell(detail("STUDENT ID", s.getStudentId()));
        t.addCell(detail("CLASS", s.getClassName() != null ? s.getClassName() : "—"));
        t.addCell(detail("TERM", termName(card.term())));
        doc.add(t);
    }

    private void results(Document doc, MarksEntryService.ReportCard card) throws DocumentException {
        doc.add(sectionTitle("Results"));
        PdfPTable t = new PdfPTable(new float[]{2.2f, 2.6f, 1.6f, 1.4f, 1f, 0.9f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setSpacingAfter(16);
        for (String h : new String[]{"Subject", "Assessment", "Date", "Marks", "%", "Grade"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, TH));
            c.setBackgroundColor(NAVY);
            c.setBorderColor(NAVY);
            c.setPadding(7);
            if (!h.equals("Subject") && !h.equals("Assessment")) c.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(c);
        }
        if (card.entries().isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("No exam results have been recorded for this term yet.", TD));
            c.setColspan(6);
            c.setPadding(12);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setBorderColor(LINE);
            t.addCell(c);
        }
        int i = 0;
        for (MarksEntryService.ReportCardEntry e : card.entries()) {
            Color bg = (i++ % 2 == 0) ? Color.WHITE : SOFT;
            double pct = e.maxMarks() <= 0 ? 0 : e.marksObtained() * 100.0 / e.maxMarks();
            t.addCell(row(e.subject(), TD_BOLD, bg, Element.ALIGN_LEFT));
            t.addCell(row(e.examName(), TD, bg, Element.ALIGN_LEFT));
            t.addCell(row(e.examDate().format(DATE), TD, bg, Element.ALIGN_CENTER));
            t.addCell(row(num(e.marksObtained()) + " / " + num(e.maxMarks()), TD, bg, Element.ALIGN_CENTER));
            t.addCell(row(String.format("%.0f%%", pct), TD, bg, Element.ALIGN_CENTER));
            PdfPCell g = row(e.grade().name(), font(FontFactory.HELVETICA_BOLD, 10, gradeColor(e.grade())), bg, Element.ALIGN_CENTER);
            t.addCell(g);
        }
        doc.add(t);
    }

    private void summary(Document doc, MarksEntryService.ReportCard card) throws DocumentException {
        doc.add(sectionTitle("Summary"));
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setSpacingAfter(14);
        boolean any = !card.entries().isEmpty();
        t.addCell(stat(any ? num(card.totalMarks()) : "—", "Total marks (all subjects)"));
        t.addCell(stat(card.averageMark() != null ? String.format("%.1f", card.averageMark()) : "—", "Average mark"));
        t.addCell(stat(String.valueOf(card.entries().size()), "Exams recorded"));
        doc.add(t);
    }

    private void gradeScale(Document doc) throws DocumentException {
        Paragraph p = new Paragraph("Grading scale:  A 75–100%   ·   B 60–74%   ·   C 50–59%   ·   D 40–49%   ·   F below 40%", SMALL);
        p.setSpacingAfter(46);
        doc.add(p);
    }

    private void signatures(Document doc) throws DocumentException {
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        for (String who : new String[]{"Class Teacher", "Principal", "Parent / Guardian"}) {
            PdfPCell c = new PdfPCell(new Phrase(who, SMALL));
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(MUTED);
            c.setPaddingTop(6);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            PdfPTable wrap = new PdfPTable(1);
            wrap.setWidthPercentage(80);
            wrap.addCell(c);
            PdfPCell outer = new PdfPCell(wrap);
            outer.setBorder(Rectangle.NO_BORDER);
            t.addCell(outer);
        }
        doc.add(t);
    }

    // ---------- small helpers ----------

    private static PdfPCell bar(Color color) {
        PdfPCell c = new PdfPCell();
        c.setFixedHeight(3);
        c.setBackgroundColor(color);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static PdfPCell detail(String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(SOFT);
        c.setBorderColor(Color.WHITE);
        c.setBorderWidth(2);
        c.setPadding(9);
        c.addElement(new Paragraph(label, LABEL));
        c.addElement(new Paragraph(value, VALUE));
        return c;
    }

    private static Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, SECTION);
        p.setSpacingAfter(6);
        return p;
    }

    private static PdfPCell row(String text, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(LINE);
        c.setPadding(7);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static PdfPCell stat(String value, String label) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(LINE);
        c.setPadding(10);
        Paragraph v = new Paragraph(value, BIG);
        v.setAlignment(Element.ALIGN_CENTER);
        Paragraph l = new Paragraph(label, SMALL);
        l.setAlignment(Element.ALIGN_CENTER);
        c.addElement(v);
        c.addElement(l);
        return c;
    }

    private static Color gradeColor(Grade g) {
        return switch (g) {
            case A, B -> new Color(0x1F, 0x7A, 0x46);
            case C -> new Color(0xD0, 0x6A, 0x0E);
            case D, F -> new Color(0xB8, 0x41, 0x2A);
        };
    }

    private static String num(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format("%.1f", d);
    }

    private static String termName(AcademicTerm term) {
        return term != null ? term.getName() : "All terms";
    }

    private static String termDates(AcademicTerm term) {
        return term == null ? "" : "  ·  " + term.getStartDate().format(DATE) + " – " + term.getEndDate().format(DATE);
    }

    private static Image loadLogo() {
        try (InputStream in = ReportCardPdf.class.getResourceAsStream("/static/images/sisa-logo.png")) {
            return in == null ? null : Image.getInstance(in.readAllBytes());
        } catch (IOException | BadElementException e) {
            return null;
        }
    }

    /** "Generated by SISA on 4 Oct 2026" on the left and "Page 1" on the right of every page. */
    private static class Footer extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 22;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("Generated by SISA School Information Management System on " + LocalDate.now().format(DATE), SMALL),
                    document.left(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), SMALL), document.right(), y, 0);
        }
    }
}
