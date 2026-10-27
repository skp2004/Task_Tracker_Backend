package com.tasktracker.service;

import com.tasktracker.entity.Task;
import com.tasktracker.entity.User;
import com.tasktracker.exception.ResourceNotFoundException;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter ROW_DATE     = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final float MARGIN      = 35f;
    private static final float PAGE_WIDTH  = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_W   = PAGE_WIDTH - 2 * MARGIN;

    // 3-column layout: DATE (75pt) | TITLE (165pt) | DESCRIPTION (275pt)
    private static final float COL_DATE  = MARGIN + 5;
    private static final float COL_TITLE = MARGIN + 80;
    private static final float COL_DESC  = MARGIN + 245;

    public enum ReportType { YEAR, MONTH, DATE_RANGE }

    @Transactional(readOnly = true)
    public byte[] generateReport(Long userId, ReportType type, Integer year, Integer month,
                                 LocalDate from, LocalDate to) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDate rangeFrom, rangeTo;
        String reportTitle;

        switch (type) {
            case YEAR -> {
                rangeFrom = LocalDate.of(year, 1, 1);
                rangeTo   = LocalDate.of(year, 12, 31);
                reportTitle = "Annual Task Report - " + year;
            }
            case MONTH -> {
                rangeFrom = LocalDate.of(year, month, 1);
                rangeTo   = rangeFrom.withDayOfMonth(rangeFrom.lengthOfMonth());
                reportTitle = "Monthly Task Report - "
                        + rangeFrom.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            }
            default -> {
                rangeFrom   = from;
                rangeTo     = to;
                reportTitle = "Task Report: " + from.format(DISPLAY_DATE) + " to " + to.format(DISPLAY_DATE);
            }
        }

        List<Task> tasks = taskRepository
                .findByUserIdAndTaskDateBetweenOrderByTaskDateAscCreatedAtAsc(userId, rangeFrom, rangeTo);

        return buildPdf(user, reportTitle, rangeFrom, rangeTo, tasks);
    }

    private byte[] buildPdf(User user, String reportTitle,
                             LocalDate from, LocalDate to, List<Task> tasks) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            long total = tasks.size();

            // Use embedded TrueType fonts (Noto Sans) to support full Unicode including
            // Gujarati, Devanagari, Arabic, CJK, etc.  PDType1Font (Helvetica) only
            // covers WinAnsiEncoding (Latin) and will throw IllegalArgumentException
            // for any character outside that range.
            PDType0Font fontBold    = loadFont(doc, "fonts/NotoSans-Bold.ttf");
            PDType0Font fontRegular = loadFont(doc, "fonts/NotoSans-Regular.ttf");
            PDType0Font fontOblique = loadFont(doc, "fonts/NotoSans-Italic.ttf");

            // ── Page 1 ──────────────────────────────────────────────────────
            PDPage firstPage = new PDPage(PDRectangle.A4);
            doc.addPage(firstPage);
            PDPageContentStream[] csRef = { new PDPageContentStream(doc, firstPage) };
            float[] yRef = { PAGE_HEIGHT - MARGIN };

            // ── Header Banner (Dark Indigo) ──────────────────────────────────
            csRef[0].setNonStrokingColor(0.15f, 0.12f, 0.38f);
            csRef[0].addRect(0, PAGE_HEIGHT - 85, PAGE_WIDTH, 85);
            csRef[0].fill();

            // Report Title
            csRef[0].setNonStrokingColor(1f, 1f, 1f);
            csRef[0].beginText();
            csRef[0].setFont(fontBold, 16);
            csRef[0].newLineAtOffset(MARGIN, PAGE_HEIGHT - 32);
            safeShowText(csRef[0], reportTitle);
            csRef[0].endText();

            // Generated info
            csRef[0].beginText();
            csRef[0].setFont(fontRegular, 8.5f);
            csRef[0].newLineAtOffset(MARGIN, PAGE_HEIGHT - 48);
            csRef[0].showText("Generated: " + LocalDate.now().format(DISPLAY_DATE)
                    + "   |   Total Tasks: " + total);
            csRef[0].endText();

            // College Name Header
            String college = user.getCollegeName();
            if (college != null && !college.isBlank()) {
                csRef[0].beginText();
                csRef[0].setFont(fontBold, 12);
                csRef[0].newLineAtOffset(MARGIN, PAGE_HEIGHT - 66);
                String c = sanitizeText(college);
                if (c.length() > 85) c = c.substring(0, 82) + "...";
                safeShowText(csRef[0], c);
                csRef[0].endText();
            }

            yRef[0] = PAGE_HEIGHT - 95;

            // ── User Details Box with Explicit Field Names ────────────────────
            csRef[0].setNonStrokingColor(0.94f, 0.94f, 0.97f);
            csRef[0].addRect(MARGIN, yRef[0] - 32, CONTENT_W, 34);
            csRef[0].fill();

            // Line 1: Name | Email | Phone
            csRef[0].setNonStrokingColor(0.15f, 0.12f, 0.38f);
            csRef[0].beginText();
            csRef[0].setFont(fontBold, 8f);
            csRef[0].newLineAtOffset(MARGIN + 8, yRef[0] - 12);
            String line1 = "Name: " + sanitizeText(user.getFullName())
                    + "   |   Email: " + sanitizeText(user.getEmail())
                    + (user.getPhone() != null && !user.getPhone().isBlank()
                       ? "   |   Phone: " + sanitizeText(user.getPhone()) : "");
            if (line1.length() > 115) line1 = line1.substring(0, 112) + "...";
            safeShowText(csRef[0], line1);
            csRef[0].endText();

            // Line 2: Dept | Designation | College
            csRef[0].beginText();
            csRef[0].setFont(fontRegular, 8f);
            csRef[0].newLineAtOffset(MARGIN + 8, yRef[0] - 25);
            StringBuilder line2Sb = new StringBuilder();
            if (user.getDepartment() != null && !user.getDepartment().isBlank())
                line2Sb.append("Department: ").append(sanitizeText(user.getDepartment())).append("   |   ");
            if (user.getDesignation() != null && !user.getDesignation().isBlank())
                line2Sb.append("Designation: ").append(sanitizeText(user.getDesignation())).append("   |   ");
            if (user.getCollegeName() != null && !user.getCollegeName().isBlank())
                line2Sb.append("College: ").append(sanitizeText(user.getCollegeName()));
            String line2 = line2Sb.toString().replaceAll("   \\|   $", "");
            if (line2.isBlank()) line2 = "User Account Details";
            if (line2.length() > 115) line2 = line2.substring(0, 112) + "...";
            safeShowText(csRef[0], line2);
            csRef[0].endText();

            yRef[0] -= 44;

            // ── Column Header Row ────────────────────────────────────────────
            drawTableHeader(csRef[0], fontBold, yRef[0]);
            yRef[0] -= 18;

            // ── Task Rows (Clean single-line per task, no extra date banner) ──
            boolean alt = false;
            for (Task t : tasks) {
                final float ROW_H = 14f;

                if (yRef[0] < MARGIN + ROW_H + 4) {
                    csRef[0].close();
                    csRef[0] = addNewPage(doc, fontBold, yRef);
                    alt = false;
                }

                // Alternating row background
                if (alt) {
                    csRef[0].setNonStrokingColor(0.96f, 0.96f, 0.98f);
                    csRef[0].addRect(MARGIN, yRef[0] - 2, CONTENT_W, ROW_H);
                    csRef[0].fill();
                }
                alt = !alt;

                // Left accent bar
                csRef[0].setNonStrokingColor(0.35f, 0.28f, 0.75f);
                csRef[0].addRect(MARGIN, yRef[0] - 2, 3, ROW_H);
                csRef[0].fill();

                // DATE column
                csRef[0].setNonStrokingColor(0.3f, 0.3f, 0.3f);
                csRef[0].beginText();
                csRef[0].setFont(fontRegular, 7.5f);
                csRef[0].newLineAtOffset(COL_DATE, yRef[0] + 3);
                csRef[0].showText(t.getTaskDate().format(ROW_DATE));
                csRef[0].endText();

                // TITLE column
                csRef[0].setNonStrokingColor(0f, 0f, 0f);
                csRef[0].beginText();
                csRef[0].setFont(fontBold, 7.5f);
                csRef[0].newLineAtOffset(COL_TITLE, yRef[0] + 3);
                String title = sanitizeText(t.getTitle());
                if (title.length() > 36) title = title.substring(0, 33) + "...";
                safeShowText(csRef[0], title);
                csRef[0].endText();

                // DESCRIPTION column
                if (t.getDescription() != null && !t.getDescription().isBlank()) {
                    String desc = sanitizeText(t.getDescription()).replaceAll("[\\r\\n]+", " ").trim();
                    int maxChars = 72;
                    if (desc.length() > maxChars) desc = desc.substring(0, maxChars - 3) + "...";
                    csRef[0].setNonStrokingColor(0.35f, 0.35f, 0.35f);
                    csRef[0].beginText();
                    csRef[0].setFont(fontOblique, 7f);
                    csRef[0].newLineAtOffset(COL_DESC, yRef[0] + 3);
                    safeShowText(csRef[0], desc);
                    csRef[0].endText();
                }

                yRef[0] -= ROW_H;
            }

            if (tasks.isEmpty()) {
                csRef[0].setNonStrokingColor(0.5f, 0.5f, 0.5f);
                csRef[0].beginText();
                csRef[0].setFont(fontOblique, 10);
                csRef[0].newLineAtOffset(MARGIN + 10, yRef[0]);
                csRef[0].showText("No tasks found for the selected period.");
                csRef[0].endText();
            }

            // ── Footer ───────────────────────────────────────────────────────
            csRef[0].setNonStrokingColor(0.6f, 0.6f, 0.6f);
            csRef[0].beginText();
            csRef[0].setFont(fontRegular, 7);
            csRef[0].newLineAtOffset(MARGIN, 20);
            csRef[0].showText("TaskTracker - Confidential  |  "
                    + from.format(DISPLAY_DATE) + " to " + to.format(DISPLAY_DATE));
            csRef[0].endText();

            csRef[0].close();
            doc.save(baos);
            log.info("PDF generated — user={} tasks={} pages={}", user.getId(), tasks.size(), doc.getNumberOfPages());
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    private String safe(String input) {
        return input != null ? input : "-";
    }

    /**
     * Loads a TrueType font from the classpath and embeds it in the document.
     * Using PDType0Font (composite font) instead of PDType1Font ensures full
     * Unicode support, preventing IllegalArgumentException for non-Latin characters.
     */
    private PDType0Font loadFont(PDDocument doc, String classpathPath) throws IOException {
        try (InputStream is = new ClassPathResource(classpathPath).getInputStream()) {
            return PDType0Font.load(doc, is);
        }
    }

    /**
     * Sanitizes a string for safe PDF rendering.
     * Replaces null with a dash, strips control characters, and replaces
     * supplementary Unicode code points (surrogate pairs, > U+FFFF) with '?'.
     * Non-ASCII characters in the BMP that might cause GSUB issues are handled
     * separately by {@link #safeShowText}.
     */
    private String sanitizeText(String input) {
        if (input == null) return "-";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            if (cp < 0x20 && cp != 0x09) {
                // Strip control characters except tab (render as space)
                sb.append(' ');
            } else if (cp > 0xFFFF) {
                // Surrogate pairs / supplementary plane — replace with '?'
                sb.append('?');
            } else {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /**
     * Renders text to the PDF content stream safely.
     *
     * PDFBox 3.0.x's GSUB (Glyph Substitution) engine throws
     * {@link IllegalStateException} for complex Indic scripts (Gujarati U+0A80-0AFF,
     * Devanagari, Tamil, etc.) even when the font has GSUB table entries for those
     * ranges — because the glyph lookup inside applyGSUBRules fails.
     *
     * Crucially, PDFBox encodes the ENTIRE string BEFORE writing a single byte
     * to the content stream. So if encoding throws, the stream is still in a
     * valid, recoverable state and we can safely retry with a fallback string.
     *
     * Fallback strategy: replace every non-ASCII-printable character with '?'.
     * The PDF always generates; Indic/complex-script text shows as '???' rather
     * than crashing the entire report.
     */
    private void safeShowText(PDPageContentStream cs, String text) {
        try {
            cs.showText(text);
        } catch (IllegalStateException | IOException e) {
            // GSUB shaping or encoding failed — strip non-ASCII and retry
            log.debug("PDF GSUB encoding failed for text '{}', using ASCII fallback: {}",
                    text.replaceAll("[^\\x20-\\x7E]", "?"), e.getMessage());
            String asciiSafe = text.replaceAll("[^\\x20-\\x7E]", "?");
            try {
                cs.showText(asciiSafe);
            } catch (Exception ex) {
                log.warn("PDF: ASCII fallback also failed for text segment, skipping: {}", ex.getMessage());
            }
        }
    }

    private PDPageContentStream addNewPage(PDDocument doc, PDType0Font fontBold,
                                           float[] yRef) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        yRef[0] = PAGE_HEIGHT - MARGIN;
        drawTableHeader(cs, fontBold, yRef[0]);
        yRef[0] -= 18;
        return cs;
    }

    private void drawTableHeader(PDPageContentStream cs, PDType0Font fontBold, float y) throws IOException {
        cs.setNonStrokingColor(0.15f, 0.12f, 0.38f);
        cs.addRect(MARGIN, y - 3, CONTENT_W, 16);
        cs.fill();

        cs.setNonStrokingColor(1f, 1f, 1f);
        cs.beginText();
        cs.setFont(fontBold, 8f);
        cs.newLineAtOffset(COL_DATE, y + 4);
        cs.showText("DATE");
        cs.newLineAtOffset(COL_TITLE - COL_DATE, 0);
        cs.showText("TITLE");
        cs.newLineAtOffset(COL_DESC - COL_TITLE, 0);
        cs.showText("DESCRIPTION");
        cs.endText();
    }
}
