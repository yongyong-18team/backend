package com.batong.global.auth;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TRD 7-2: OWNER / CO_MANAGER / NONE 판정이 요청마다 DB 기준으로 동작하는지 본다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import({TestcontainersConfiguration.class, RoleInterceptorTest.ProbeController.class})
class RoleInterceptorTest {

    @RestController
    @RequestMapping("/api/v1/subjects/{subjectId}/probe")
    static class ProbeController {

        @GetMapping("/owner")
        @RequireRole(SubjectRole.OWNER)
        String ownerOnly(@PathVariable Long subjectId) {
            return "ok";
        }

        @GetMapping("/both")
        @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
        String both(@PathVariable Long subjectId) {
            return "ok";
        }
    }

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

    @BeforeEach
    void setUp() {
        ownerId = user("role_owner");
        coId = user("role_co");
        strangerId = user("role_other");
        subjectId = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
                .param("o", ownerId).query(Long.class).single();
        long invitationId = jdbc.sql("""
                INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id""")
                .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
                .param("s", subjectId).param("c", coId).param("i", invitationId).update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'role\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private String bearer(long userId) {
        return "Bearer " + jwt.issue(userId);
    }

    private String path(String leaf) {
        return "/api/v1/subjects/" + subjectId + "/probe/" + leaf;
    }

    @Test
    void OWNER는_OWNER_전용과_공용_모두_통과한다() throws Exception {
        mvc.perform(get(path("owner")).header("Authorization", bearer(ownerId))).andExpect(status().isOk());
        mvc.perform(get(path("both")).header("Authorization", bearer(ownerId))).andExpect(status().isOk());
    }

    @Test
    void CO_MANAGER는_공용만_통과하고_OWNER_전용은_403이다() throws Exception {
        mvc.perform(get(path("both")).header("Authorization", bearer(coId))).andExpect(status().isOk());
        mvc.perform(get(path("owner")).header("Authorization", bearer(coId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"));
    }

    @Test
    void 무관한_사용자는_403이다() throws Exception {
        mvc.perform(get(path("both")).header("Authorization", bearer(strangerId))).andExpect(status().isForbidden());
    }

    @Test
    void 없는_대상자도_403이다() throws Exception {
        mvc.perform(get("/api/v1/subjects/999999/probe/both").header("Authorization", bearer(ownerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mvc.perform(get(path("both"))).andExpect(status().isUnauthorized());
    }

    @Test
    void 공동_관리자를_해제하면_같은_토큰이_즉시_403이다() throws Exception {
        mvc.perform(get(path("both")).header("Authorization", bearer(coId))).andExpect(status().isOk());
        jdbc.sql("DELETE FROM co_manager WHERE subject_id = :s AND user_id = :u")
                .param("s", subjectId).param("u", coId).update();
        mvc.perform(get(path("both")).header("Authorization", bearer(coId))).andExpect(status().isForbidden());
    }
}
