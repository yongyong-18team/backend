package com.batong.auth.dto;

import com.batong.global.enums.Relation;
import com.batong.user.dto.SubjectSummary;

import java.util.List;

/** AUTH-01~03 요청·응답 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(String loginId, String password) {
    }

    public record SignupResponse(Long userId) {
    }

    public record LoginRequest(String loginId, String password) {
    }

    public record LoginResponse(String accessToken, long expiresIn, LoginUser user,
                                List<SubjectSummary> subjects, int pendingInvitationCount) {
    }

    public record LoginUser(Long userId, String loginId, String displayName, Relation relation) {
    }
}
