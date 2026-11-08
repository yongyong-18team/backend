package com.batong.resource.controller;

import com.batong.global.response.ApiResponse;
import com.batong.resource.dto.PublicDataDtos.SyncResponse;
import com.batong.resource.service.PublicDataSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PublicDataAdminController {

    private final PublicDataSyncService publicDataSyncService;

    @Operation(summary = "ADMIN-01 공공데이터 적재·갱신",
        description = "수기 정리한 시설 시드 CSV를 (source, external_id) 기준으로 upsert한다. X-Admin-Key는 AdminKeyFilter가 검사한다")
    @Parameter(name = "X-Admin-Key", in = ParameterIn.HEADER, required = true)
    @PostMapping("/admin/public-data/sync")
    public ApiResponse<SyncResponse> sync() {
        return ApiResponse.ok(publicDataSyncService.sync());
    }
}
