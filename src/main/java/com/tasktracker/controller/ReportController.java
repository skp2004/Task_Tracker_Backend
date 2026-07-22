package com.tasktracker.controller;

import com.tasktracker.security.UserPrincipal;
import com.tasktracker.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "PDF report generation and download")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/year/{year}")
    @Operation(summary = "Download annual PDF report")
    public ResponseEntity<byte[]> yearReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable int year) {
        byte[] pdf = reportService.generateReport(principal.getId(),
                ReportService.ReportType.YEAR, year, null, null, null);
        return pdfResponse(pdf, "TaskReport_" + year + ".pdf");
    }

    @GetMapping("/month/{year}/{month}")
    @Operation(summary = "Download monthly PDF report")
    public ResponseEntity<byte[]> monthReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable int year,
            @PathVariable int month) {
        byte[] pdf = reportService.generateReport(principal.getId(),
                ReportService.ReportType.MONTH, year, month, null, null);
        return pdfResponse(pdf, String.format("TaskReport_%d_%02d.pdf", year, month));
    }

    @GetMapping("/range")
    @Operation(summary = "Download date-range PDF report")
    public ResponseEntity<byte[]> rangeReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = reportService.generateReport(principal.getId(),
                ReportService.ReportType.DATE_RANGE, null, null, from, to);
        String filename = "TaskReport_" + from.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_to_" + to.format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";
        return pdfResponse(pdf, filename);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdf.length))
                .body(pdf);
    }
}
