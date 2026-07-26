package com.tasktracker.service;

import com.tasktracker.entity.Task;
import com.tasktracker.entity.TaskStatus;
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
    private static final float MARGIN       = 40f;
    private static final float PAGE_WIDTH   = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT  = PDRectangle.A4.getHeight();
    private static final float CONTENT_W    = PAGE_WIDTH - 2 * MARGIN;

    // Column X positions (compact single-line layout)
    // DATE(65) | TITLE(160) | STATUS(55) | DESCRIPTION(rest)
    private static final float COL_DATE   = MARGIN + 2;
    private static final float COL_TITLE  = MARGIN + 67;
    private static final float COL_STATUS = MARGIN + 227;
    private static final float COL_DESC   = MARGIN + 282;

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
                rangeTo = LocalDate.of(year, 12, 31);
                reportTitle = "Annual Task Report — " + year;
            }
            case MONTH -> {
                rangeFrom = LocalDate.of(year, month, 1);
                rangeTo = rangeFrom.withDayOfMonth(rangeFrom.lengthOfMonth());
                reportTitle = "Monthly Task Report — " + rangeFrom.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            }
            default -> {
                rangeFrom = from;
                rangeTo = to;
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

            long total      = tasks.size();
            long done       = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
            long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            // ── Page 1 ───────────────────────────────────────────────────
            PDPage firstPage = new PDPage(PDRectangle.A4);
            doc.addPage(firstPage);
            PDPageContentStream[] csRef = { new PDPageContentStream(doc, firstPage) };
            PDPageContentStream cs = csRef[0];
            float[] yRef = { PAGE_HEIGHT - MARGIN };

            // ── Banner (dark purple) ─────────────────────────────────────
            cs.setNonStrokingColor(0.17f, 0.12f, 0.40f);
            cs.addRect(0, PAGE_HEIGHT - 90, PAGE_WIDTH, 90);
            cs.fill();

            // Report title
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.beginText();
            cs.setFont(fontBold, 16);
            cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 38);
            cs.showText(reportTitle);
            cs.endText();

            // Generated date
            cs.beginText();
            cs.setFont(fontRegular, 9);
            cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 55);
            cs.showText("Generated: " + LocalDate.now().format(DISPLAY_DATE));
            cs.endText();

            // College name — bold, larger
            String college = user.getCollegeName();
            if (college != null && !college.isBlank()) {
                cs.beginText();
                cs.setFont(fontBold, 13);
                cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 74);
                String collegeDisplay = college.length() > 80 ? college.substring(0, 77) + "..." : college;
                cs.showText(collegeDisplay);
                cs.endText();
            }

            yRef[0] = PAGE_HEIGHT - 105;

            // ── User Info bar ────────────────────────────────────────────
            cs.setNonStrokingColor(0.93f, 0.93f, 0.97f);
            cs.addRect(MARGIN, yRef[0] - 26, CONTENT_W, 30);
            cs.fill();

            cs.setNonStrokingColor(0.17f, 0.12f, 0.40f);
            cs.beginText();
            cs.setFont(fontBold, 8.5f);
            cs.newLineAtOffset(MARGIN + 6, yRef[0] - 12);
            StringBuilder userLine = new StringBuilder();
            userLine.append(user.getFullName());
            if (user.getDepartment() != null && !user.getDepartment().isBlank())
                userLine.append("  |  Dept: ").append(user.getDepartment());
            if (user.getDesignation() != null && !user.getDesignation().isBlank())
                userLine.append("  |  ").append(user.getDesignation());
            userLine.append("  |  ").append(user.getEmail());
            String ul = userLine.toString();
            if (ul.length() > 105) ul = ul.substring(0, 102) + "...";
            cs.showText(ul);
            cs.endText();

            yRef[0] -= 40;

            // ── Summary counts ───────────────────────────────────────────
            cs.setNonStrokingColor(0f, 0f, 0f);
            cs.beginText();
            cs.setFont(fontBold, 9);
            cs.newLineAtOffset(MARGIN + 4, yRef[0]);
            cs.showText(String.format("Total Tasks: %d     Done: %d     In Progress: %d", total, done, inProgress));
            cs.endText();

            yRef[0] -= 18;

            // ── Column header row ────────────────────────────────────────
            drawTableHeader(cs, fontBold, yRef[0]);
            yRef[0] -= 18;

            // ── Task rows (compact single line per task) ─────────────────
            boolean alt = false;
            for (Map.Entry<LocalDate, List<Task>> entry : byDate.entrySet()) {
                LocalDate date = entry.getKey();

                // Date group separator
                if (yRef[0] < MARGIN + 20) {
                    cs.close();
                    cs = addNewPage(doc, fontBold, yRef);
                    alt = false;
                }

                cs.setNonStrokingColor(0.88f, 0.85f, 0.97f);
                cs.addRect(MARGIN, yRef[0] - 2, CONTENT_W, 13);
                cs.fill();

                cs.setNonStrokingColor(0.17f, 0.12f, 0.40f);
                cs.beginText();
                cs.setFont(fontBold, 7.5f);
                cs.newLineAtOffset(COL_DATE, yRef[0] + 4);
                cs.showText("▸  " + date.format(DISPLAY_DATE));
                cs.endText();

                yRef[0] -= 15;

                for (Task t : entry.getValue()) {
                    final float ROW_H = 13f;

                    if (yRef[0] < MARGIN + ROW_H + 4) {
                        cs.close();
                        cs = addNewPage(doc, fontBold, yRef);
                        alt = false;
                    }

                    // Alternating row shading
                    if (alt) {
                        cs.setNonStrokingColor(0.97f, 0.97f, 0.99f);
                        cs.addRect(MARGIN, yRef[0] - 2, CONTENT_W, ROW_H);
                        cs.fill();
                    }
                    alt = !alt;

                    // Thin left accent line by status colour
                    float[] sc = statusColor(t.getStatus());
                    cs.setNonStrokingColor(sc[0], sc[1], sc[2]);
                    cs.addRect(MARGIN, yRef[0] - 2, 3, ROW_H);
                    cs.fill();

                    // DATE column
                    cs.setNonStrokingColor(0.35f, 0.35f, 0.35f);
                    cs.beginText();
                    cs.setFont(fontRegular, 7.5f);
                    cs.newLineAtOffset(COL_DATE, yRef[0] + 3);
                    cs.showText(date.format(DateTimeFormatter.ofPattern("dd/MM")));
                    cs.endText();

                    // TITLE column
                    cs.setNonStrokingColor(0f, 0f, 0f);
                    cs.beginText();
                    cs.setFont(fontBold, 7.5f);
                    cs.newLineAtOffset(COL_TITLE, yRef[0] + 3);
                    String title = t.getTitle();
                    if (title.length() > 30) title = title.substring(0, 27) + "...";
                    cs.showText(title);
                    cs.endText();

                    // STATUS column
                    cs.setNonStrokingColor(sc[0], sc[1], sc[2]);
                    cs.beginText();
                    cs.setFont(fontRegular, 7f);
                    cs.newLineAtOffset(COL_STATUS, yRef[0] + 3);
                    cs.showText(t.getStatus() == TaskStatus.IN_PROGRESS ? "In Progress" : "Done");
                    cs.endText();

                    // DESCRIPTION column
                    if (t.getDescription() != null && !t.getDescription().isBlank()) {
                        String desc = t.getDescription().replaceAll("[\\r\\n]+", " ").trim();
                        int maxDescChars = 55;
                        if (desc.length() > maxDescChars) desc = desc.substring(0, maxDescChars - 3) + "...";
                        cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                        cs.beginText();
                        cs.setFont(fontOblique, 7f);
                        cs.newLineAtOffset(COL_DESC, yRef[0] + 3);
                        cs.showText(desc);
                        cs.endText();
                    }

                    yRef[0] -= ROW_H;
                }
            }

            if (tasks.isEmpty()) {
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                cs.beginText();
                cs.setFont(fontOblique, 10);
                cs.newLineAtOffset(MARGIN, yRef[0]);
                cs.showText("No tasks found for the selected period.");
                cs.endText();
            }

            // ── Footer ───────────────────────────────────────────────────
            cs.setNonStrokingColor(0.6f, 0.6f, 0.6f);
            cs.beginText();
            cs.setFont(fontRegular, 7);
            cs.newLineAtOffset(MARGIN, 22);
            cs.showText("TaskTracker — Confidential  |  " + from.format(DISPLAY_DATE) + " to " + to.format(DISPLAY_DATE));
            cs.endText();

            cs.close();
            doc.save(baos);
            log.info("PDF report generated — user={} tasks={} pages={}", user.getId(), tasks.size(), doc.getNumberOfPages());
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    /** Colour for left status accent: Done=green, InProgress=blue, otherwise gray */
    private float[] statusColor(TaskStatus s) {
        return switch (s) {
            case DONE        -> new float[]{0.13f, 0.76f, 0.37f};
            case IN_PROGRESS -> new float[]{0.23f, 0.51f, 0.96f};
            default          -> new float[]{0.7f,  0.7f,  0.7f};
        };
    }

    /** Opens a new page, resets y, redraws column header. */
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

    /** Draws the purple column-header row. */
    private void drawTableHeader(PDPageContentStream cs, PDType1Font fontBold, float y) throws IOException {
        cs.setNonStrokingColor(0.17f, 0.12f, 0.40f);
        cs.addRect(MARGIN, y - 3, CONTENT_W, 16);
        cs.fill();

        cs.setNonStrokingColor(1f, 1f, 1f);
        cs.beginText();
        cs.setFont(fontBold, 7.5f);
        cs.newLineAtOffset(COL_DATE, y + 4);
        cs.showText("DATE");
        cs.newLineAtOffset(COL_TITLE - COL_DATE, 0);
        cs.showText("TITLE");
        cs.newLineAtOffset(COL_STATUS - COL_TITLE, 0);
        cs.showText("STATUS");
        cs.newLineAtOffset(COL_DESC - COL_STATUS, 0);
        cs.showText("DESCRIPTION");
        cs.endText();
    }
}
