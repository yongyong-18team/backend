package com.batong.auth.controller;

import com.batong.auth.dto.AuthDtos.LoginRequest;
import com.batong.auth.dto.AuthDtos.LoginResponse;
import com.batong.auth.dto.AuthDtos.SignupRequest;
import com.batong.auth.dto.AuthDtos.SignupResponse;
import com.batong.auth.service.AuthService;
import com.batong.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증·사용자")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "AUTH-01 회원가입")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @Operation(summary = "AUTH-02 ID 중복 확인", description = "사용 가능하면 200(data null), 이미 있으면 409 AUTH_DUPLICATE_LOGIN_ID")
    @GetMapping("/login-id/check")
    public ApiResponse<Void> checkLoginId(@RequestParam String loginId) {
        authService.checkLoginId(loginId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "AUTH-03 로그인", description = "스텁 단계: 아무 값이나 넣으면 userId 1 토큰을 준다")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
