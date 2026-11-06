package com.batong.resource.controller;

import com.batong.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자")
@RestController
@RequestMapping("/api/v1")
public class PublicDataAdminController {

    @Operation(summary = "ADMIN-01 공공데이터 적재·갱신",
        description = "스텁. X-Admin-Key는 AdminKeyFilter가 검사한다. 실제 적재는 이후 PR")
    @Parameter(name = "X-Admin-Key", in = ParameterIn.HEADER, required = true)
    @PostMapping("/admin/public-data/sync")
    public ApiResponse<Void> sync() {
        return ApiResponse.ok(null);
    }
}
