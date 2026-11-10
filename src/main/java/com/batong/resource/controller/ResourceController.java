package com.batong.resource.controller;

import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.enums.CareTerm;
import com.batong.global.enums.District;
import com.batong.global.enums.ResourceKind;
import com.batong.global.response.ApiResponse;
import com.batong.resource.dto.ResourceDtos.DetailResponse;
import com.batong.resource.dto.ResourceDtos.SearchResponse;
import com.batong.resource.service.ResourceService;
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

@Tag(name = "돌봄 지원")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @Operation(summary = "RES-02 조건 검색", description = "화면 5.1→5.2, 5.0→5.2. 저장하지 않는다")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/subjects/{subjectId}/resources/search")
    public ApiResponse<SearchResponse> search(
        @PathVariable Long subjectId,
        @RequestParam(required = false) CareTerm careTerm,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate neededDate,
        @RequestParam(required = false) District district,
        @RequestParam(required = false) String dong,
        @RequestParam(required = false) ResourceKind kind) {
        return ApiResponse.ok(resourceService.search(subjectId, careTerm, neededDate, district, dong, kind));
    }

    @Operation(summary = "RES-01 기관·제도 상세", description = "화면 5.3. 로그인 필요")
    @GetMapping("/resources/{resourceId}")
    public ApiResponse<DetailResponse> getDetail(@PathVariable Long resourceId) {
        return ApiResponse.ok(resourceService.getDetail(resourceId));
    }
}
