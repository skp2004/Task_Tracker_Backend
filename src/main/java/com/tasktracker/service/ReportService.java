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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter SHORT_DATE   = DateTimeFormatter.ofPattern("dd/MM");
    private static final float MARGIN      = 40f;
    private static final float PAGE_WIDTH  = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_W   = PAGE_WIDTH - 2 * MARGIN;

    // 3-column layout: DATE | TITLE | DESCRIPTION
    private static final float COL_DATE  = MARGIN + 2;
    private static final float COL_TITLE = MARGIN + 62;
    private static final float COL_DESC  = MARGIN + 232;

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

            Map<LocalDate, List<Task>> byDate = tasks.stream()
                    .collect(Collectors.groupingBy(Task::getTaskDate, LinkedHashMap::new, Collectors.toList()));

            long total = tasks.size();

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            // ── Page 1 ──────────────────────────────────────────────────────
            PDPage firstPage = new PDPage(PDRectangle.A4);
            doc.addPage(firstPage);
            PDPageContentStream[] csRef = { new PDPageContentStream(doc, firstPage) };
            float[] yRef = { PAGE_HEIGHT - MARGIN };

            // ── Banner ───────────────────────────────────────────────────────
            csRef[0].setNonStrokingColor(0.17f, 0.12f, 0.40f);
            csRef[0].addRect(0, PAGE_HEIGHT - 90, PAGE_WIDTH, 90);
            csRef[0].fill();

            // Report title
            csRef[0].setNonStrokingColor(1f, 1f, 1f);
            csRef[0].beginText();
            csRef[0].setFont(fontBold, 16);
            csRef[0].newLineAtOffset(MARGIN, PAGE_HEIGHT - 36);
            csRef[0].showText(reportTitle);
            csRef[0].endText();

            // Generated date
            csRef[0].beginText();
            csRef[0].setFont(fontRegular, 8.5f);
            csRef[0].newLineAtOffset(MARGIN, PAGE_HEIGHT - 52);
            csRef[0].showText("Generated: " + LocalDate.now().format(DISPLAY_DATE)
                    + "   |   Total Tasks: " + total);
            csRef[0].endText();

            // College name — bold, prominent
            String college = user.getCollegeName();
            if (college != null && !college.isBlank()) {
                csRef[0].beginText();
                csRef[0].setFont(fontBold, 12);
                csRef[0].newLineAtOffset(MARGIN, PAGE_HEIGHT - 70);
                String c = college.length() > 85 ? college.substring(0, 82) + "..." : college;
                csRef[0].showText(c);
                csRef[0].endText();
            }

            yRef[0] = PAGE_HEIGHT - 102;

            // ── User info bar ────────────────────────────────────────────────
            csRef[0].setNonStrokingColor(0.93f, 0.93f, 0.97f);
            csRef[0].addRect(MARGIN, yRef[0] - 24, CONTENT_W, 28);
            csRef[0].fill();

            csRef[0].setNonStrokingColor(0.17f, 0.12f, 0.40f);
            csRef[0].beginText();
            csRef[0].setFont(fontBold, 8f);
            csRef[0].newLineAtOffset(MARGIN + 6, yRef[0] - 10);
            StringBuilder ul = new StringBuilder(user.getFullName());
            if (user.getDepartment() != null && !user.getDepartment().isBlank())
                ul.append("  |  ").append(user.getDepartment());
            if (user.getDesignation() != null && !user.getDesignation().isBlank())
                ul.append("  |  ").append(user.getDesignation());
            ul.append("  |  ").append(user.getEmail());
            String userLine = ul.toString();
            if (userLine.length() > 110) userLine = userLine.substring(0, 107) + "...";
            csRef[0].showText(userLine);
            csRef[0].endText();

            yRef[0] -= 38;

            // ── Column header row ────────────────────────────────────────────
            drawTableHeader(csRef[0], fontBold, yRef[0]);
            yRef[0] -= 18;

            // ── Task rows ────────────────────────────────────────────────────
            boolean alt = false;
            for (Map.Entry<LocalDate, List<Task>> entry : byDate.entrySet()) {
                LocalDate date = entry.getKey();

                // Date group separator
                if (yRef[0] < MARGIN + 20) {
                    csRef[0].close();
                    csRef[0] = addNewPage(doc, fontBold, yRef);
                    alt = false;
                }

                csRef[0].setNonStrokingColor(0.88f, 0.85f, 0.97f);
                csRef[0].addRect(MARGIN, yRef[0] - 2, CONTENT_W, 13);
                csRef[0].fill();

                csRef[0].setNonStrokingColor(0.17f, 0.12f, 0.40f);
                csRef[0].beginText();
                csRef[0].setFont(fontBold, 7.5f);
                csRef[0].newLineAtOffset(COL_DATE, yRef[0] + 4);
                csRef[0].showText(">  " + date.format(DISPLAY_DATE));
                csRef[0].endText();

                yRef[0] -= 15;

                for (Task t : entry.getValue()) {
                    final float ROW_H = 13f;

                    if (yRef[0] < MARGIN + ROW_H + 4) {
                        csRef[0].close();
                        csRef[0] = addNewPage(doc, fontBold, yRef);
                        alt = false;
                    }

                    // Alternating row background
                    if (alt) {
                        csRef[0].setNonStrokingColor(0.97f, 0.97f, 0.99f);
                        csRef[0].addRect(MARGIN, yRef[0] - 2, CONTENT_W, ROW_H);
                        csRef[0].fill();
                    }
                    alt = !alt;

                    // Left purple accent bar
                    csRef[0].setNonStrokingColor(0.40f, 0.30f, 0.80f);
                    csRef[0].addRect(MARGIN, yRef[0] - 2, 3, ROW_H);
                    csRef[0].fill();

                    // DATE column (short)
                    csRef[0].setNonStrokingColor(0.40f, 0.40f, 0.40f);
                    csRef[0].beginText();
                    csRef[0].setFont(fontRegular, 7.5f);
                    csRef[0].newLineAtOffset(COL_DATE + 5, yRef[0] + 3);
                    csRef[0].showText(date.format(SHORT_DATE));
                    csRef[0].endText();

                    // TITLE column
                    csRef[0].setNonStrokingColor(0f, 0f, 0f);
                    csRef[0].beginText();
                    csRef[0].setFont(fontBold, 7.5f);
                    csRef[0].newLineAtOffset(COL_TITLE, yRef[0] + 3);
                    String title = t.getTitle();
                    if (title.length() > 35) title = title.substring(0, 32) + "...";
                    csRef[0].showText(title);
                    csRef[0].endText();

                    // DESCRIPTION column
                    if (t.getDescription() != null && !t.getDescription().isBlank()) {
                        String desc = t.getDescription().replaceAll("[\\r\\n]+", " ").trim();
                        int maxChars = 68;
                        if (desc.length() > maxChars) desc = desc.substring(0, maxChars - 3) + "...";
                        csRef[0].setNonStrokingColor(0.35f, 0.35f, 0.35f);
                        csRef[0].beginText();
                        csRef[0].setFont(fontOblique, 7f);
                        csRef[0].newLineAtOffset(COL_DESC, yRef[0] + 3);
                        csRef[0].showText(desc);
                        csRef[0].endText();
                    }

                    yRef[0] -= ROW_H;
                }
            }

            if (tasks.isEmpty()) {
                csRef[0].setNonStrokingColor(0.5f, 0.5f, 0.5f);
                csRef[0].beginText();
                csRef[0].setFont(fontOblique, 10);
                csRef[0].newLineAtOffset(MARGIN, yRef[0]);
                csRef[0].showText("No tasks found for the selected period.");
                csRef[0].endText();
            }

            // ── Footer ───────────────────────────────────────────────────────
            csRef[0].setNonStrokingColor(0.6f, 0.6f, 0.6f);
            csRef[0].beginText();
            csRef[0].setFont(fontRegular, 7);
            csRef[0].newLineAtOffset(MARGIN, 22);
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

    private PDPageContentStream addNewPage(PDDocument doc, PDType1Font fontBold,
                                           float[] yRef) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        yRef[0] = PAGE_HEIGHT - MARGIN;
        drawTableHeader(cs, fontBold, yRef[0]);
        yRef[0] -= 18;
        return cs;
    }

    private void drawTableHeader(PDPageContentStream cs, PDType1Font fontBold, float y) throws IOException {
        cs.setNonStrokingColor(0.17f, 0.12f, 0.40f);
        cs.addRect(MARGIN, y - 3, CONTENT_W, 16);
        cs.fill();

        cs.setNonStrokingColor(1f, 1f, 1f);
        cs.beginText();
        cs.setFont(fontBold, 8f);
        cs.newLineAtOffset(COL_DATE + 5, y + 4);
        cs.showText("DATE");
        cs.newLineAtOffset(COL_TITLE - COL_DATE - 5, 0);
        cs.showText("TITLE");
        cs.newLineAtOffset(COL_DESC - COL_TITLE, 0);
        cs.showText("DESCRIPTION");
        cs.endText();
    }
}
