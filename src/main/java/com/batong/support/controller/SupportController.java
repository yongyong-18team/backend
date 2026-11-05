package com.batong.support.controller;

import com.batong.global.response.ApiResponse;
import com.batong.support.dto.SupportDtos.BurnoutGuideResponse;
import com.batong.support.dto.SupportDtos.CrisisGuideResponse;
import com.batong.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "돌봄 지원")
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @Operation(summary = "SUP-01 소진 지원 안내", description = "화면 5.4. 로그인 필요")
    @GetMapping("/burnout-guide")
    public ApiResponse<BurnoutGuideResponse> getBurnoutGuide() {
        return ApiResponse.ok(supportService.getBurnoutGuide());
    }

    @Operation(summary = "SUP-02 위기 안전 안내", description = "화면 5.5. 로그인 필요")
    @GetMapping("/crisis-guide")
    public ApiResponse<CrisisGuideResponse> getCrisisGuide() {
        return ApiResponse.ok(supportService.getCrisisGuide());
    }
}
