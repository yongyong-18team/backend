package com.batong.guide;

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

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ITEM-01: 승인·수정·제외, 중복 방지, 권한, 동시성 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ItemReviewApiTest {

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
    long diaryId;

    @BeforeEach
    void setUp() {
        owner = user("itmowner");
        co = user("itmco");
        stranger = user("itmother");
        subjectId = subject(owner);
        long inv = jdbc.sql("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (:s, :u, 'ACCEPTED', now()) RETURNING invitation_id")
                .param("s", subjectId).param("u", co).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :u, :i, now())")
                .param("s", subjectId).param("u", co).param("i", inv).update();
        diaryId = diary(subjectId);
        item(diaryId, 1, "TRIGGER", "공사 소음", "PENDING");
        item(diaryId, 2, "RESPONSE", "음악을 틀어줌", "PENDING");
        item(diaryId, 3, "PREFERENCE", "담요를 좋아함", "PENDING");
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'itm%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private long subject(long ownerId) {
        return jdbc.sql("INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at) VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id")
                .param("o", ownerId).query(Long.class).single();
    }

    private long diary(long sid) {
        return jdbc.sql("INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at) VALUES (:s, current_date, '가상 일기', 'TEXT', 'DONE', now(), now()) RETURNING diary_id")
                .param("s", sid).query(Long.class).single();
    }

    private void item(long d, int no, String category, String content, String status) {
        jdbc.sql("INSERT INTO extracted_item (diary_id, item_no, category, content, evidence_quote, status) VALUES (:d, :n, :c, :t, '근거', :st)")
                .param("d", d).param("n", no).param("c", category).param("t", content).param("st", status).update();
    }

    private ResultActions review(long userId, long sid, long did, String body) throws Exception {
        return mvc.perform(post("/api/v1/subjects/" + sid + "/diaries/" + did + "/items/review")
                .header("Authorization", "Bearer " + jwt.issue(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String itemStatus(int no) {
        return jdbc.sql("SELECT status FROM extracted_item WHERE diary_id = :d AND item_no = :n").param("d", diaryId).param("n", no)
                .query(String.class).single();
    }

    private int entries() {
        return jdbc.sql("SELECT count(*) FROM guide_entry WHERE subject_id = :s").param("s", subjectId).query(Integer.class).single();
    }

    @Test
    void 승인은_카테고리에_맞는_섹션에_설명서_항목을_만든다() throws Exception {
        review(owner, subjectId, diaryId, """
                {"decisions":[{"itemNo":1,"status":"APPROVED"},{"itemNo":2,"status":"APPROVED","content":" 20~30분간 음악을 틀어줌 "},{"itemNo":3,"status":"EXCLUDED"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvedCount").value(2))
                .andExpect(jsonPath("$.data.excludedCount").value(1))
                .andExpect(jsonPath("$.data.duplicateSkippedCount").value(0))
                .andExpect(jsonPath("$.data.createdGuideEntryIds.length()").value(2));
        assertThat(itemStatus(1)).isEqualTo("APPROVED");
        assertThat(itemStatus(2)).isEqualTo("APPROVED");
        assertThat(itemStatus(3)).isEqualTo("EXCLUDED");
        var row = jdbc.sql("SELECT content, edited FROM extracted_item WHERE diary_id = :d AND item_no = 2").param("d", diaryId).query().singleRow();
        assertThat(row.get("content")).isEqualTo("20~30분간 음악을 틀어줌");
        assertThat(row.get("edited")).isEqualTo(true);
        assertThat(jdbc.sql("SELECT edited FROM extracted_item WHERE diary_id = :d AND item_no = 1").param("d", diaryId).query(Boolean.class).single()).isFalse();
        var e = jdbc.sql("SELECT section, content, source_type, src_diary_id, src_item_no FROM guide_entry WHERE src_item_no = 2 AND subject_id = :s")
                .param("s", subjectId).query().singleRow();
        assertThat(e).containsEntry("section", "BEHAVIOR_RESPONSE").containsEntry("content", "20~30분간 음악을 틀어줌")
                .containsEntry("source_type", "DIARY").containsEntry("src_diary_id", diaryId);
        assertThat(entries()).isEqualTo(2);
    }

    @Test
    void 같은_섹션에_공백만_다른_내용이_있으면_새로_만들지_않고_센다() throws Exception {
        jdbc.sql("INSERT INTO guide_entry (subject_id, section, content, source_type, created_at, updated_at) VALUES (:s, 'BEHAVIOR_RESPONSE', '공사  소음', 'MANUAL', now(), now())")
                .param("s", subjectId).update();
        review(owner, subjectId, diaryId, """
                {"decisions":[{"itemNo":1,"status":"APPROVED"},{"itemNo":2,"status":"APPROVED"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvedCount").value(2))
                .andExpect(jsonPath("$.data.duplicateSkippedCount").value(1))
                .andExpect(jsonPath("$.data.createdGuideEntryIds.length()").value(1));
        assertThat(entries()).isEqualTo(2);
    }

    @Test
    void 제외했던_항목은_다시_승인할_수_있지만_승인한_항목은_바꿀_수_없다() throws Exception {
        review(owner, subjectId, diaryId, """
                {"decisions":[{"itemNo":1,"status":"EXCLUDED"},{"itemNo":2,"status":"APPROVED"}]}""").andExpect(status().isOk());
        review(owner, subjectId, diaryId, """
                {"decisions":[{"itemNo":1,"status":"APPROVED"}]}""").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdGuideEntryIds.length()").value(1));
        review(owner, subjectId, diaryId, """
                {"decisions":[{"itemNo":2,"status":"EXCLUDED"}]}""").andExpect(status().isBadRequest());
        review(owner, subjectId, diaryId, """
                {"decisions":[{"itemNo":3,"status":"APPROVED"},{"itemNo":2,"status":"APPROVED"}]}""").andExpect(status().isBadRequest());
        assertThat(itemStatus(3)).isEqualTo("PENDING");
        assertThat(entries()).isEqualTo(2);
    }

    @Test
    void 잘못된_요청은_아무것도_바꾸지_않는다() throws Exception {
        String[] bad = {
                "{}", "{\"decisions\":null}", "{\"decisions\":[]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"EXCLUDED\"}]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"PENDING\"}]}",
                "{\"decisions\":[{\"itemNo\":1}]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\"},{\"itemNo\":1,\"status\":\"APPROVED\"}]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\",\"content\":\"\"}]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\",\"content\":\"   \"}]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\",\"content\":\"" + "가".repeat(501) + "\"}]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\",\"content\":\"가\\u0000나\"}]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\",\"content\":\"가\\ud800나\"}]}",
                "{\"decisions\":[{\"itemNo\":1,\"status\":\"EXCLUDED\",\"content\":\"수정\"},{\"itemNo\":2,\"status\":\"APPROVED\"}]}",
                "{\"decisions\":[null]}", "not json"
        };
        for (String body : bad) {
            assertThat(review(owner, subjectId, diaryId, body).andReturn().getResponse().getStatus()).as(body).isEqualTo(400);
        }
        assertThat(itemStatus(1)).isEqualTo("PENDING");
        assertThat(entries()).isZero();
    }

    @Test
    void 없는_항목이면_404이고_앞선_결정도_취소된다() throws Exception {
        review(owner, subjectId, diaryId, """
                {"decisions":[{"itemNo":1,"status":"APPROVED"},{"itemNo":99,"status":"APPROVED"}]}""")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("GUIDE_ITEM_NOT_FOUND"));
        assertThat(itemStatus(1)).isEqualTo("PENDING");
        assertThat(entries()).isZero();
    }

    @Test
    void 다른_대상자의_일기나_없는_일기는_404() throws Exception {
        long otherSubject = subject(stranger);
        long otherDiary = diary(otherSubject);
        item(otherDiary, 1, "TRIGGER", "남의 항목", "PENDING");
        review(owner, subjectId, otherDiary, """
                {"decisions":[{"itemNo":1,"status":"APPROVED"}]}""").andExpect(status().isNotFound());
        review(owner, subjectId, 999_999, """
                {"decisions":[{"itemNo":1,"status":"APPROVED"}]}""").andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT status FROM extracted_item WHERE diary_id = :d").param("d", otherDiary).query(String.class).single()).isEqualTo("PENDING");
    }

    @Test
    void OWNER만_검토할_수_있다() throws Exception {
        String body = "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\"}]}";
        review(co, subjectId, diaryId, body).andExpect(status().isForbidden());
        review(stranger, subjectId, diaryId, body).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/subjects/" + subjectId + "/diaries/" + diaryId + "/items/review")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertThat(entries()).isZero();
    }

    @Test
    void 동시에_같은_항목을_승인해도_설명서_항목은_하나만_생긴다() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var fs = new ArrayList<Future<Integer>>();
        for (int i = 0; i < 8; i++) {
            fs.add(pool.submit(() -> review(owner, subjectId, diaryId,
                    "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\"}]}").andReturn().getResponse().getStatus()));
        }
        int ok = 0;
        for (var f : fs) {
            int c = f.get();
            if (c == 200) ok++;
            else assertThat(c).isEqualTo(400);
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(entries()).isEqualTo(1);
    }

    @Test
    void 서로_다른_일기의_같은_내용을_동시에_승인해도_중복이_생기지_않는다() throws Exception {
        long second = diary(subjectId);
        item(second, 1, "TRIGGER", "공사  소음", "PENDING");
        var pool = Executors.newFixedThreadPool(2);
        var a = pool.submit(() -> review(owner, subjectId, diaryId, "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\"}]}").andReturn().getResponse().getStatus());
        var b = pool.submit(() -> review(owner, subjectId, second, "{\"decisions\":[{\"itemNo\":1,\"status\":\"APPROVED\"}]}").andReturn().getResponse().getStatus());
        assertThat(a.get()).isEqualTo(200);
        assertThat(b.get()).isEqualTo(200);
        pool.shutdown();
        assertThat(entries()).isEqualTo(1);
    }
}
