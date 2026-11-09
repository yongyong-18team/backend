package com.batong.user;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** USER-01~03 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class UserApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long owner;
    long co;
    long lone;
    long subjectId;

    @BeforeEach
    void setUp() {
        owner = user("usrowner", "김영희", "PARENT");
        co = user("usrco", "이철수", "OTHER");
        lone = user("usrlone", null, null);
        subjectId = jdbc.sql("INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at) VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id")
                .param("o", owner).query(Long.class).single();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'usr%'").update();
    }

    private long user(String loginId, String name, String relation) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, display_name, relation, created_at, updated_at) VALUES (:l, 'x', :n, :r, now(), now()) RETURNING user_id")
                .param("l", loginId).param("n", name).param("r", relation).query(Long.class).single();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long userId) {
        return b.header("Authorization", "Bearer " + jwt.issue(userId)).contentType(MediaType.APPLICATION_JSON);
    }

    private long invitation(long toUser, String status) {
        return jdbc.sql("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (:s, :u, :st, now()) RETURNING invitation_id")
                .param("s", subjectId).param("u", toUser).param("st", status).query(Long.class).single();
    }

    // USER-01

    @Test
    void 내_정보는_대상자별_역할과_받은_초대_수를_준다() throws Exception {
        long inv = invitation(co, "ACCEPTED");
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :u, :i, now())")
                .param("s", subjectId).param("u", co).param("i", inv).update();
        invitation(lone, "PENDING");
        invitation(lone, "REJECTED");

        mvc.perform(as(get("/api/v1/users/me"), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(owner))
                .andExpect(jsonPath("$.data.loginId").value("usrowner"))
                .andExpect(jsonPath("$.data.displayName").value("김영희"))
                .andExpect(jsonPath("$.data.relation").value("PARENT"))
                .andExpect(jsonPath("$.data.subjects[0].subjectId").value(subjectId))
                .andExpect(jsonPath("$.data.subjects[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data.pendingInvitationCount").value(0))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        mvc.perform(as(get("/api/v1/users/me"), co)).andExpect(jsonPath("$.data.subjects[0].role").value("CO_MANAGER"));
        mvc.perform(as(get("/api/v1/users/me"), lone))
                .andExpect(jsonPath("$.data.subjects.length()").value(0))
                .andExpect(jsonPath("$.data.displayName").doesNotExist())
                .andExpect(jsonPath("$.data.pendingInvitationCount").value(1));
    }

    @Test
    void 토큰이_없거나_계정이_없으면_401() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/v1/users/me"), 987_654_321L)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"));
    }

    // USER-02

    @Test
    void 프로필을_수정하면_USER_01에_반영된다() throws Exception {
        mvc.perform(as(patch("/api/v1/users/me"), lone).content("{\"displayName\":\"  박순이 \",\"relation\":\"OTHER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(lone))
                .andExpect(jsonPath("$.data.displayName").value("박순이"))
                .andExpect(jsonPath("$.data.relation").value("OTHER"));
        mvc.perform(as(get("/api/v1/users/me"), lone))
                .andExpect(jsonPath("$.data.displayName").value("박순이"))
                .andExpect(jsonPath("$.data.relation").value("OTHER"));
    }

    @Test
    void 잘못된_프로필은_400이고_값이_바뀌지_않는다() throws Exception {
        String[] bad = {
                "{}", "{\"displayName\":\"가\"}", "{\"relation\":\"PARENT\"}",
                "{\"displayName\":\"\",\"relation\":\"PARENT\"}", "{\"displayName\":\"   \",\"relation\":\"PARENT\"}",
                "{\"displayName\":\"" + "가".repeat(31) + "\",\"relation\":\"PARENT\"}",
                "{\"displayName\":\"가\\u0000나\",\"relation\":\"PARENT\"}",
                "{\"displayName\":\"가\\ud800나\",\"relation\":\"PARENT\"}",
                "{\"displayName\":\"가나\",\"relation\":\"FRIEND\"}",
                "{\"displayName\":[\"가\"],\"relation\":\"PARENT\"}", "not json", "[]"
        };
        for (String body : bad) {
            ResultActions r = mvc.perform(as(patch("/api/v1/users/me"), owner).content(body));
            assertThat(r.andReturn().getResponse().getStatus()).as(body).isEqualTo(400);
        }
        mvc.perform(as(get("/api/v1/users/me"), owner)).andExpect(jsonPath("$.data.displayName").value("김영희"));
    }

    @Test
    void 이름은_30자까지_이모지도_허용한다() throws Exception {
        mvc.perform(as(patch("/api/v1/users/me"), owner).content("{\"displayName\":\"" + "가".repeat(30) + "\",\"relation\":\"PARENT\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(patch("/api/v1/users/me"), owner).content("{\"displayName\":\"엄마😀\",\"relation\":\"PARENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("엄마😀"));
    }

    // USER-03

    @Test
    void 주_보호자가_탈퇴하면_하위_데이터가_모두_삭제되고_CO의_공유는_남는다() throws Exception {
        long inv = invitation(co, "ACCEPTED");
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :u, :i, now())")
                .param("s", subjectId).param("u", co).param("i", inv).update();
        long diary = jdbc.sql("INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at) VALUES (:s, current_date, '가상 일기', 'TEXT', 'DONE', now(), now()) RETURNING diary_id")
                .param("s", subjectId).query(Long.class).single();
        jdbc.sql("INSERT INTO extracted_item (diary_id, item_no, category, content, evidence_quote, status) VALUES (:d, 1, 'TRIGGER', '소음', '가상', 'APPROVED')").param("d", diary).update();
        jdbc.sql("INSERT INTO guide_entry (subject_id, section, content, source_type, src_diary_id, src_item_no, created_at, updated_at) VALUES (:s, 'BEHAVIOR_RESPONSE', '소음에 불안', 'DIARY', :d, 1, now(), now())").param("s", subjectId).param("d", diary).update();
        jdbc.sql("INSERT INTO wellbeing_signal (user_id, diary_id, signal_type, detected_date, created_at) VALUES (:u, :d, 'BURNOUT', current_date, now())").param("u", owner).param("d", diary).update();
        jdbc.sql("INSERT INTO share (subject_id, created_by, document_type, snapshot_json, created_at) VALUES (:s, :u, 'GUIDE', '{}'::jsonb, now())").param("s", subjectId).param("u", owner).update();

        mvc.perform(as(delete("/api/v1/users/me"), owner)).andExpect(status().isOk());

        for (String sql : new String[]{
                "SELECT count(*) FROM users WHERE user_id = " + owner,
                "SELECT count(*) FROM care_subject WHERE subject_id = " + subjectId,
                "SELECT count(*) FROM diary WHERE subject_id = " + subjectId,
                "SELECT count(*) FROM guide_entry WHERE subject_id = " + subjectId,
                "SELECT count(*) FROM share WHERE subject_id = " + subjectId,
                "SELECT count(*) FROM invitation WHERE subject_id = " + subjectId,
                "SELECT count(*) FROM co_manager WHERE subject_id = " + subjectId,
                "SELECT count(*) FROM wellbeing_signal WHERE user_id = " + owner}) {
            assertThat(jdbc.sql(sql).query(Integer.class).single()).as(sql).isZero();
        }
        // CO 계정은 남고 더는 대상자가 없다
        mvc.perform(as(get("/api/v1/users/me"), co)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjects.length()").value(0));
        // 탈퇴한 계정의 토큰은 쓸 수 없다
        mvc.perform(as(get("/api/v1/users/me"), owner)).andExpect(status().isUnauthorized());
        mvc.perform(as(delete("/api/v1/users/me"), owner)).andExpect(status().isUnauthorized());
    }

    @Test
    void CO가_탈퇴하면_공동_관리자_행은_삭제되고_만든_공유는_작성자만_NULL이_된다() throws Exception {
        long inv = invitation(co, "ACCEPTED");
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :u, :i, now())")
                .param("s", subjectId).param("u", co).param("i", inv).update();
        long share = jdbc.sql("INSERT INTO share (subject_id, created_by, document_type, snapshot_json, token_hash, approved_at, expires_at, created_at) VALUES (:s, :u, 'GUIDE', '{}'::jsonb, :h, now(), now() + interval '7 days', now()) RETURNING share_id")
                .param("s", subjectId).param("u", co).param("h", "a".repeat(64)).query(Long.class).single();

        mvc.perform(as(delete("/api/v1/users/me"), co)).andExpect(status().isOk());

        assertThat(jdbc.sql("SELECT count(*) FROM co_manager WHERE subject_id = :s").param("s", subjectId).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT created_by IS NULL FROM share WHERE share_id = :i").param("i", share).query(Boolean.class).single()).isTrue();
        mvc.perform(as(get("/api/v1/subjects/" + subjectId + "/home"), owner)).andExpect(status().isOk());
    }
}
