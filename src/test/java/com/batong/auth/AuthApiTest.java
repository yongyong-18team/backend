package com.batong.auth;

import com.batong.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AUTH-01~03: 형식 검증, 중복, BCrypt 저장, 로그인 응답, 5회 실패 IP 제한. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class AuthApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'auth%'").update();
    }

    private ResultActions signup(String loginId, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String loginId, String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void 가입하면_201과_userId를_주고_비밀번호는_BCrypt로_저장된다() throws Exception {
        signup("authuser1", "abcd1234").andExpect(status().isCreated()).andExpect(jsonPath("$.data.userId").isNumber());
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE login_id = 'authuser1'").query(String.class).single();
        assertThat(hash).startsWith("$2").doesNotContain("abcd1234");
    }

    @Test
    void 아이디_형식이_틀리면_400이다() throws Exception {
        signup("ab", "abcd1234").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_LOGIN_ID_FORMAT"));
        signup("AuthUser1", "abcd1234").andExpect(status().isBadRequest());
    }

    @Test
    void 비밀번호_형식이_틀리면_400이다() throws Exception {
        signup("authuser2", "abcdefgh").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_PASSWORD_FORMAT"));
        signup("authuser2", "ab12").andExpect(status().isBadRequest());
        signup("authuser2", "abcd12345678901234567").andExpect(status().isBadRequest());
    }

    @Test
    void 중복_아이디는_409이고_중복확인_API도_같다() throws Exception {
        signup("authuser3", "abcd1234").andExpect(status().isCreated());
        signup("authuser3", "abcd1234").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_DUPLICATE_LOGIN_ID"));
        mvc.perform(get("/api/v1/auth/login-id/check").param("loginId", "authuser3")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/auth/login-id/check").param("loginId", "authfree3")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/login-id/check").param("loginId", "A!")).andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하면_토큰과_사용자_정보를_주고_대상자_목록과_받은_초대_수를_채운다() throws Exception {
        signup("authowner", "abcd1234").andExpect(status().isCreated());
        signup("authinvit", "abcd1234").andExpect(status().isCreated());
        long ownerId = jdbc.sql("SELECT user_id FROM users WHERE login_id = 'authowner'").query(Long.class).single();
        long inviteeId = jdbc.sql("SELECT user_id FROM users WHERE login_id = 'authinvit'").query(Long.class).single();
        long subjectId = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
                .param("o", ownerId).query(Long.class).single();
        jdbc.sql("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (:s, :i, 'PENDING', now())")
                .param("s", subjectId).param("i", inviteeId).update();

        login("authowner", "abcd1234", "10.0.0.1").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(604800))
                .andExpect(jsonPath("$.data.user.loginId").value("authowner"))
                .andExpect(jsonPath("$.data.subjects[0].subjectId").value(subjectId))
                .andExpect(jsonPath("$.data.subjects[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data.pendingInvitationCount").value(0));
        login("authinvit", "abcd1234", "10.0.0.2").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjects").isEmpty())
                .andExpect(jsonPath("$.data.pendingInvitationCount").value(1));
    }

    @Test
    void 틀린_비밀번호와_없는_아이디는_같은_401이다() throws Exception {
        signup("authuser4", "abcd1234").andExpect(status().isCreated());
        login("authuser4", "wrongpass1", "10.0.1.1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        login("authnone4", "abcd1234", "10.0.1.1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void 같은_IP에서_5회_실패하면_올바른_비밀번호도_429이고_다른_IP는_영향이_없다() throws Exception {
        signup("authuser5", "abcd1234").andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            login("authuser5", "wrongpass1", "10.0.2.1").andExpect(status().isUnauthorized());
        }
        login("authuser5", "abcd1234", "10.0.2.1").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOO_MANY_ATTEMPTS"));
        login("authuser5", "abcd1234", "10.0.2.2").andExpect(status().isOk());
    }

    @Test
    void 로그인에_성공하면_실패_횟수가_초기화된다() throws Exception {
        signup("authuser6", "abcd1234").andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            login("authuser6", "wrongpass1", "10.0.3.1").andExpect(status().isUnauthorized());
        }
        login("authuser6", "abcd1234", "10.0.3.1").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("authuser6", "wrongpass1", "10.0.3.1").andExpect(status().isUnauthorized());
        }
        login("authuser6", "abcd1234", "10.0.3.1").andExpect(status().isOk());
    }
}
