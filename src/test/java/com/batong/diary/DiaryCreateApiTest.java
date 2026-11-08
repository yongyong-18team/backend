package com.batong.diary;

import com.batong.TestcontainersConfiguration;
import com.batong.global.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DIARY-01: 저장 내용·상태, 빈 본문, 저장할 수 없는 문자, 필수 값, 역할(TRD 7-2: OWNER만) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class DiaryCreateApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ObjectMapper objectMapper;

    long subjectId;
    String owner;
    String coManager;
    String stranger;

    @BeforeEach
    void seed() {
        long ownerId = user("diary_owner");
        long coId = user("diary_co");
        long strangerId = user("diary_other");
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
        stranger = "Bearer " + jwt.issue(strangerId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'diary\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private ResultActions create(String token, String json) throws Exception {
        var req = post("/api/v1/subjects/" + subjectId + "/diaries").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            req.header("Authorization", token);
        }
        return mvc.perform(req);
    }

    private static String body(String diaryDate, String contentJson, String inputType) {
        return "{\"diaryDate\":" + diaryDate + ",\"content\":" + contentJson + ",\"inputType\":" + inputType + "}";
    }

    private int diaryCount() {
        return jdbc.sql("SELECT count(*) FROM diary WHERE subject_id = :s").param("s", subjectId)
                .query(Integer.class).single();
    }

    @Test
    void 저장하면_201과_PENDING이고_본문_앞뒤_공백은_잘린다() throws Exception {
        String response = create(owner, body("\"2026-11-03\"", "\"  오후에 옆 건물 공사 소리가 났다.\\n \"", "\"VOICE\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.analysisStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long diaryId = objectMapper.readTree(response).at("/data/diaryId").asLong();

        Map<String, Object> row = jdbc.sql("SELECT subject_id, diary_date, content, input_type, analysis_status FROM diary WHERE diary_id = :d")
                .param("d", diaryId).query().singleRow();
        assertThat(row.get("subject_id")).isEqualTo(subjectId);
        assertThat(row.get("diary_date").toString()).isEqualTo("2026-11-03");
        assertThat(row.get("content")).isEqualTo("오후에 옆 건물 공사 소리가 났다.");
        assertThat(row.get("input_type")).isEqualTo("VOICE");
        assertThat(row.get("analysis_status")).isEqualTo("PENDING");
    }

    @Test
    void 같은_날짜에_여러_개_저장할_수_있고_미래_날짜도_막지_않는다() throws Exception {
        String tomorrow = "\"" + LocalDate.now().plusDays(1) + "\"";
        create(owner, body("\"2026-11-03\"", "\"첫 번째\"", "\"TEXT\"")).andExpect(status().isCreated());
        create(owner, body("\"2026-11-03\"", "\"두 번째\"", "\"TEXT\"")).andExpect(status().isCreated());
        create(owner, body(tomorrow, "\"내일\"", "\"TEXT\"")).andExpect(status().isCreated());

        assertThat(diaryCount()).isEqualTo(3);
    }

    @Test
    void 본문이_없거나_공백뿐이면_DIARY_EMPTY_CONTENT() throws Exception {
        for (String content : new String[]{"\"\"", "\"   \\n\\t \"", "null"}) {
            create(owner, body("\"2026-11-03\"", content, "\"TEXT\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("DIARY_EMPTY_CONTENT"));
        }
        assertThat(diaryCount()).isZero();
    }

    @Test
    void NUL_문자나_짝_없는_서로게이트는_500이_아니라_400이다() throws Exception {
        for (String content : new String[]{"\"a\\u0000b\"", "\"a\\ud800b\"", "\"a\\udfffb\""}) {
            create(owner, body("\"2026-11-03\"", content, "\"TEXT\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        }
        create(owner, body("\"2026-11-03\"", "\"귤 \\ud83c\\udf4a 먹었다\"", "\"TEXT\"")).andExpect(status().isCreated());

        assertThat(diaryCount()).isEqualTo(1);
    }

    @Test
    void 날짜나_입력_방식이_없거나_틀리면_COMMON_VALIDATION() throws Exception {
        for (String json : new String[]{
                body("null", "\"내용\"", "\"TEXT\""),
                body("\"2026-11-03\"", "\"내용\"", "null"),
                body("\"2026-11-03\"", "\"내용\"", "\"PHOTO\""),
                body("\"2026-13-40\"", "\"내용\"", "\"TEXT\"")}) {
            create(owner, json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        }
        assertThat(diaryCount()).isZero();
    }

    @Test
    void 공동_관리자와_무관한_사용자는_403_토큰이_없으면_401() throws Exception {
        String json = body("\"2026-11-03\"", "\"내용\"", "\"TEXT\"");
        create(coManager, json).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"));
        create(stranger, json).andExpect(status().isForbidden());
        create(null, json).andExpect(status().isUnauthorized());

        assertThat(diaryCount()).isZero();
    }
}
