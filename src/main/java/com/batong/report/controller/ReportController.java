package com.batong.report.controller;

import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.response.ApiResponse;
import com.batong.report.dto.ReportDtos.DailyRecordListResponse;
import com.batong.report.dto.ReportDtos.DailyRecordResponse;
import com.batong.report.dto.ReportDtos.WeeklyReportResponse;
import com.batong.report.service.ReportService;
import com.batong.report.service.WeeklyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final WeeklyReportService weeklyReportService;

    @Operation(summary = "REPORT-01 일일 기록 날짜 목록",
        description = "화면 2.4. 승인 항목이 하나 이상 있는 일기. from·to를 생략하면 to=오늘(KST), from=to 포함 최근 7일. 응답의 from·to는 실제 적용한 구간")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/daily-records")
    public ApiResponse<DailyRecordListResponse> getDailyRecords(
        @PathVariable Long subjectId,
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.getDailyRecords(subjectId, userId, from, to));
    }

    @Operation(summary = "REPORT-02 일일 기록", description = "화면 2.4. 승인 항목만. 원문·공감 문장·근거 문장은 없다")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/daily-records/{diaryId}")
    public ApiResponse<DailyRecordResponse> getDailyRecord(@PathVariable Long subjectId,
                                                           @PathVariable Long diaryId,
                                                           @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(reportService.getDailyRecord(subjectId, userId, diaryId));
    }

    @Operation(summary = "REPORT-03 주간 리포트 (P1)",
        description = "화면 2.5. endDate 포함 최근 7일(기본 오늘 KST). 승인 항목만. 일기 수 3건 미만이면 summaryLines는 빈 배열, 0건이면 건수 0과 빈 배열")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/weekly-reports")
    public ApiResponse<WeeklyReportResponse> getWeeklyReport(
        @PathVariable Long subjectId,
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(weeklyReportService.getWeeklyReport(subjectId, userId, endDate));
    }
}
