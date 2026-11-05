package com.batong.user.controller;

import com.batong.global.response.ApiResponse;
import com.batong.user.dto.TermsResponse;
import com.batong.user.service.TermsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증·사용자")
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    @Operation(summary = "TERMS-01 동의 항목과 현재 버전", description = "화면 0.2-4. 공개 API")
    @SecurityRequirements
    @GetMapping
    public ApiResponse<TermsResponse> getTerms() {
        return ApiResponse.ok(termsService.getTerms());
    }
}
