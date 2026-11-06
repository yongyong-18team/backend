package com.batong.diary.controller;

import com.batong.diary.dto.DiaryDtos.AnalyzeResponse;
import com.batong.diary.dto.DiaryDtos.DiaryCreateRequest;
import com.batong.diary.dto.DiaryDtos.DiaryCreateResponse;
import com.batong.diary.dto.DiaryDtos.DiaryDetailResponse;
import com.batong.diary.dto.DiaryDtos.DiaryListResponse;
import com.batong.diary.service.DiaryService;
import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 일기 원문을 다루므로 모두 OWNER 전용(TRD 7-2). 공동 관리자는 REPORT-01·02를 쓴다 */
@Tag(name = "일기·AI 분석")
@RestController
@RequestMapping("/api/v1/subjects/{subjectId}/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "DIARY-01 일기 저장")
    @RequireRole(SubjectRole.OWNER)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<DiaryCreateResponse> create(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId,
                                                   @RequestBody DiaryCreateRequest request) {
        return ApiResponse.ok(diaryService.create(subjectId, userId, request));
    }

    @Operation(summary = "DIARY-02 AI 분석", description = "동기, 최대 25초. 클라이언트 타임아웃은 30초 이상")
    @RequireRole(SubjectRole.OWNER)
    @PostMapping("/{diaryId}/analyze")
    public ApiResponse<AnalyzeResponse> analyze(@PathVariable Long subjectId, @PathVariable Long diaryId,
                                                @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(diaryService.analyze(subjectId, diaryId, userId));
    }

    @Operation(summary = "DIARY-03 일기 목록", description = "전체·이번 주·지난달 필터는 from/to로")
    @RequireRole(SubjectRole.OWNER)
    @GetMapping
    public ApiResponse<DiaryListResponse> list(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId,
                                               @RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(diaryService.list(subjectId, userId, from, to, page, size));
    }

    @Operation(summary = "DIARY-04 일기 상세 (원문, 공감, 추출 항목)")
    @RequireRole(SubjectRole.OWNER)
    @GetMapping("/{diaryId}")
    public ApiResponse<DiaryDetailResponse> get(@PathVariable Long subjectId, @PathVariable Long diaryId,
                                                @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(diaryService.get(subjectId, diaryId, userId));
    }

    @Operation(summary = "DIARY-05 일기 삭제")
    @RequireRole(SubjectRole.OWNER)
    @DeleteMapping("/{diaryId}")
    public ApiResponse<Void> delete(@PathVariable Long subjectId, @PathVariable Long diaryId,
                                    @AuthenticationPrincipal Long userId) {
        diaryService.delete(subjectId, diaryId, userId);
        return ApiResponse.ok(null);
    }
}
