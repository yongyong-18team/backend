package com.batong;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 백엔드 B 스텁: 경로·HTTP 상태·권한·응답 모양이 API 명세와 같은지. 실제 구현이 들어오면 도메인별 테스트로 옮긴다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class BackendBStubTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long subjectId;
    String owner;
    String coManager;

    @BeforeEach
    void seed() {
        long ownerId = user("bstub_owner");
        long coId = user("bstub_co");
        subjectId = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '민지', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
                .param("o", ownerId).query(Long.class).single();
        long invitationId = jdbc.sql("""
                INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id""")
                .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
                .param("s", subjectId).param("c", coId).param("i", invitationId).update();
        owner = "Bearer " + jwt.issue(ownerId);
        coManager = "Bearer " + jwt.issue(coId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'bstub\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private String diaries() {
        return "/api/v1/subjects/" + subjectId + "/diaries";
    }

    @Test
    void ONB_01은_201과_대상자_ID를_준다() throws Exception {
        mvc.perform(post("/api/v1/onboarding").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guardian":{"displayName":"김영희","relation":"PARENT"},
                                 "subject":{"name":"민지","birthYear":2001,"district":"SUJI","dong":"풍덕천동"},
                                 "profile":{"Q1":"아침 7시에 일어나요.","Q2":null,"Q3":null,"Q4":null,"Q5":null},
                                 "consents":[{"consentType":"PRIVACY","termsVersion":"1.0"}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjectId").value(10))
                .andExpect(jsonPath("$.data.createdGuideEntryCount").value(4));
    }

    @Test
    void SUBJ_02는_OWNER와_CO_모두_조회하고_SUBJ_03은_OWNER만() throws Exception {
        String path = "/api/v1/subjects/" + subjectId;
        mvc.perform(get(path).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.age").value(25))
                .andExpect(jsonPath("$.data.district").value("SUJI"));
        mvc.perform(get(path).header("Authorization", coManager)).andExpect(status().isOk());

        String body = "{\"emergencyContact\":\"어머니 010-0000-0000\"}";
        mvc.perform(patch(path).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-03T21:00:00+09:00"));
        mvc.perform(patch(path).header("Authorization", coManager).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void STT_01은_multipart_audio를_받아_텍스트를_준다() throws Exception {
        var audio = new MockMultipartFile("audio", "rec.webm", "audio/webm", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/stt").file(audio).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").isString());
    }

    @Test
    void STT_01은_로그인이_필요하다() throws Exception {
        var audio = new MockMultipartFile("audio", "rec.webm", "audio/webm", new byte[]{1});
        mvc.perform(multipart("/api/v1/stt").file(audio)).andExpect(status().isUnauthorized());
    }

    @Test
    void DIARY_01은_저장_후_PENDING을_준다() throws Exception {
        // DIARY-02는 실제 구현으로 바뀌어 DiaryAnalyzeApiTest가 검증한다
        mvc.perform(post(diaries()).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diaryDate\":\"2026-11-03\",\"content\":\"오후에 옆 건물 공사 소리가...\",\"inputType\":\"VOICE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.analysisStatus").value("PENDING"));
    }

    @Test
    void DIARY_03_04_05는_목록_상세_삭제() throws Exception {
        mvc.perform(get(diaries()).header("Authorization", owner).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.size").value(20));
        mvc.perform(get(diaries() + "/501").header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputType").value("VOICE"))
                .andExpect(jsonPath("$.data.items[0].edited").value(false));
        mvc.perform(delete(diaries() + "/501").header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 일기_API는_공동_관리자에게_403이다() throws Exception {
        mvc.perform(get(diaries()).header("Authorization", coManager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"));
        mvc.perform(get(diaries() + "/501").header("Authorization", coManager)).andExpect(status().isForbidden());
        mvc.perform(post(diaries() + "/501/analyze").header("Authorization", coManager)).andExpect(status().isForbidden());
    }
}
