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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HOME-01 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class HomeApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long owner;
    long co;
    long stranger;
    long subjectId;

    @BeforeEach
    void setUp() {
        owner = user("homeowner");
        co = user("homeco");
        stranger = user("homeother");
        subjectId = jdbc.sql("INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at) VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id")
                .param("o", owner).query(Long.class).single();
        long inv = jdbc.sql("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (:s, :u, 'ACCEPTED', now()) RETURNING invitation_id")
                .param("s", subjectId).param("u", co).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :u, :i, now())")
                .param("s", subjectId).param("u", co).param("i", inv).update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'home%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private long diary(LocalDate date, String content, String... itemStatuses) {
        long id = jdbc.sql("INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at) VALUES (:s, :d, :c, 'TEXT', 'DONE', now(), now()) RETURNING diary_id")
                .param("s", subjectId).param("d", date).param("c", content).query(Long.class).single();
        for (int i = 0; i < itemStatuses.length; i++) {
            jdbc.sql("INSERT INTO extracted_item (diary_id, item_no, category, content, evidence_quote, status) VALUES (:d, :n, 'TRIGGER', '항목', '근거', :st)")
                    .param("d", id).param("n", i + 1).param("st", itemStatuses[i]).update();
        }
        return id;
    }

    private ResultActions home(long userId) throws Exception {
        return mvc.perform(get("/api/v1/subjects/" + subjectId + "/home").header("Authorization", "Bearer " + jwt.issue(userId)));
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void 기록이_없으면_빈_목록과_todayRecorded_false() throws Exception {
        home(owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.name").value("가나"))
                .andExpect(jsonPath("$.data.todayRecorded").value(false))
                .andExpect(jsonPath("$.data.recentActivities.length()").value(0))
                .andExpect(jsonPath("$.data.pendingInvitationCount").value(0));
    }

    @Test
    void OWNER는_미리보기와_승인_대기_개수를_본다() throws Exception {
        long d = diary(today(), "오후에 옆 건물 공사 소리가 나서 아이가 귀를 막고 소리를 질렀어요 정말 힘들었어요", "APPROVED", "APPROVED", "PENDING", "EXCLUDED");
        home(owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayRecorded").value(true))
                .andExpect(jsonPath("$.data.recentActivities[0].diaryId").value(d))
                .andExpect(jsonPath("$.data.recentActivities[0].approvedCount").value(2))
                .andExpect(jsonPath("$.data.recentActivities[0].pendingCount").value(1))
                .andExpect(jsonPath("$.data.recentActivities[0].preview").value("오후에 옆 건물 공사 소리가 나서 아이가 귀를 막고 소리를 질렀어요 정말 힘들었어요".substring(0, 30) + "..."));
    }

    @Test
    void 짧은_일기는_그대로_줄바꿈은_공백으로() throws Exception {
        diary(today(), "  짧은\n\n  기록  ");
        home(owner).andExpect(jsonPath("$.data.recentActivities[0].preview").value("짧은 기록"));
    }

    @Test
    void 최근_5건만_최신순이다() throws Exception {
        for (int i = 0; i < 7; i++) {
            diary(today().minusDays(i), "기록 " + i, "APPROVED");
        }
        home(owner).andExpect(jsonPath("$.data.recentActivities.length()").value(5))
                .andExpect(jsonPath("$.data.recentActivities[0].preview").value("기록 0"))
                .andExpect(jsonPath("$.data.recentActivities[4].preview").value("기록 4"));
    }

    @Test
    void CO는_승인_항목이_있는_일기만_받고_원문과_대기_정보는_없다() throws Exception {
        diary(today(), "비공개 원문 내용", "PENDING");
        long approved = diary(today().minusDays(1), "다른 원문 내용", "APPROVED", "PENDING");
        diary(today().minusDays(2), "항목 없는 원문");
        String body = home(co).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CO_MANAGER"))
                .andExpect(jsonPath("$.data.recentActivities.length()").value(1))
                .andExpect(jsonPath("$.data.recentActivities[0].diaryId").value(approved))
                .andExpect(jsonPath("$.data.recentActivities[0].preview").doesNotExist())
                .andExpect(jsonPath("$.data.recentActivities[0].approvedCount").value(1))
                .andExpect(jsonPath("$.data.recentActivities[0].pendingCount").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("원문");
    }

    @Test
    void 받은_초대_수는_본인_PENDING만() throws Exception {
        jdbc.sql("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (:s, :u, 'PENDING', now())")
                .param("s", subjectId).param("u", stranger).update();
        home(owner).andExpect(jsonPath("$.data.pendingInvitationCount").value(0));
    }

    @Test
    void 무관한_사용자_403_토큰_없음_401() throws Exception {
        home(stranger).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/subjects/" + subjectId + "/home")).andExpect(status().isUnauthorized());
    }
}
