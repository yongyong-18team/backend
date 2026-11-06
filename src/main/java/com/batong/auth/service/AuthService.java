package com.batong.auth.service;

import com.batong.auth.dto.AuthDtos.LoginRequest;
import com.batong.auth.dto.AuthDtos.LoginResponse;
import com.batong.auth.dto.AuthDtos.LoginUser;
import com.batong.auth.dto.AuthDtos.SignupRequest;
import com.batong.auth.dto.AuthDtos.SignupResponse;
import com.batong.global.auth.SubjectRole;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.global.security.JwtProvider;
import com.batong.user.domain.User;
import com.batong.user.dto.SubjectSummary;
import com.batong.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9]{4,20}$");
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginAttemptLimiter limiter;
    private final JdbcClient jdbc;

    public SignupResponse signup(SignupRequest request) {
        requireValidLoginId(request.loginId());
        if (request.password() == null || !PASSWORD.matcher(request.password()).matches()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_PASSWORD_FORMAT);
        }
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
        }
        try {
            User user = userRepository.saveAndFlush(
                    User.create(request.loginId(), passwordEncoder.encode(request.password())));
            return new SignupResponse(user.getId());
        } catch (DataIntegrityViolationException e) {
            // 동시 가입으로 UNIQUE에 걸린 경우
            throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
        }
    }

    /** 사용할 수 있으면 정상 반환, 이미 있으면 AUTH_DUPLICATE_LOGIN_ID(409) */
    public void checkLoginId(String loginId) {
        requireValidLoginId(loginId);
        if (userRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
        }
    }

    public LoginResponse login(LoginRequest request, String ip) {
        if (limiter.isBlocked(ip)) {
            throw new BusinessException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
        }
        User user = request.loginId() == null ? null : userRepository.findByLoginId(request.loginId()).orElse(null);
        // 아이디가 없어도 BCrypt를 한 번 돌려 응답 시간으로 존재 여부를 알 수 없게 한다
        String hash = user != null ? user.getPasswordHash() : dummyHash();
        boolean ok = request.password() != null && passwordEncoder.matches(request.password(), hash);
        if (user == null || !ok) {
            limiter.recordFailure(ip);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        limiter.reset(ip);

        Long userId = user.getId();
        List<SubjectSummary> subjects = jdbc.sql("""
                        SELECT subject_id, name, 'OWNER' AS role FROM care_subject WHERE owner_user_id = :u
                        UNION ALL
                        SELECT s.subject_id, s.name, 'CO_MANAGER' FROM co_manager c
                          JOIN care_subject s ON s.subject_id = c.subject_id WHERE c.user_id = :u
                        ORDER BY subject_id""")
                .param("u", userId)
                .query((rs, n) -> new SubjectSummary(rs.getLong("subject_id"), rs.getString("name"),
                        SubjectRole.valueOf(rs.getString("role"))))
                .list();
        int pending = jdbc.sql("SELECT count(*) FROM invitation WHERE invitee_user_id = :u AND status = 'PENDING'")
                .param("u", userId).query(Integer.class).single();

        return new LoginResponse(
                jwtProvider.issue(userId),
                jwtProvider.getAccessTtlSeconds(),
                new LoginUser(userId, user.getLoginId(), user.getDisplayName(), user.getRelation()),
                subjects,
                pending);
    }

    private static void requireValidLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_LOGIN_ID_FORMAT);
        }
    }

    // 어떤 계정의 값도 아닌 해시. 첫 사용 때 만든다(동시에 만들어져도 결과는 같은 성격)
    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("not-a-real-password-1");
        }
        return dummyHash;
    }
}
