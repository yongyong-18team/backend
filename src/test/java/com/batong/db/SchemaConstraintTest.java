package com.batong.db;

import com.batong.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V1 스키마의 제약이 ERD v1.4 규칙을 DB에서 막는지 본다. 각 테스트는 롤백된다. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SchemaConstraintTest {

    @Autowired
    JdbcTemplate jdbc;

    long ownerId;
    long inviteeId;
    long subjectId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("owner01");
        inviteeId = insertUser("invitee01");
        subjectId = jdbc.queryForObject("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (?, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id""", Long.class, ownerId);
    }

    private long insertUser(String loginId) {
        return jdbc.queryForObject("""
                INSERT INTO users (login_id, password_hash, created_at, updated_at)
                VALUES (?, 'x', now(), now()) RETURNING user_id""", Long.class, loginId);
    }

    private void invite(String status) {
        jdbc.update("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (?, ?, ?, now())",
                subjectId, inviteeId, status);
    }

    private long insertDiary() {
        return jdbc.queryForObject("""
                INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at)
                VALUES (?, DATE '2026-11-03', '가상 일기', 'TEXT', 'DONE', now(), now()) RETURNING diary_id""",
                Long.class, subjectId);
    }

    private void insertShare(String documentType, String periodStart, int validDays, String tokenHash, boolean approved) {
        jdbc.update("""
                INSERT INTO share (subject_id, created_by, document_type, period_start, valid_days, token_hash, approved_at, created_at)
                VALUES (?, ?, ?, CAST(? AS DATE), ?, ?, CASE WHEN ? THEN now() END, now())""",
                subjectId, ownerId, documentType, periodStart, validDays, tokenHash, approved);
    }

    @Test
    void 같은_대상자_같은_사람에게_대기_중_초대는_하나만() {
        invite("REJECTED");
        invite("PENDING");

        assertThatThrownBy(() -> invite("PENDING")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 공유_유효기간은_7일만() {
        insertShare("GUIDE", null, 7, null, false);

        assertThatThrownBy(() -> insertShare("GUIDE", null, 3, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 승인_여부와_토큰_해시는_함께_있거나_함께_없다() {
        insertShare("GUIDE", null, 7, "a".repeat(64), true);

        assertThatThrownBy(() -> insertShare("GUIDE", null, 7, null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 주간_공유는_시작일이_필요하다() {
        insertShare("WEEKLY", "2026-10-28", 7, null, false);

        assertThatThrownBy(() -> insertShare("WEEKLY", null, 7, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 위기_분석에는_공감_문장을_저장할_수_없다() {
        long diaryId = insertDiary();
        jdbc.update("""
                INSERT INTO ai_analysis (diary_id, analysis_no, safety_level, empathy_text, prompt_version, created_at)
                VALUES (?, 1, 'CRISIS', NULL, 'keyword-only', now())""", diaryId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ai_analysis (diary_id, analysis_no, safety_level, empathy_text, prompt_version, created_at)
                VALUES (?, 2, 'CRISIS', '공감 문장', 'v1', now())""", diaryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 대상자_이름은_2자_이상() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (?, '가', 2001, 'SUJI', now(), now())""", ownerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 출생_연도_1929년은_거부한다() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (?, '가나', 1929, 'SUJI', now(), now())""", ownerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 일기를_지우면_분석_항목은_삭제되고_설명서_출처와_공유_출처는_NULL이_된다() {
        long diaryId = insertDiary();
        jdbc.update("""
                INSERT INTO extracted_item (diary_id, item_no, category, content, evidence_quote, status)
                VALUES (?, 1, 'TRIGGER', '공사 소음에 불안해함', '공사 소리가 나니까', 'APPROVED')""", diaryId);
        long entryId = jdbc.queryForObject("""
                INSERT INTO guide_entry (subject_id, section, content, source_type, src_diary_id, src_item_no, created_at, updated_at)
                VALUES (?, 'BEHAVIOR_RESPONSE', '공사 소음에 불안해함', 'DIARY', ?, 1, now(), now()) RETURNING entry_id""",
                Long.class, subjectId, diaryId);
        long shareId = jdbc.queryForObject("""
                INSERT INTO share (subject_id, created_by, document_type, source_diary_id, created_at)
                VALUES (?, ?, 'DAILY', ?, now()) RETURNING share_id""", Long.class, subjectId, ownerId, diaryId);

        jdbc.update("DELETE FROM diary WHERE diary_id = ?", diaryId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM extracted_item WHERE diary_id = ?", Long.class, diaryId)).isZero();
        assertThat(jdbc.queryForObject("SELECT src_diary_id FROM guide_entry WHERE entry_id = ?", Long.class, entryId)).isNull();
        assertThat(jdbc.queryForObject("SELECT source_diary_id FROM share WHERE share_id = ?", Long.class, shareId)).isNull();
    }

    @Test
    void 주_보호자를_지우면_대상자_하위_데이터가_모두_삭제된다() {
        insertDiary();
        invite("PENDING");

        jdbc.update("DELETE FROM users WHERE user_id = ?", ownerId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_subject WHERE owner_user_id = ?", Long.class, ownerId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM diary WHERE subject_id = ?", Long.class, subjectId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invitation WHERE subject_id = ?", Long.class, subjectId)).isZero();
    }
}
