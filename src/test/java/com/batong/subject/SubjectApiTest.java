package com.batong.subject;

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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SUBJ-02·03: DB 기준 조회·부분 수정, 역할(TRD 7-2), 입력 검증(ERD CHECK와 같은 규칙) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class SubjectApiTest {

    private static final int THIS_YEAR = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long subjectId;
    String owner;
    String coManager;
    String stranger;

    @BeforeEach
    void seed() {
        long ownerId = user("subj_owner");
        long coId = user("subj_co");
        long strangerId = user("subj_other");
        subjectId = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, dong, created_at, updated_at)
                VALUES (:o, '민지', 2001, 'SUJI', '풍덕천동', now() - interval '1 day', now() - interval '1 day')
                RETURNING subject_id""")
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
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'subj\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private String path() {
        return "/api/v1/subjects/" + subjectId;
    }

    private ResultActions patchAs(String token, String json) throws Exception {
        return mvc.perform(patch(path()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Map<String, Object> row() {
        return jdbc.sql("SELECT name, birth_year, district, dong, emergency_contact, updated_at FROM care_subject WHERE subject_id = :s")
                .param("s", subjectId).query().singleRow();
    }

    @Test
    void SUBJ_02_OWNER는_DB_값과_계산한_나이를_받는다() throws Exception {
        mvc.perform(get(path()).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectId").value(subjectId))
                .andExpect(jsonPath("$.data.name").value("민지"))
                .andExpect(jsonPath("$.data.birthYear").value(2001))
                .andExpect(jsonPath("$.data.age").value(THIS_YEAR - 2001))
                .andExpect(jsonPath("$.data.district").value("SUJI"))
                .andExpect(jsonPath("$.data.dong").value("풍덕천동"))
                .andExpect(jsonPath("$.data.emergencyContact").doesNotExist());
    }

    @Test
    void SUBJ_02_CO는_조회하고_무관한_사용자는_403() throws Exception {
        mvc.perform(get(path()).header("Authorization", coManager)).andExpect(status().isOk());
        mvc.perform(get(path()).header("Authorization", stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"));
    }

    @Test
    void SUBJ_03_보낸_값만_바꾸고_updatedAt을_갱신한다() throws Exception {
        Object before = row().get("updated_at");

        patchAs(owner, "{\"emergencyContact\":\"어머니 010-0000-0000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectId").value(subjectId))
                .andExpect(jsonPath("$.data.updatedAt").isString());

        Map<String, Object> after = row();
        assertThat(after.get("emergency_contact")).isEqualTo("어머니 010-0000-0000");
        assertThat(after.get("name")).isEqualTo("민지");
        assertThat(after.get("dong")).isEqualTo("풍덕천동");
        assertThat(after.get("updated_at")).isNotEqualTo(before);
    }

    @Test
    void SUBJ_03_빈_문자열은_지우고_null은_그대로_둔다() throws Exception {
        patchAs(owner, "{\"dong\":\"\",\"name\":null}").andExpect(status().isOk());

        Map<String, Object> after = row();
        assertThat(after.get("dong")).isNull();
        assertThat(after.get("name")).isEqualTo("민지");
    }

    @Test
    void SUBJ_03_이름이_2자_미만이거나_20자_초과면_SUBJECT_INVALID_NAME() throws Exception {
        for (String name : new String[]{"민", "가".repeat(21), "  "}) {
            patchAs(owner, "{\"name\":\"" + name + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SUBJECT_INVALID_NAME"));
        }
        assertThat(row().get("name")).isEqualTo("민지");
    }

    @Test
    void SUBJ_03_출생_연도가_1930_미만이거나_올해_이후면_SUBJECT_INVALID_BIRTH_YEAR() throws Exception {
        for (int year : new int[]{1929, THIS_YEAR + 1}) {
            patchAs(owner, "{\"birthYear\":" + year + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SUBJECT_INVALID_BIRTH_YEAR"));
        }
        patchAs(owner, "{\"birthYear\":1930}").andExpect(status().isOk());
        patchAs(owner, "{\"birthYear\":" + THIS_YEAR + "}").andExpect(status().isOk());
    }

    @Test
    void SUBJ_03_길이_초과와_없는_지역은_COMMON_VALIDATION() throws Exception {
        patchAs(owner, "{\"dong\":\"" + "가".repeat(21) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        patchAs(owner, "{\"emergencyContact\":\"" + "0".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        patchAs(owner, "{\"district\":\"SEOUL\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        assertThat(row().get("dong")).isEqualTo("풍덕천동");
    }

    @Test
    void SUBJ_03_CO는_403이고_값이_바뀌지_않는다() throws Exception {
        patchAs(coManager, "{\"name\":\"다른이름\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"));
        assertThat(row().get("name")).isEqualTo("민지");
    }
}
