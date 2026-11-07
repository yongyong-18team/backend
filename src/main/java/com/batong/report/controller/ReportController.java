package com.batong.report.controller;

import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.response.ApiResponse;
import com.batong.report.dto.ReportDtos.DailyRecordListResponse;
import com.batong.report.dto.ReportDtos.DailyRecordResponse;
import com.batong.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "리포트")
@RestController
@RequestMapping("/api/v1/subjects/{subjectId}")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "REPORT-01 일일 기록 날짜 목록", description = "화면 2.4. 승인 항목이 있는 날짜")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/daily-records")
    public ApiResponse<DailyRecordListResponse> getDailyRecords(
        @PathVariable Long subjectId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.getDailyRecords(subjectId, from, to));
    }

    @Operation(summary = "REPORT-02 일일 기록", description = "화면 2.4. 승인 항목만")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/daily-records/{diaryId}")
    public ApiResponse<DailyRecordResponse> getDailyRecord(@PathVariable Long subjectId, @PathVariable Long diaryId) {
        return ApiResponse.ok(reportService.getDailyRecord(subjectId, diaryId));
    }
}
