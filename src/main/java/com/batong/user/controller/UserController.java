package com.batong.user.controller;

import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.response.ApiResponse;
import com.batong.user.dto.UserDtos.HomeResponse;
import com.batong.user.dto.UserDtos.MeResponse;
import com.batong.user.dto.UserDtos.ProfileUpdateRequest;
import com.batong.user.dto.UserDtos.ProfileUpdateResponse;
import com.batong.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증·사용자")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "USER-01 내 정보")
    @GetMapping("/users/me")
    public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getMe(userId));
    }

    @Operation(summary = "USER-02 보호자 프로필 수정")
    @PatchMapping("/users/me")
    public ApiResponse<ProfileUpdateResponse> updateProfile(@AuthenticationPrincipal Long userId,
                                                            @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(userService.updateProfile(userId, request));
    }

    @Operation(summary = "USER-03 탈퇴")
    @DeleteMapping("/users/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "HOME-01 홈 화면 데이터")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/subjects/{subjectId}/home")
    public ApiResponse<HomeResponse> getHome(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getHome(subjectId, userId));
    }
}
