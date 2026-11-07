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

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GUIDE-01~04: 7개 섹션, 권한(OWNER 수정 / CO 열람만), 검증, 중복 방지, 다른 대상자 항목 접근. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class GuideApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long ownerId;
    long coId;
    long subjectId;
    long otherSubjectId;

    @BeforeEach
    void setUp() {
        ownerId = user("guide_owner");
        coId = user("guide_co");
        long otherOwner = user("guide_other");
        subjectId = subject(ownerId);
        otherSubjectId = subject(otherOwner);
        long invitationId = jdbc.sql("""
                INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id""")
                .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
                .param("s", subjectId).param("c", coId).param("i", invitationId).update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'guide\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private long subject(long owner) {
        return jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, dong, emergency_contact, created_at, updated_at)
                VALUES (:o, '가나', 2001, 'SUJI', '풍덕천동', '어머니 010-0000-0000', now(), now()) RETURNING subject_id""")
                .param("o", owner).query(Long.class).single();
    }

    private String bearer(long userId) {
        return "Bearer " + jwt.issue(userId);
    }

    private ResultActions add(long userId, long subject, String section, String content) throws Exception {
        return mvc.perform(post("/api/v1/subjects/" + subject + "/guide/entries").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"section\":\"" + section + "\",\"content\":\"" + content + "\"}"));
    }

    private long addOk(String section, String content) throws Exception {
        String body = add(ownerId, subjectId, section, content).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).at("/data/entryId").asLong();
    }

    @Test
    void 항목이_없어도_7개_섹션과_기본_정보를_주고_OWNER는_editable이다() throws Exception {
        mvc.perform(get("/api/v1/subjects/" + subjectId + "/guide").header("Authorization", bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections", hasSize(7)))
                .andExpect(jsonPath("$.data.sections[0].section").value("PROHIBITED"))
                .andExpect(jsonPath("$.data.sections[6].section").value("PREFERENCE"))
                .andExpect(jsonPath("$.data.sections[0].entries", hasSize(0)))
                .andExpect(jsonPath("$.data.basicInfo.name").value("가나"))
                .andExpect(jsonPath("$.data.basicInfo.age").value(Year.now().getValue() - 2001))
                .andExpect(jsonPath("$.data.basicInfo.district").value("SUJI"))
                .andExpect(jsonPath("$.data.editable").value(true));
    }

    @Test
    void 추가한_항목은_해당_섹션에_MANUAL로_보이고_공동_관리자도_열람하지만_editable은_false다() throws Exception {
        long id = addOk("PROHIBITED", "갑자기 뒤에서 어깨를 잡지 않기");
        mvc.perform(get("/api/v1/subjects/" + subjectId + "/guide").header("Authorization", bearer(coId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[0].entries[0].entryId").value(id))
                .andExpect(jsonPath("$.data.sections[0].entries[0].sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.editable").value(false));
    }

    @Test
    void 공동_관리자와_무관한_사용자는_수정할_수_없다() throws Exception {
        long id = addOk("PROHIBITED", "금지 항목");
        add(coId, subjectId, "PROHIBITED", "다른 항목").andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/subjects/" + subjectId + "/guide/entries/" + id).header("Authorization", bearer(coId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/subjects/" + subjectId + "/guide/entries/" + id).header("Authorization", bearer(coId)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/subjects/" + otherSubjectId + "/guide").header("Authorization", bearer(ownerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 내용이_비었거나_500자를_넘거나_섹션이_틀리면_400이다() throws Exception {
        add(ownerId, subjectId, "PROHIBITED", "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        add(ownerId, subjectId, "PROHIBITED", "가".repeat(501)).andExpect(status().isBadRequest());
        add(ownerId, subjectId, "NOPE", "내용").andExpect(status().isBadRequest());
        add(ownerId, subjectId, "PROHIBITED", "가".repeat(500)).andExpect(status().isCreated());
    }

    @Test
    void 같은_섹션에_공백만_다른_내용은_새로_만들지_않고_기존_항목을_돌려준다() throws Exception {
        long first = addOk("PREFERENCE", "좋아하는 간식은 귤");
        long second = addOk("PREFERENCE", "좋아하는  간식은 귤 ");
        assertThat(second).isEqualTo(first);
        long otherSection = addOk("DAILY_LIFE", "좋아하는 간식은 귤");
        assertThat(otherSection).isNotEqualTo(first);
        assertThat(jdbc.sql("SELECT count(*) FROM guide_entry WHERE subject_id = :s").param("s", subjectId)
                .query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void 수정하면_내용과_updatedAt이_바뀌고_삭제하면_사라진다() throws Exception {
        long id = addOk("COMMUNICATION", "천천히 말하기");
        mvc.perform(patch("/api/v1/subjects/" + subjectId + "/guide/entries/" + id).header("Authorization", bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"천천히 또렷하게 말하기\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(id))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        assertThat(jdbc.sql("SELECT content FROM guide_entry WHERE entry_id = :i").param("i", id)
                .query(String.class).single()).isEqualTo("천천히 또렷하게 말하기");
        mvc.perform(delete("/api/v1/subjects/" + subjectId + "/guide/entries/" + id).header("Authorization", bearer(ownerId)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/subjects/" + subjectId + "/guide/entries/" + id).header("Authorization", bearer(ownerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GUIDE_ITEM_NOT_FOUND"));
    }

    @Test
    void 동시에_같은_내용을_추가해도_행은_하나다() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(10);
        java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            results.add(pool.submit(() -> {
                add(ownerId, subjectId, "DAILY_LIFE", "연타해도 한 번만").andReturn();
                return 0;
            }));
        }
        for (var f : results) {
            f.get();
        }
        pool.shutdown();
        assertThat(jdbc.sql("SELECT count(*) FROM guide_entry WHERE subject_id = :s AND content = '연타해도 한 번만'")
                .param("s", subjectId).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void NUL_문자가_들어간_내용은_500이_아니라_400이다() throws Exception {
        add(ownerId, subjectId, "PROHIBITED", "a\\u0000b").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
    }

    @Test
    void 다른_대상자의_항목은_내_대상자_경로로도_수정_삭제할_수_없다() throws Exception {
        long otherEntry = jdbc.sql("""
                INSERT INTO guide_entry (subject_id, section, content, source_type, created_at, updated_at)
                VALUES (:s, 'PROHIBITED', '남의 항목', 'MANUAL', now(), now()) RETURNING entry_id""")
                .param("s", otherSubjectId).query(Long.class).single();
        mvc.perform(patch("/api/v1/subjects/" + subjectId + "/guide/entries/" + otherEntry).header("Authorization", bearer(ownerId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/subjects/" + subjectId + "/guide/entries/" + otherEntry).header("Authorization", bearer(ownerId)))
                .andExpect(status().isNotFound());
    }
}
