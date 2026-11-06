package com.batong.subject.controller;

import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.response.ApiResponse;
import com.batong.subject.dto.SubjectDtos.OnboardingRequest;
import com.batong.subject.dto.SubjectDtos.OnboardingResponse;
import com.batong.subject.dto.SubjectDtos.SubjectResponse;
import com.batong.subject.dto.SubjectDtos.SubjectUpdateRequest;
import com.batong.subject.dto.SubjectDtos.SubjectUpdateResponse;
import com.batong.subject.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "온보딩·대상자")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @Operation(summary = "ONB-01 온보딩 일괄 등록", description = "보호자 프로필 + 대상자 + 돌봄 프로필(Q1~Q5) + 동의 → 초기 설명서")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/onboarding")
    public ApiResponse<OnboardingResponse> onboard(@AuthenticationPrincipal Long userId,
                                                   @RequestBody OnboardingRequest request) {
        return ApiResponse.ok(subjectService.onboard(userId, request));
    }

    @Operation(summary = "SUBJ-02 대상자 기본 정보")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/subjects/{subjectId}")
    public ApiResponse<SubjectResponse> getSubject(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(subjectService.getSubject(subjectId, userId));
    }

    @Operation(summary = "SUBJ-03 대상자 기본 정보·비상 연락처 수정", description = "바꿀 값만 보낸다")
    @RequireRole(SubjectRole.OWNER)
    @PatchMapping("/subjects/{subjectId}")
    public ApiResponse<SubjectUpdateResponse> updateSubject(@PathVariable Long subjectId,
                                                            @AuthenticationPrincipal Long userId,
                                                            @RequestBody SubjectUpdateRequest request) {
        return ApiResponse.ok(subjectService.updateSubject(subjectId, userId, request));
    }
}
