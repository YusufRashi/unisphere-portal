package com.unisphere.transcript;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.GrayColor;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.unisphere.result.dto.ResultDtos.CourseGrade;
import com.unisphere.result.dto.ResultDtos.SemesterRecord;
import com.unisphere.transcript.dto.TranscriptDtos.Transcript;
import com.unisphere.transcript.dto.TranscriptDtos.TranscriptStudent;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link Transcript} as an A4 PDF (TRN-3). Every page carries the diagonal "unofficial" watermark,
 * the generation time and the verification reference (TRN-4), drawn by a page event so no page can miss them.
 *
 * <p>Uses the standard Helvetica font (Windows-1252): names outside that character set would not render;
 * embedding a Unicode font is the fix if that is ever needed.
 */
@Component
public class TranscriptPdfRenderer {

    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final Color BRAND = new Color(30, 41, 99);
    private static final Color RULE = new Color(203, 213, 225);
    private static final Color MUTED = new Color(71, 85, 105);
    private static final Color HEADER_FILL = new Color(241, 245, 249);

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 11, Font.NORMAL, MUTED);
    private static final Font LABEL = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font VALUE = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font HEADING = new Font(Font.HELVETICA, 11, Font.BOLD, BRAND);
    private static final Font TH = new Font(Font.HELVETICA, 8, Font.BOLD, MUTED);
    private static final Font TD = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font TD_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font NOTE = new Font(Font.HELVETICA, 8, Font.ITALIC, MUTED);

    public byte[] render(Transcript t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 56, 64);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new EveryPage(t));
        document.addTitle(t.title() + " — " + t.student().studentNumber());
        document.addCreator(t.universityName());
        document.open();

        document.add(new Paragraph(t.universityName(), TITLE));
        Paragraph subtitle = new Paragraph(t.title(), SUBTITLE);
        subtitle.setSpacingAfter(14);
        document.add(subtitle);
        document.add(studentBlock(t.student()));

        boolean anyRepeated = false;
        if (t.semesters().isEmpty()) {
            Paragraph none = new Paragraph("No finalized results yet.", NOTE);
            none.setSpacingBefore(16);
            document.add(none);
        }
        for (SemesterRecord s : t.semesters()) {
            Paragraph heading = new Paragraph(s.semester().name(), HEADING);
            heading.setSpacingBefore(16);
            heading.setSpacingAfter(4);
            heading.setKeepTogether(true);
            document.add(heading);
            document.add(semesterTable(s));
            anyRepeated |= s.courses().stream().anyMatch(c -> !c.countsTowardCgpa());
        }

        document.add(summary(t));
        Paragraph key = new Paragraph(
                "GPA = total of (credits × grade points) ÷ total credits, rounded half-up to two decimals. "
                        + (anyRepeated ? "* Earlier attempt of a repeated course: shown for completeness, excluded from the CGPA. " : "")
                        + "Only finalized results are listed.", NOTE);
        key.setSpacingBefore(10);
        document.add(key);
        document.close();
        return out.toByteArray();
    }

    private static PdfPTable studentBlock(TranscriptStudent s) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        addField(table, "Student name", s.name());
        addField(table, "Student number", s.studentNumber());
        addField(table, "Status", capitalize(s.status()));
        addField(table, "Program", s.programCode() + " — " + s.programName());
        addField(table, "Department", s.departmentName());
        addField(table, "Admitted", s.admissionDate().format(DATE));
        return table;
    }

    private static void addField(PdfPTable table, String label, String value) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label.toUpperCase(Locale.ROOT) + "\n", LABEL));
        phrase.add(new Chunk(value, VALUE));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(8);
        table.addCell(cell);
    }

    private static PdfPTable semesterTable(SemesterRecord s) {
        PdfPTable table = new PdfPTable(new float[] {14, 46, 10, 10, 10, 10});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitLate(false);
        for (String h : new String[] {"CODE", "COURSE TITLE", "CREDITS", "MARK", "GRADE", "POINTS"}) {
            PdfPCell cell = cell(h, TH, h.equals("CODE") || h.equals("COURSE TITLE") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            cell.setBackgroundColor(HEADER_FILL);
            table.addCell(cell);
        }
        for (CourseGrade c : s.courses()) {
            table.addCell(cell(c.courseCode() + (c.countsTowardCgpa() ? "" : " *"), TD, Element.ALIGN_LEFT));
            table.addCell(cell(c.courseTitle(), TD, Element.ALIGN_LEFT));
            table.addCell(cell(String.valueOf(c.credits()), TD, Element.ALIGN_RIGHT));
            table.addCell(cell(plain(c.totalMark()), TD, Element.ALIGN_RIGHT));
            table.addCell(cell(c.grade() + (c.passed() ? "" : " (F)"), TD_BOLD, Element.ALIGN_RIGHT));
            table.addCell(cell(two(c.gradePoints()), TD, Element.ALIGN_RIGHT));
        }
        PdfPCell footer = cell("Semester GPA " + two(s.gpa()) + "    Credits earned " + s.creditsEarned() + " of " + s.creditsAttempted(),
                TD_BOLD, Element.ALIGN_RIGHT);
        footer.setColspan(6);
        footer.setBorderWidthTop(0.75f);
        table.addCell(footer);
        return table;
    }

    private static PdfPTable summary(Transcript t) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingBefore(18);
        addField(table, "Cumulative GPA", two(t.cgpa()));
        addField(table, "Credits earned", String.valueOf(t.creditsEarned()));
        addField(table, "Credits counted", String.valueOf(t.creditsAttempted()));
        for (PdfPCell cell : table.getRow(0).getCells()) {
            cell.setBorder(Rectangle.TOP);
            cell.setBorderColor(BRAND);
            cell.setBorderWidth(1f);
            cell.setPaddingTop(8);
        }
        return table;
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setPadding(4);
        cell.setPaddingBottom(5);
        return cell;
    }

    private static String two(BigDecimal value) {
        return value == null ? "—" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Watermark under the content and a footer, on every page. */
    private static final class EveryPage extends PdfPageEventHelper {

        private final Transcript transcript;
        private final BaseFont font;

        EveryPage(Transcript transcript) {
            this.transcript = transcript;
            try {
                this.font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException("Built-in Helvetica is always available", e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();

            PdfContentByte under = writer.getDirectContentUnder();
            under.saveState();
            PdfGState faint = new PdfGState();
            faint.setFillOpacity(0.12f);
            under.setGState(faint);
            under.beginText();
            under.setFontAndSize(font, 22);
            under.setColorFill(new GrayColor(0.2f));
            under.showTextAligned(Element.ALIGN_CENTER, transcript.watermark(), page.getWidth() / 2, page.getHeight() / 2, 55);
            under.endText();
            under.restoreState();

            PdfContentByte over = writer.getDirectContent();
            over.saveState();
            over.setColorStroke(RULE);
            over.setLineWidth(0.5f);
            over.moveTo(document.left(), 44);
            over.lineTo(document.right(), 44);
            over.stroke();
            over.beginText();
            over.setFontAndSize(font, 7.5f);
            over.setColorFill(MUTED);
            over.showTextAligned(Element.ALIGN_LEFT, transcript.watermark(), document.left(), 32, 0);
            over.showTextAligned(Element.ALIGN_LEFT, "Generated " + GENERATED.format(transcript.generatedAt())
                    + "   ·   Verification reference " + transcript.reference(), document.left(), 22, 0);
            over.showTextAligned(Element.ALIGN_RIGHT, transcript.student().studentNumber() + "   ·   Page " + writer.getPageNumber(),
                    document.right(), 22, 0);
            over.endText();
            over.restoreState();
        }
    }
}
