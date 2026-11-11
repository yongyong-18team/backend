package com.batong.user.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.enums.Relation;
import com.batong.user.dto.SubjectSummary;
import com.batong.user.dto.UserDtos.HomeResponse;
import com.batong.user.dto.UserDtos.MeResponse;
import com.batong.user.dto.UserDtos.ProfileUpdateRequest;
import com.batong.user.dto.UserDtos.ProfileUpdateResponse;
import com.batong.user.dto.UserDtos.RecentActivity;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** USER-01~03, HOME-01 */
@Service
public class UserService {

    private static final int MAX_NAME = 30;

    private final JdbcClient jdbc;
    private final SubjectRoleResolver roles;

    public UserService(JdbcClient jdbc, SubjectRoleResolver roles) {
        this.jdbc = jdbc;
        this.roles = roles;
    }

    /** 토큰은 유효한데 계정이 없으면(탈퇴 직후) 다시 로그인하게 401 */
    public MeResponse getMe(Long userId) {
        record Row(String loginId, String displayName, String relation) { }
        Row row = jdbc.sql("SELECT login_id, display_name, relation FROM users WHERE user_id = :u").param("u", userId)
                .query((rs, n) -> new Row(rs.getString("login_id"), rs.getString("display_name"), rs.getString("relation")))
                .optional().orElseThrow(() -> new BusinessException(ErrorCode.COMMON_UNAUTHORIZED));
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
        return new MeResponse(userId, row.loginId(), row.displayName(),
                row.relation() == null ? null : Relation.valueOf(row.relation()), subjects, pending);
    }

    /** 명세 예시처럼 이름과 관계를 모두 받는다(부분 수정 없음). 이름은 앞뒤 공백을 지운 1~30자 */
    public ProfileUpdateResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        String name = request == null || request.displayName() == null ? null : request.displayName().strip();
        if (name == null || name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME
                || !storable(name) || request.relation() == null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        int n = jdbc.sql("UPDATE users SET display_name = :n, relation = :r, updated_at = :now WHERE user_id = :u")
                .param("n", name).param("r", request.relation().name())
                .param("now", Timestamp.from(Instant.now())).param("u", userId).update();
        if (n == 0) {
            throw new BusinessException(ErrorCode.COMMON_UNAUTHORIZED);
        }
        return new ProfileUpdateResponse(userId, name, request.relation());
    }

    /** NUL은 PostgreSQL이 저장하지 못하고, 짝 없는 서로게이트는 '?'로 바뀐다 */
    private static boolean storable(String s) {
        return s.codePoints().noneMatch(c -> c == 0 || (c >= 0xD800 && c <= 0xDFFF));
    }

    /** 하위 데이터는 FK CASCADE·SET NULL로 정리된다(ERD 탈퇴 규칙). 공유 링크는 대상자와 함께 사라져 무효가 된다 */
    @Transactional
    public void withdraw(Long userId) {
        int n = jdbc.sql("DELETE FROM users WHERE user_id = :u").param("u", userId).update();
        if (n == 0) {
            throw new BusinessException(ErrorCode.COMMON_UNAUTHORIZED);
        }
    }

    private static final int RECENT_LIMIT = 5;
    private static final int PREVIEW_LENGTH = 30;

    /**
     * 최근 활동은 diary_date 최신순 5건. OWNER는 모든 일기, CO는 승인 항목이 있는 일기만(누르면 REPORT-02)이며
     * preview는 null, pendingCount는 0이다(일기 원문·검토 상태 비공개).
     */
    public HomeResponse getHome(Long subjectId, Long userId) {
        SubjectRole role = roles.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        boolean owner = role == SubjectRole.OWNER;
        String name = jdbc.sql("SELECT name FROM care_subject WHERE subject_id = :s").param("s", subjectId)
                .query(String.class).single();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        boolean todayRecorded = jdbc.sql("SELECT EXISTS (SELECT 1 FROM diary WHERE subject_id = :s AND diary_date = :d)")
                .param("s", subjectId).param("d", today).query(Boolean.class).single();
        LocalDate firstDiaryDate = jdbc.sql("SELECT min(diary_date) FROM diary WHERE subject_id = :s")
                .param("s", subjectId).query(LocalDate.class).optional().orElse(null);
        Integer careDays = firstDiaryDate == null || firstDiaryDate.isAfter(today) ? null
                : (int) ChronoUnit.DAYS.between(firstDiaryDate, today) + 1;
        List<RecentActivity> activities = jdbc.sql("""
                SELECT d.diary_id, d.diary_date, d.content,
                       count(*) FILTER (WHERE i.status = 'APPROVED') AS approved,
                       count(*) FILTER (WHERE i.status = 'PENDING') AS pending
                FROM diary d LEFT JOIN extracted_item i ON i.diary_id = d.diary_id
                WHERE d.subject_id = :s
                GROUP BY d.diary_id
                HAVING :owner OR count(*) FILTER (WHERE i.status = 'APPROVED') > 0
                ORDER BY d.diary_date DESC, d.diary_id DESC
                LIMIT :n""")
                .param("s", subjectId).param("owner", owner).param("n", RECENT_LIMIT)
                .query((rs, n) -> new RecentActivity(rs.getLong("diary_id"), rs.getDate("diary_date").toLocalDate(),
                        owner ? preview(rs.getString("content")) : null, rs.getInt("approved"),
                        owner ? rs.getInt("pending") : 0))
                .list();
        int pendingInvitations = jdbc.sql("SELECT count(*) FROM invitation WHERE invitee_user_id = :u AND status = 'PENDING'")
                .param("u", userId).query(Integer.class).single();
        return new HomeResponse(role, name, todayRecorded, careDays, activities, pendingInvitations);
    }

    private static String preview(String content) {
        String flat = content.strip().replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= PREVIEW_LENGTH) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, PREVIEW_LENGTH)) + "...";
    }
}
