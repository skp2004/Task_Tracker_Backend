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
    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    public enum ReportType { YEAR, MONTH, DATE_RANGE }

    @Transactional(readOnly = true)
    public byte[] generateReport(Long userId, ReportType type, Integer year, Integer month,
                                 LocalDate from, LocalDate to) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Compute date range based on report type
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

    private byte[] buildPdf(User user, String reportTitle, LocalDate from, LocalDate to,
                             List<Task> tasks) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Group tasks by date
            Map<LocalDate, List<Task>> byDate = tasks.stream()
                    .collect(Collectors.groupingBy(Task::getTaskDate, LinkedHashMap::new, Collectors.toList()));

            // Summary counts
            long total = tasks.size();
            long done = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
            long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
            long todo = tasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            // ── Page 1 ──────────────────────────────────────────────────
            PDPage firstPage = new PDPage(PDRectangle.A4);
            doc.addPage(firstPage);

            // Use a single-element array so we can reassign inside lambdas / loops
            PDPageContentStream[] csRef = { new PDPageContentStream(doc, firstPage) };
            PDPageContentStream cs = csRef[0];
            float[] yRef = { PAGE_HEIGHT - MARGIN };

            // ── Header ──────────────────────────────────────────────────
            cs.setNonStrokingColor(0.24f, 0.18f, 0.56f);
            cs.addRect(0, PAGE_HEIGHT - 80, PAGE_WIDTH, 80);
            cs.fill();

            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.beginText();
            cs.setFont(fontBold, 18);
            cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 45);
            cs.showText(reportTitle);
            cs.endText();

            cs.beginText();
            cs.setFont(fontRegular, 10);
            cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 65);
            cs.showText("Generated on " + LocalDate.now().format(DISPLAY_DATE));
            cs.endText();

            yRef[0] = PAGE_HEIGHT - 110;

            // ── User Info ────────────────────────────────────────────────
            cs.setNonStrokingColor(0f, 0f, 0f);
            cs.beginText();
            cs.setFont(fontBold, 11);
            cs.newLineAtOffset(MARGIN, yRef[0]);
            cs.showText("User: " + user.getFullName() + "   |   Email: " + user.getEmail()
                    + (user.getPhone() != null ? "   |   Phone: " + user.getPhone() : ""));
            cs.endText();

            yRef[0] -= 25;

            // ── Summary Bar ──────────────────────────────────────────────
            cs.setNonStrokingColor(0.95f, 0.95f, 0.95f);
            cs.addRect(MARGIN, yRef[0] - 40, PAGE_WIDTH - 2 * MARGIN, 45);
            cs.fill();

            cs.setNonStrokingColor(0f, 0f, 0f);
            cs.beginText();
            cs.setFont(fontBold, 10);
            cs.newLineAtOffset(MARGIN + 10, yRef[0] - 15);
            cs.showText(String.format("Total: %d   |   Done: %d   |   In Progress: %d   |   To Do: %d",
                    total, done, inProgress, todo));
            cs.endText();

            yRef[0] -= 65;

            // ── Helper: draw column header row ───────────────────────────
            drawTableHeader(cs, fontBold, yRef[0]);
            yRef[0] -= 25;

            // ── Task Rows ────────────────────────────────────────────────
            boolean alt = false;
            for (Map.Entry<LocalDate, List<Task>> entry : byDate.entrySet()) {
                LocalDate date = entry.getKey();
                List<Task> dayTasks = entry.getValue();

                // Check space before drawing the day header (14px height)
                if (yRef[0] < MARGIN + 30) {
                    cs.close();
                    cs = addNewPage(doc, fontBold, yRef);
                    alt = false;
                }

                // Day header
                cs.setNonStrokingColor(0.88f, 0.85f, 0.97f);
                cs.addRect(MARGIN, yRef[0] - 3, PAGE_WIDTH - 2 * MARGIN, 14);
                cs.fill();

                cs.setNonStrokingColor(0.24f, 0.18f, 0.56f);
                cs.beginText();
                cs.setFont(fontBold, 8);
                cs.newLineAtOffset(MARGIN + 5, yRef[0] + 4);
                cs.showText(date.format(DISPLAY_DATE));
                cs.endText();

                yRef[0] -= 18;

                for (Task t : dayTasks) {
                    // Check space before each task row (15px height)
                    if (yRef[0] < MARGIN + 20) {
                        cs.close();
                        cs = addNewPage(doc, fontBold, yRef);
                        alt = false;
                    }

                    if (alt) {
                        cs.setNonStrokingColor(0.98f, 0.98f, 0.98f);
                        cs.addRect(MARGIN, yRef[0] - 3, PAGE_WIDTH - 2 * MARGIN, 13);
                        cs.fill();
                    }
                    alt = !alt;

                    cs.setNonStrokingColor(0f, 0f, 0f);
                    cs.beginText();
                    cs.setFont(fontRegular, 8);
                    cs.newLineAtOffset(MARGIN + 85, yRef[0] + 4);
                    String title = t.getTitle().length() > 38 ? t.getTitle().substring(0, 35) + "..." : t.getTitle();
                    cs.showText(title);
                    cs.newLineAtOffset(220, 0);
                    cs.showText(t.getStatus().name());
                    cs.newLineAtOffset(70, 0);
                    cs.showText(t.getPriority().name());
                    cs.newLineAtOffset(60, 0);
                    cs.showText(t.getCategory() != null ? t.getCategory() : "—");
                    cs.endText();

                    yRef[0] -= 15;
                }
            }

            if (tasks.isEmpty()) {
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                cs.beginText();
                cs.setFont(fontOblique, 11);
                cs.newLineAtOffset(MARGIN, yRef[0]);
                cs.showText("No tasks found for the selected period.");
                cs.endText();
            }

            // ── Footer on last page ──────────────────────────────────────
            cs.setNonStrokingColor(0.6f, 0.6f, 0.6f);
            cs.beginText();
            cs.setFont(fontRegular, 8);
            cs.newLineAtOffset(MARGIN, 30);
            cs.showText("TaskTracker — Confidential | " + from.format(DISPLAY_DATE) + " to " + to.format(DISPLAY_DATE));
            cs.endText();

            cs.close(); // close the last open stream

            doc.save(baos);
            log.info("PDF report generated for user {} — {} tasks, {} pages",
                    user.getId(), tasks.size(), doc.getNumberOfPages());
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    /** Opens a new page, resets y to top margin, redraws the column header row. */
    private PDPageContentStream addNewPage(PDDocument doc, PDType1Font fontBold,
                                           float[] yRef) throws IOException {
        PDPage newPage = new PDPage(PDRectangle.A4);
        doc.addPage(newPage);
        PDPageContentStream newCs = new PDPageContentStream(doc, newPage);
        yRef[0] = PAGE_HEIGHT - MARGIN;
        drawTableHeader(newCs, fontBold, yRef[0]);
        yRef[0] -= 25;
        return newCs;
    }

    /** Draws the purple column header row at the given y position. */
    private void drawTableHeader(PDPageContentStream cs, PDType1Font fontBold,
                                  float y) throws IOException {
        cs.setNonStrokingColor(0.24f, 0.18f, 0.56f);
        cs.addRect(MARGIN, y - 5, PAGE_WIDTH - 2 * MARGIN, 20);
        cs.fill();

        cs.setNonStrokingColor(1f, 1f, 1f);
        cs.beginText();
        cs.setFont(fontBold, 9);
        cs.newLineAtOffset(MARGIN + 5, y + 3);
        cs.showText("DATE");
        cs.newLineAtOffset(80, 0);
        cs.showText("TITLE");
        cs.newLineAtOffset(220, 0);
        cs.showText("STATUS");
        cs.newLineAtOffset(70, 0);
        cs.showText("PRIORITY");
        cs.newLineAtOffset(60, 0);
        cs.showText("CATEGORY");
        cs.endText();
    }
}
