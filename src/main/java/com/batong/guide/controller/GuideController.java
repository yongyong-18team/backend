package com.batong.guide.controller;

import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.response.ApiResponse;
import com.batong.guide.dto.GuideDtos.EntryCreateRequest;
import com.batong.guide.dto.GuideDtos.EntryCreateResponse;
import com.batong.guide.dto.GuideDtos.EntryUpdateRequest;
import com.batong.guide.dto.GuideDtos.EntryUpdateResponse;
import com.batong.guide.dto.GuideDtos.GuideResponse;
import com.batong.guide.dto.GuideDtos.ReviewRequest;
import com.batong.guide.dto.GuideDtos.ReviewResponse;
import com.batong.guide.service.GuideService;
import com.batong.guide.service.ItemReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "설명서")
@RestController
@RequestMapping("/api/v1/subjects/{subjectId}")
@RequiredArgsConstructor
public class GuideController {

    private final GuideService guideService;
    private final ItemReviewService itemReviewService;

    @Operation(summary = "GUIDE-01 설명서 전체")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/guide")
    public ApiResponse<GuideResponse> getGuide(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(guideService.getGuide(subjectId, userId));
    }

    @Operation(summary = "GUIDE-02 항목 추가 (MANUAL)")
    @RequireRole(SubjectRole.OWNER)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/guide/entries")
    public ApiResponse<EntryCreateResponse> addEntry(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId,
                                                     @RequestBody EntryCreateRequest request) {
        return ApiResponse.ok(guideService.addEntry(subjectId, userId, request));
    }

    @Operation(summary = "GUIDE-03 항목 수정")
    @RequireRole(SubjectRole.OWNER)
    @PatchMapping("/guide/entries/{entryId}")
    public ApiResponse<EntryUpdateResponse> updateEntry(@PathVariable Long subjectId, @PathVariable Long entryId,
                                                        @AuthenticationPrincipal Long userId,
                                                        @RequestBody EntryUpdateRequest request) {
        return ApiResponse.ok(guideService.updateEntry(subjectId, entryId, userId, request));
    }

    @Operation(summary = "GUIDE-04 항목 삭제")
    @RequireRole(SubjectRole.OWNER)
    @DeleteMapping("/guide/entries/{entryId}")
    public ApiResponse<Void> deleteEntry(@PathVariable Long subjectId, @PathVariable Long entryId,
                                         @AuthenticationPrincipal Long userId) {
        guideService.deleteEntry(subjectId, entryId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "ITEM-01 선택 항목 승인(수정 포함) → 설명서 반영")
    @RequireRole(SubjectRole.OWNER)
    @PostMapping("/diaries/{diaryId}/items/review")
    public ApiResponse<ReviewResponse> review(@PathVariable Long subjectId, @PathVariable Long diaryId,
                                              @AuthenticationPrincipal Long userId,
                                              @RequestBody ReviewRequest request) {
        return ApiResponse.ok(itemReviewService.review(subjectId, diaryId, userId, request));
    }
}
