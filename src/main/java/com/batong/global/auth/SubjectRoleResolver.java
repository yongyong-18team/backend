package com.batong.global.auth;

import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * TRD 7-2 역할 판정. 요청마다 DB를 본다(캐시 없음) — 공동 관리자 해제·탈퇴가 즉시 반영돼야 한다.
 * 인터셉터와 서비스 계층 재확인이 같은 메서드를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SubjectRoleResolver {

    private final JdbcClient jdbc;

    /** OWNER, CO_MANAGER, 둘 다 아니면(대상자가 없는 경우 포함) empty */
    public Optional<SubjectRole> resolve(Long subjectId, Long userId) {
        return jdbc.sql("""
                        SELECT CASE
                            WHEN EXISTS (SELECT 1 FROM care_subject WHERE subject_id = :s AND owner_user_id = :u) THEN 'OWNER'
                            WHEN EXISTS (SELECT 1 FROM co_manager WHERE subject_id = :s AND user_id = :u) THEN 'CO_MANAGER'
                        END
                        """)
                .param("s", subjectId)
                .param("u", userId)
                .query(String.class)
                .optional()
                .map(SubjectRole::valueOf);
    }

    /** 허용 역할이 아니면 COMMON_FORBIDDEN. 없는 대상자도 같은 403으로 존재 여부를 숨긴다. */
    public SubjectRole require(Long subjectId, Long userId, SubjectRole... allowed) {
        return resolve(subjectId, userId)
                .filter(role -> Arrays.asList(allowed).contains(role))
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_FORBIDDEN));
    }
}
