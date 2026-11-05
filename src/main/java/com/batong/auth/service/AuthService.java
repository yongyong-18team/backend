package com.batong.auth.service;

import com.batong.auth.dto.AuthDtos.LoginRequest;
import com.batong.auth.dto.AuthDtos.LoginResponse;
import com.batong.auth.dto.AuthDtos.LoginUser;
import com.batong.auth.dto.AuthDtos.SignupRequest;
import com.batong.auth.dto.AuthDtos.SignupResponse;
import com.batong.global.auth.SubjectRole;
import com.batong.global.enums.Relation;
import com.batong.global.security.JwtProvider;
import com.batong.user.dto.SubjectSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 스텁: API 명세 예시를 돌려준다. 실제 구현은 #6 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProvider jwtProvider;

    public SignupResponse signup(SignupRequest request) {
        return new SignupResponse(1L);
    }

    /** 사용할 수 있으면 정상 반환, 이미 있으면 AUTH_DUPLICATE_LOGIN_ID(409) */
    public void checkLoginId(String loginId) {
    }

    // ponytail: 스텁은 비밀번호를 보지 않고 userId 1 토큰을 준다. 프론트가 보호 API 스텁을 부를 수 있게 하려는 것. #6에서 반드시 교체
    public LoginResponse login(LoginRequest request) {
        return new LoginResponse(
                jwtProvider.issue(1L),
                jwtProvider.getAccessTtlSeconds(),
                new LoginUser(1L, "mom1234", "김영희", Relation.PARENT),
                List.of(new SubjectSummary(10L, "민지", SubjectRole.OWNER)),
                0);
    }
}
