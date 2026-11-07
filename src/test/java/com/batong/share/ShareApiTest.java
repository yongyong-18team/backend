package com.batong.share;

import com.batong.TestcontainersConfiguration;
import com.batong.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SHARE-01·02: 섹션 옵션, 초안·고정본, 권한, 문서 유형별 입력 검증. 지역·출생 연도는 고정본에 없어야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ShareApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long ownerId;
    long coId;
    long strangerId;
    long subjectId;
    long otherSubjectId;

    @BeforeEach
    void setUp() {
        ownerId = user("share_owner");
        coId = user("share_co");
        strangerId = user("share_other");
        subjectId = subject(ownerId, "어머니 010-0000-0000");
        otherSubjectId = subject(strangerId, null);
        long invitationId = jdbc.sql("""
                INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id""")
                .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
                .param("s", subjectId).param("c", coId).param("i", invitationId).update();
        guide("COMMUNICATION", "손짓으로 원하는 것을 알려줌");
        guide("BEHAVIOR_RESPONSE", "공사 소음에 불안해함");
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'share\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private long subject(long owner, String emergency) {
        return jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, dong, emergency_contact, created_at, updated_at)
                VALUES (:o, '가나', 2001, 'SUJI', '풍덕천동', :e, now(), now()) RETURNING subject_id""")
                .param("o", owner).param("e", emergency).query(Long.class).single();
    }

    private void guide(String section, String content) {
        jdbc.sql("INSERT INTO guide_entry (subject_id, section, content, source_type, created_at, updated_at) VALUES (:s, :sec, :c, 'MANUAL', now(), now())")
                .param("s", subjectId).param("sec", section).param("c", content).update();
    }

    private long diary(long subject, String date) {
        return jdbc.sql("""
                INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at)
                VALUES (:s, CAST(:d AS date), '가상 일기 본문', 'TEXT', 'DONE', now(), now()) RETURNING diary_id""")
                .param("s", subject).param("d", date).query(Long.class).single();
    }

    private void item(long diaryId, int no, String category, String content, String status) {
        jdbc.sql("""
                INSERT INTO extracted_item (diary_id, item_no, category, content, evidence_quote, status, edited)
                VALUES (:d, :n, :c, :t, '근거', :st, false)""")
                .param("d", diaryId).param("n", no).param("c", category).param("t", content).param("st", status).update();
    }

    private String bearer(long userId) {
        return "Bearer " + jwt.issue(userId);
    }

    private ResultActions options(long userId, long subject, String query) throws Exception {
        return mvc.perform(get("/api/v1/subjects/" + subject + "/shares/section-options?" + query)
                .header("Authorization", bearer(userId)));
    }

    private ResultActions draft(long userId, long subject, String json) throws Exception {
        return mvc.perform(post("/api/v1/subjects/" + subject + "/shares/drafts").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 섹션_옵션은_7개를_주고_내용이_있는_섹션만_기본_ON이다() throws Exception {
        options(ownerId, subjectId, "documentType=GUIDE").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentType").value("GUIDE"))
                .andExpect(jsonPath("$.data.options", hasSize(7)))
                .andExpect(jsonPath("$.data.options[0].sectionCode").value("PROHIBITED"))
                .andExpect(jsonPath("$.data.options[0].hasContent").value(false))
                .andExpect(jsonPath("$.data.options[0].defaultOn").value(false))
                .andExpect(jsonPath("$.data.options[2].sectionCode").value("COMMUNICATION"))
                .andExpect(jsonPath("$.data.options[2].hasContent").value(true))
                .andExpect(jsonPath("$.data.options[2].defaultOn").value(true))
                .andExpect(jsonPath("$.data.emergencyContact.available").value(true))
                .andExpect(jsonPath("$.data.emergencyContact.defaultOn").value(false));
    }

    @Test
    void 비상_연락처가_없으면_available이_false다() throws Exception {
        jdbc.sql("UPDATE care_subject SET emergency_contact = NULL WHERE subject_id = :s").param("s", subjectId).update();
        options(coId, subjectId, "documentType=GUIDE").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emergencyContact.available").value(false));
    }

    @Test
    void 무관한_사용자는_옵션도_초안도_403이다() throws Exception {
        options(strangerId, subjectId, "documentType=GUIDE").andExpect(status().isForbidden());
        draft(strangerId, subjectId, "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"COMMUNICATION\"]}")
                .andExpect(status().isForbidden());
    }

    @Test
    void 초안은_선택한_섹션만_순서대로_담고_이름_나이만_넣고_비상_연락처는_켠_경우에만_넣는다() throws Exception {
        String body = "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"COMMUNICATION\",\"BEHAVIOR_RESPONSE\"],"
                + "\"showEmergencyContact\":true,\"recipientLabel\":\"  목요일 선생님 \"}";
        draft(ownerId, subjectId, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shareId").isNumber())
                .andExpect(jsonPath("$.data.validDays").value(7))
                .andExpect(jsonPath("$.data.preview.documentType").value("GUIDE"))
                .andExpect(jsonPath("$.data.preview.summaryLines", hasSize(0)))
                .andExpect(jsonPath("$.data.preview.basicInfo.name").value("가나"))
                .andExpect(jsonPath("$.data.preview.basicInfo.emergencyContact").value("어머니 010-0000-0000"))
                .andExpect(jsonPath("$.data.preview.basicInfo.district").doesNotExist())
                .andExpect(jsonPath("$.data.preview.basicInfo.birthYear").doesNotExist())
                .andExpect(jsonPath("$.data.preview.sections", hasSize(2)))
                .andExpect(jsonPath("$.data.preview.sections[0].section").value("COMMUNICATION"))
                .andExpect(jsonPath("$.data.preview.sections[1].entries[0]").value("공사 소음에 불안해함"));

        draft(coId, subjectId, "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"COMMUNICATION\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.preview.basicInfo.emergencyContact").doesNotExist());
    }

    @Test
    void 초안은_승인_전_상태로_고정본과_함께_저장된다() throws Exception {
        draft(ownerId, subjectId,
                "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"COMMUNICATION\"],\"recipientLabel\":\" 선생님 \"}")
                .andExpect(status().isCreated());
        var row = jdbc.sql("""
                SELECT created_by, recipient_label, approved_at, token_hash, valid_days, show_emergency_contact,
                       snapshot_json::text AS snap,
                       (SELECT string_agg(section_code, ',') FROM share_section ss WHERE ss.share_id = s.share_id) AS secs
                  FROM share s WHERE subject_id = :s""").param("s", subjectId).query().singleRow();
        assertThat(row.get("created_by")).isEqualTo(ownerId);
        assertThat(row.get("recipient_label")).isEqualTo("선생님");
        assertThat(row.get("approved_at")).isNull();
        assertThat(row.get("token_hash")).isNull();
        assertThat(((Number) row.get("valid_days")).intValue()).isEqualTo(7);
        assertThat(row.get("show_emergency_contact")).isEqualTo(false);
        assertThat(row.get("secs")).isEqualTo("COMMUNICATION");
        assertThat((String) row.get("snap")).contains("손짓으로 원하는 것을 알려줌").doesNotContain("풍덕천동");
    }

    @Test
    void 섹션을_안_고르면_NO_SECTION이고_내용_없는_섹션을_고르면_NO_APPROVED_CONTENT다() throws Exception {
        draft(ownerId, subjectId, "{\"documentType\":\"GUIDE\",\"sectionCodes\":[]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SHARE_NO_SECTION"));
        draft(ownerId, subjectId, "{\"documentType\":\"GUIDE\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SHARE_NO_SECTION"));
        draft(ownerId, subjectId, "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"COMMUNICATION\",\"PROHIBITED\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SHARE_NO_APPROVED_CONTENT"));
    }

    @Test
    void 잘못된_입력은_400이다() throws Exception {
        draft(ownerId, subjectId, "{\"sectionCodes\":[\"COMMUNICATION\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        draft(ownerId, subjectId, "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"WEEKLY_SUMMARY\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        draft(ownerId, subjectId, "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"COMMUNICATION\",\"COMMUNICATION\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        draft(ownerId, subjectId, "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"COMMUNICATION\"],\"recipientLabel\":\""
                + "가".repeat(31) + "\"}").andExpect(status().isBadRequest());
        draft(ownerId, subjectId, "{\"documentType\":\"WEEKLY\",\"periodStart\":\"2026-11-02\",\"sectionCodes\":[\"WEEKLY_SUMMARY\"]}")
                .andExpect(status().isBadRequest());
        options(ownerId, subjectId, "documentType=WEEKLY").andExpect(status().isBadRequest());
    }

    @Test
    void recipientLabel에_NUL_문자가_있으면_500이_아니라_400이다() throws Exception {
        draft(ownerId, subjectId, "{\"documentType\":\"GUIDE\",\"sectionCodes\":[\"COMMUNICATION\"],\"recipientLabel\":\"a\\u0000b\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
    }

    @Test
    void DAILY는_일기_ID가_필수이고_그_일기의_승인_항목만_섹션별로_담는다() throws Exception {
        long diaryId = diary(subjectId, "2026-11-03");
        item(diaryId, 1, "TRIGGER", "옆 건물 공사 소리가 나자 불안해함", "APPROVED");
        item(diaryId, 2, "BEHAVIOR", "30분 넘게 소리를 지름", "APPROVED");
        item(diaryId, 3, "RESPONSE", "차에 태워 트로트를 틀어주니 조용해짐", "EXCLUDED");
        item(diaryId, 4, "MEAL", "저녁을 반 공기 먹음", "PENDING");

        options(ownerId, subjectId, "documentType=DAILY").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SHARE_SOURCE_REQUIRED"));
        options(coId, subjectId, "documentType=DAILY&sourceDiaryId=" + diaryId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentType").value("DAILY"))
                .andExpect(jsonPath("$.data.options[3].sectionCode").value("BEHAVIOR_RESPONSE"))
                .andExpect(jsonPath("$.data.options[3].hasContent").value(true))
                .andExpect(jsonPath("$.data.options[5].sectionCode").value("MEDICATION"))
                .andExpect(jsonPath("$.data.options[5].hasContent").value(false));

        draft(coId, subjectId, "{\"documentType\":\"DAILY\",\"sourceDiaryId\":" + diaryId
                + ",\"sectionCodes\":[\"BEHAVIOR_RESPONSE\"]}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.preview.documentType").value("DAILY"))
                .andExpect(jsonPath("$.data.preview.sections[0].entries", hasSize(2)))
                .andExpect(jsonPath("$.data.preview.sections[0].entries[1]").value("30분 넘게 소리를 지름"));
        draft(ownerId, subjectId, "{\"documentType\":\"DAILY\",\"sourceDiaryId\":" + diaryId
                + ",\"sectionCodes\":[\"MEDICATION\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SHARE_NO_APPROVED_CONTENT"));
        draft(ownerId, subjectId, "{\"documentType\":\"DAILY\",\"sectionCodes\":[\"BEHAVIOR_RESPONSE\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SHARE_SOURCE_REQUIRED"));
        assertThat(jdbc.sql("SELECT source_diary_id FROM share WHERE document_type = 'DAILY' AND subject_id = :s")
                .param("s", subjectId).query(Long.class).single()).isEqualTo(diaryId);
    }

    @Test
    void DAILY는_다른_대상자의_일기나_없는_일기를_거절한다() throws Exception {
        long otherDiary = diary(otherSubjectId, "2026-11-03");
        item(otherDiary, 1, "BEHAVIOR", "남의 항목", "APPROVED");
        draft(ownerId, subjectId, "{\"documentType\":\"DAILY\",\"sourceDiaryId\":" + otherDiary
                + ",\"sectionCodes\":[\"BEHAVIOR_RESPONSE\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SHARE_SOURCE_REQUIRED"));
        options(ownerId, subjectId, "documentType=DAILY&sourceDiaryId=999999").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SHARE_SOURCE_REQUIRED"));
    }
}
