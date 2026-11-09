package com.batong.diary;

import com.batong.TestcontainersConfiguration;
import com.batong.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DIARY-03 목록, DIARY-04 상세, DIARY-05 삭제: 필터·페이지·정렬, reviewStatus, 위기 숨김, 삭제 연쇄, 역할 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class DiaryQueryApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long ownerId;
    long subjectId;
    long otherSubjectId;
    String owner;
    String coManager;
    String stranger;

    @BeforeEach
    void seed() {
        ownerId = user("dq_owner");
        long coId = user("dq_co");
        long strangerId = user("dq_other");
        subjectId = subject(ownerId);
        otherSubjectId = subject(strangerId);
        long invitationId = jdbc.sql("""
                INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id""")
                .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
                .param("s", subjectId).param("c", coId).param("i", invitationId).update();
        owner = "Bearer " + jwt.issue(ownerId);
        coManager = "Bearer " + jwt.issue(coId);
        stranger = "Bearer " + jwt.issue(strangerId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'dq\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private long subject(long ownerUserId) {
        return jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '민지', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
                .param("o", ownerUserId).query(Long.class).single();
    }

    private long diary(long sid, String date, String content, String status) {
        return jdbc.sql("""
                INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at)
                VALUES (:s, CAST(:d AS date), :c, 'VOICE', :st, now(), now()) RETURNING diary_id""")
                .param("s", sid).param("d", date).param("c", content).param("st", status).query(Long.class).single();
    }

    private void analysis(long diaryId, int no, String safetyLevel, String empathy) {
        jdbc.sql("""
                INSERT INTO ai_analysis (diary_id, analysis_no, safety_level, empathy_text, raw_json, prompt_version, model_name, created_at)
                VALUES (:d, :n, :l, :e, '{"secret":"raw"}'::jsonb, 'v1', 'mock', now())""")
                .param("d", diaryId).param("n", no).param("l", safetyLevel).param("e", empathy).update();
    }

    private void item(long diaryId, int no, String category, String status, boolean edited) {
        jdbc.sql("""
                INSERT INTO extracted_item (diary_id, item_no, category, content, evidence_quote, status, edited)
                VALUES (:d, :n, :c, :t, '근거 문장', :st, :e)""")
                .param("d", diaryId).param("n", no).param("c", category).param("t", "항목 " + no)
                .param("st", status).param("e", edited).update();
    }

    private String diaries(long sid) {
        return "/api/v1/subjects/" + sid + "/diaries";
    }

    private ResultActions list(String token, Map<String, String> params) throws Exception {
        var req = get(diaries(subjectId)).header("Authorization", token);
        params.forEach(req::param);
        return mvc.perform(req);
    }

    private long count(String table, long diaryId) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE diary_id = :d").param("d", diaryId).query(Long.class).single();
    }

    // DIARY-03

    @Test
    void 목록은_날짜_최신순이고_같은_날은_나중_것이_먼저다() throws Exception {
        long older = diary(subjectId, "2026-11-01", "첫 일기", "PENDING");
        long sameDayFirst = diary(subjectId, "2026-11-03", "같은 날 첫째", "PENDING");
        long sameDaySecond = diary(subjectId, "2026-11-03", "같은 날 둘째", "PENDING");
        diary(otherSubjectId, "2026-11-04", "남의 일기", "PENDING");

        list(owner, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].diaryId").value(sameDaySecond))
                .andExpect(jsonPath("$.data.content[1].diaryId").value(sameDayFirst))
                .andExpect(jsonPath("$.data.content[2].diaryId").value(older))
                .andExpect(jsonPath("$.data.content[2].diaryDate").value("2026-11-01"));
    }

    @Test
    void from_to는_양끝을_포함하고_페이지는_size만큼_자른다() throws Exception {
        diary(subjectId, "2026-10-31", "범위 밖", "PENDING");
        long d1 = diary(subjectId, "2026-11-01", "1일", "PENDING");
        long d2 = diary(subjectId, "2026-11-02", "2일", "PENDING");
        long d3 = diary(subjectId, "2026-11-03", "3일", "PENDING");
        diary(subjectId, "2026-11-04", "범위 밖", "PENDING");

        list(owner, Map.of("from", "2026-11-01", "to", "2026-11-03", "size", "2"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].diaryId").value(d3))
                .andExpect(jsonPath("$.data.content[1].diaryId").value(d2));
        list(owner, Map.of("from", "2026-11-01", "to", "2026-11-03", "size", "2", "page", "1"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].diaryId").value(d1));
        list(owner, Map.of("from", "2026-11-04"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        list(owner, Map.of("to", "2026-10-31"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        list(owner, Map.of("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(5));
    }

    @Test
    void reviewStatus와_항목_수는_계산값이다() throws Exception {
        long notAnalyzed = diary(subjectId, "2026-11-01", "분석 전", "PENDING");
        long pending = diary(subjectId, "2026-11-02", "검토 대기", "DONE");
        analysis(pending, 1, "NORMAL", "공감");
        item(pending, 1, "TRIGGER", "PENDING", false);
        item(pending, 2, "RESPONSE", "APPROVED", false);
        item(pending, 3, "PREFERENCE", "EXCLUDED", false);
        long approved = diary(subjectId, "2026-11-03", "검토 끝", "DONE");
        analysis(approved, 1, "NORMAL", "공감");
        item(approved, 1, "TRIGGER", "APPROVED", true);
        long noItems = diary(subjectId, "2026-11-04", "항목 없음", "DONE");
        analysis(noItems, 1, "NORMAL", "공감");
        long failed = diary(subjectId, "2026-11-05", "실패", "FAILED");

        list(owner, Map.of())
                .andExpect(jsonPath("$.data.content[0].diaryId").value(failed))
                .andExpect(jsonPath("$.data.content[0].analysisStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.content[0].reviewStatus").value("NONE"))
                .andExpect(jsonPath("$.data.content[1].diaryId").value(noItems))
                .andExpect(jsonPath("$.data.content[1].reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.content[1].approvedCount").value(0))
                .andExpect(jsonPath("$.data.content[2].diaryId").value(approved))
                .andExpect(jsonPath("$.data.content[2].reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.content[2].approvedCount").value(1))
                .andExpect(jsonPath("$.data.content[3].diaryId").value(pending))
                .andExpect(jsonPath("$.data.content[3].reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.content[3].pendingCount").value(1))
                .andExpect(jsonPath("$.data.content[3].approvedCount").value(1))
                .andExpect(jsonPath("$.data.content[4].diaryId").value(notAnalyzed))
                .andExpect(jsonPath("$.data.content[4].analysisStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.content[4].reviewStatus").value("NONE"))
                .andExpect(jsonPath("$.data.content[4].pendingCount").value(0));
    }

    @Test
    void preview는_공백을_줄이고_30자에서_자른다() throws Exception {
        diary(subjectId, "2026-11-01", "  짧은\n\n일기  ", "PENDING");
        diary(subjectId, "2026-11-02", "가".repeat(30), "PENDING");
        diary(subjectId, "2026-11-03", "😀".repeat(31), "PENDING");

        list(owner, Map.of())
                .andExpect(jsonPath("$.data.content[0].preview").value("😀".repeat(30) + "..."))
                .andExpect(jsonPath("$.data.content[1].preview").value("가".repeat(30)))
                .andExpect(jsonPath("$.data.content[2].preview").value("짧은 일기"));
    }

    @Test
    void 잘못된_목록_요청은_400() throws Exception {
        for (Map<String, String> bad : List.of(
                Map.of("page", "-1"), Map.of("size", "0"), Map.of("size", "101"),
                Map.of("from", "2026-11-05", "to", "2026-11-01"), Map.of("from", "2026-13-01"), Map.of("size", "abc"))) {
            list(owner, bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        }
        list(owner, Map.of("size", "100")).andExpect(status().isOk());
        list(owner, Map.of("from", "2026-11-01", "to", "2026-11-01")).andExpect(status().isOk());
    }

    // DIARY-04

    @Test
    void 상세는_원문과_최근_공감_문장과_모든_항목을_준다() throws Exception {
        long id = diary(subjectId, "2026-11-03", "오후에 옆 건물 공사 소리가 났다.", "DONE");
        analysis(id, 1, "NORMAL", "예전 공감");
        analysis(id, 2, "NORMAL", "새 공감");
        item(id, 2, "RESPONSE", "PENDING", false);
        item(id, 1, "TRIGGER", "APPROVED", true);
        item(id, 3, "PREFERENCE", "EXCLUDED", false);

        mvc.perform(get(diaries(subjectId) + "/" + id).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diaryId").value(id))
                .andExpect(jsonPath("$.data.diaryDate").value("2026-11-03"))
                .andExpect(jsonPath("$.data.content").value("오후에 옆 건물 공사 소리가 났다."))
                .andExpect(jsonPath("$.data.inputType").value("VOICE"))
                .andExpect(jsonPath("$.data.analysisStatus").value("DONE"))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.empathyText").value("새 공감"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].itemNo").value(1))
                .andExpect(jsonPath("$.data.items[0].category").value("TRIGGER"))
                .andExpect(jsonPath("$.data.items[0].section").value("BEHAVIOR_RESPONSE"))
                .andExpect(jsonPath("$.data.items[0].content").value("항목 1"))
                .andExpect(jsonPath("$.data.items[0].evidenceQuote").value("근거 문장"))
                .andExpect(jsonPath("$.data.items[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.items[0].edited").value(true))
                .andExpect(jsonPath("$.data.items[1].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[2].status").value("EXCLUDED"))
                .andExpect(jsonPath("$.data.rawJson").doesNotExist())
                .andExpect(jsonPath("$.data.safetyLevel").doesNotExist());
    }

    @Test
    void 분석_전_일기는_공감과_항목이_없고_NONE이다() throws Exception {
        long id = diary(subjectId, "2026-11-03", "분석 전", "PENDING");

        mvc.perform(get(diaries(subjectId) + "/" + id).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("NONE"))
                .andExpect(jsonPath("$.data.empathyText").doesNotExist())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 최근_분석이_위기면_공감과_항목을_비우고_위기_여부는_내보내지_않는다() throws Exception {
        long id = diary(subjectId, "2026-11-03", "위기 일기", "DONE");
        analysis(id, 1, "NORMAL", "예전 공감");
        item(id, 1, "TRIGGER", "APPROVED", false);
        analysis(id, 2, "CRISIS", null);

        String body = mvc.perform(get(diaries(subjectId) + "/" + id).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("위기 일기"))
                .andExpect(jsonPath("$.data.empathyText").doesNotExist())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("CRISIS").doesNotContain("secret");
    }

    @Test
    void 다른_대상자의_일기나_없는_일기는_404() throws Exception {
        long others = diary(otherSubjectId, "2026-11-03", "남의 일기", "PENDING");

        mvc.perform(get(diaries(subjectId) + "/" + others).header("Authorization", owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
        mvc.perform(get(diaries(subjectId) + "/999999999").header("Authorization", owner))
                .andExpect(status().isNotFound());
    }

    // DIARY-05

    @Test
    void 삭제하면_분석과_항목은_지워지고_설명서_공유_소진_신호는_출처만_비운다() throws Exception {
        long id = diary(subjectId, "2026-11-03", "지울 일기", "DONE");
        analysis(id, 1, "NORMAL", "공감");
        item(id, 1, "TRIGGER", "APPROVED", false);
        long entryId = jdbc.sql("""
                INSERT INTO guide_entry (subject_id, section, content, source_type, src_diary_id, src_item_no, created_at, updated_at)
                VALUES (:s, 'BEHAVIOR_RESPONSE', '항목 1', 'DIARY', :d, 1, now(), now()) RETURNING entry_id""")
                .param("s", subjectId).param("d", id).query(Long.class).single();
        long shareId = jdbc.sql("""
                INSERT INTO share (subject_id, created_by, document_type, source_diary_id, created_at)
                VALUES (:s, :u, 'DAILY', :d, now()) RETURNING share_id""")
                .param("s", subjectId).param("u", ownerId).param("d", id).query(Long.class).single();
        long signalId = jdbc.sql("""
                INSERT INTO wellbeing_signal (user_id, diary_id, signal_type, detected_date, created_at)
                VALUES (:u, :d, 'BURNOUT', current_date, now()) RETURNING signal_id""")
                .param("u", ownerId).param("d", id).query(Long.class).single();

        mvc.perform(delete(diaries(subjectId) + "/" + id).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(jdbc.sql("SELECT count(*) FROM diary WHERE diary_id = :d").param("d", id).query(Long.class).single()).isZero();
        assertThat(count("ai_analysis", id)).isZero();
        assertThat(count("extracted_item", id)).isZero();
        var entry = jdbc.sql("SELECT content, src_diary_id, src_item_no FROM guide_entry WHERE entry_id = :e")
                .param("e", entryId).query().singleRow();
        assertThat(entry.get("content")).isEqualTo("항목 1");
        assertThat(entry.get("src_diary_id")).isNull();
        assertThat(entry.get("src_item_no")).isNull();
        assertThat(jdbc.sql("SELECT count(*) FROM share WHERE share_id = :i AND source_diary_id IS NULL")
                .param("i", shareId).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM wellbeing_signal WHERE signal_id = :i AND diary_id IS NULL")
                .param("i", signalId).query(Long.class).single()).isOne();

        mvc.perform(delete(diaries(subjectId) + "/" + id).header("Authorization", owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void 분석_중인_일기는_지우지_않고_409() throws Exception {
        long id = diary(subjectId, "2026-11-03", "분석 중", "PROCESSING");

        mvc.perform(delete(diaries(subjectId) + "/" + id).header("Authorization", owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DIARY_ALREADY_ANALYZING"));
        assertThat(jdbc.sql("SELECT count(*) FROM diary WHERE diary_id = :d").param("d", id).query(Long.class).single()).isOne();
    }

    @Test
    void 다른_대상자의_일기는_지우지_못한다() throws Exception {
        long others = diary(otherSubjectId, "2026-11-03", "남의 일기", "PENDING");

        mvc.perform(delete(diaries(subjectId) + "/" + others).header("Authorization", owner))
                .andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT count(*) FROM diary WHERE diary_id = :d").param("d", others).query(Long.class).single()).isOne();
    }

    // 역할

    @Test
    void 목록_상세_삭제는_OWNER만_된다() throws Exception {
        long id = diary(subjectId, "2026-11-03", "일기", "PENDING");
        for (String token : new String[]{coManager, stranger}) {
            mvc.perform(get(diaries(subjectId)).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(diaries(subjectId) + "/" + id).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(delete(diaries(subjectId) + "/" + id).header("Authorization", token)).andExpect(status().isForbidden());
        }
        mvc.perform(get(diaries(subjectId))).andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT count(*) FROM diary WHERE diary_id = :d").param("d", id).query(Long.class).single()).isOne();
    }
}
