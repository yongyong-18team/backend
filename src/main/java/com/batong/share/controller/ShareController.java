package com.batong.share.controller;

import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.enums.DocumentType;
import com.batong.global.response.ApiResponse;
import com.batong.share.dto.ShareDtos.ApproveResponse;
import com.batong.share.dto.ShareDtos.DraftRequest;
import com.batong.share.dto.ShareDtos.DraftResponse;
import com.batong.share.dto.ShareDtos.PublicShareResponse;
import com.batong.share.dto.ShareDtos.RevokeResponse;
import com.batong.share.dto.ShareDtos.SectionOptionsResponse;
import com.batong.share.dto.ShareDtos.ShareListResponse;
import com.batong.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "공유")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @Operation(summary = "SHARE-01 문서 유형별 공개 가능 섹션")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/subjects/{subjectId}/shares/section-options")
    public ApiResponse<SectionOptionsResponse> getSectionOptions(
            @PathVariable Long subjectId, @AuthenticationPrincipal Long userId,
            @RequestParam DocumentType documentType,
            @RequestParam(required = false) Long sourceDiaryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart) {
        return ApiResponse.ok(shareService.getSectionOptions(subjectId, userId, documentType, sourceDiaryId, periodStart));
    }

    @Operation(summary = "SHARE-02 공유 초안 + 미리보기")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/subjects/{subjectId}/shares/drafts")
    public ApiResponse<DraftResponse> createDraft(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId,
                                                  @RequestBody DraftRequest request) {
        return ApiResponse.ok(shareService.createDraft(subjectId, userId, request));
    }

    @Operation(summary = "SHARE-03 승인, 링크 발급", description = "초안 생성자만. shareUrl은 이 응답에서 한 번만 제공")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @PostMapping("/subjects/{subjectId}/shares/{shareId}/approve")
    public ApiResponse<ApproveResponse> approve(@PathVariable Long subjectId, @PathVariable Long shareId,
                                                @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(shareService.approve(subjectId, shareId, userId));
    }

    @Operation(summary = "SHARE-04 공유 내역")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/subjects/{subjectId}/shares")
    public ApiResponse<ShareListResponse> getShares(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(shareService.getShares(subjectId, userId));
    }

    @Operation(summary = "SHARE-05 링크 접근 중지", description = "OWNER는 전체, CO는 본인 생성분")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @PostMapping("/subjects/{subjectId}/shares/{shareId}/revoke")
    public ApiResponse<RevokeResponse> revoke(@PathVariable Long subjectId, @PathVariable Long shareId,
                                              @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(shareService.revoke(subjectId, shareId, userId));
    }

    @Operation(summary = "PUB-01 수신자 열람 (고정본만)")
    @SecurityRequirements
    @GetMapping("/public/shares/{token}")
    public ResponseEntity<ApiResponse<PublicShareResponse>> getPublicShare(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .body(ApiResponse.ok(shareService.getPublicShare(token)));
    }
}
