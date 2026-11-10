package com.batong.member;

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

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MEMBER-01~03, INVITE-01·03~06 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class MemberApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long owner;
    long dad;
    long mom;
    long stranger;
    long subjectId;

    @BeforeEach
    void setUp() {
        owner = user("memowner", "김영희", "PARENT");
        dad = user("memdad", "이철수", "OTHER");
        mom = user("memmom", "박순이", null);
        stranger = user("memother", "최낯선", null);
        subjectId = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, emergency_contact, created_at, updated_at)
                VALUES (:o, '가나', 2001, 'SUJI', '어머니 010-0000-0000', now(), now()) RETURNING subject_id""")
                .param("o", owner).query(Long.class).single();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'mem%'").update();
    }

    private long user(String loginId, String name, String relation) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, display_name, relation, created_at, updated_at) VALUES (:l, 'x', :n, :r, now(), now()) RETURNING user_id")
                .param("l", loginId).param("n", name).param("r", relation).query(Long.class).single();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long userId) {
        return b.header("Authorization", "Bearer " + jwt.issue(userId)).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions invite(long by, String loginId) throws Exception {
        return mvc.perform(as(post("/api/v1/subjects/" + subjectId + "/invitations"), by)
                .content("{\"inviteeLoginId\":\"" + loginId + "\"}"));
    }

    private long inviteOk(String loginId) throws Exception {
        String body = invite(owner, loginId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).at("/data/invitationId").asLong();
    }

    private ResultActions respond(long by, long invId, String action) throws Exception {
        return mvc.perform(as(post("/api/v1/users/me/invitations/" + invId + "/" + action), by));
    }

    private long accepted(long who) throws Exception {
        long id = inviteOk(jdbc.sql("SELECT login_id FROM users WHERE user_id = :u").param("u", who).query(String.class).single());
        respond(who, id, "accept").andExpect(status().isOk());
        return id;
    }

    // MEMBER-01

    @Test
    void 계정_확인은_표시명만_주고_없으면_404() throws Exception {
        mvc.perform(as(get("/api/v1/users/lookup").param("loginId", "memdad"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(dad))
                .andExpect(jsonPath("$.data.displayName").value("이철수"))
                .andExpect(jsonPath("$.data.loginId").doesNotExist())
                .andExpect(jsonPath("$.data.relation").doesNotExist());
        mvc.perform(as(get("/api/v1/users/lookup").param("loginId", "nobody99"), owner))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("INVITE_USER_NOT_FOUND"));
        mvc.perform(as(get("/api/v1/users/lookup").param("loginId", "A!"), owner)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/lookup").param("loginId", "memdad")).andExpect(status().isUnauthorized());
    }

    @Test
    void 계정_확인은_사용자당_분당_10회까지다() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(as(get("/api/v1/users/lookup").param("loginId", "memdad"), stranger)).andExpect(status().isOk());
        }
        mvc.perform(as(get("/api/v1/users/lookup").param("loginId", "memdad"), stranger))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("COMMON_TOO_MANY_REQUESTS"));
        mvc.perform(as(get("/api/v1/users/lookup").param("loginId", "memdad"), mom)).andExpect(status().isOk());
    }

    // INVITE-01·03

    @Test
    void 초대_오류_코드() throws Exception {
        invite(owner, "memowner").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVITE_SELF"));
        invite(owner, "nobody99").andExpect(status().isNotFound());
        invite(owner, "").andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/subjects/" + subjectId + "/invitations"), owner).content("{}")).andExpect(status().isNotFound());
        inviteOk("memdad");
        invite(owner, "memdad").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVITE_DUPLICATE_PENDING"));
    }

    @Test
    void 초대는_OWNER만_보낸다() throws Exception {
        accepted(dad);
        invite(dad, "memmom").andExpect(status().isForbidden());
        invite(stranger, "memmom").andExpect(status().isForbidden());
    }

    @Test
    void 이미_공동_관리자인_사람은_다시_초대할_수_없다() throws Exception {
        accepted(dad);
        invite(owner, "memdad").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVITE_ALREADY_MEMBER"));
    }

    @Test
    void 동시에_같은_사람을_초대해도_하나만_생긴다() throws Exception {
        var pool = Executors.newFixedThreadPool(10);
        var fs = new ArrayList<Future<Integer>>();
        for (int i = 0; i < 10; i++) {
            fs.add(pool.submit(() -> invite(owner, "memdad").andReturn().getResponse().getStatus()));
        }
        int created = 0;
        for (var f : fs) {
            int c = f.get();
            if (c == 201) created++;
            else assertThat(c).isEqualTo(409);
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
    }

    @Test
    void 취소는_대기_중인_내_대상자_초대만() throws Exception {
        long id = inviteOk("memdad");
        mvc.perform(as(post("/api/v1/subjects/" + subjectId + "/invitations/" + id + "/cancel"), owner)).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/subjects/" + subjectId + "/invitations/" + id + "/cancel"), owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVITE_NOT_PENDING"));
        respond(dad, id, "accept").andExpect(status().isConflict());
        // 취소 뒤에는 다시 초대할 수 있다
        inviteOk("memdad");
        // 다른 대상자의 초대는 취소할 수 없다
        long otherSubject = jdbc.sql("INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at) VALUES (:o, '다라', 2000, 'SUJI', now(), now()) RETURNING subject_id")
                .param("o", stranger).query(Long.class).single();
        long theirs = jdbc.sql("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (:s, :u, 'PENDING', now()) RETURNING invitation_id")
                .param("s", otherSubject).param("u", mom).query(Long.class).single();
        mvc.perform(as(post("/api/v1/subjects/" + subjectId + "/invitations/" + theirs + "/cancel"), owner))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("INVITE_FORBIDDEN"));
    }

    // INVITE-04~06

    @Test
    void 받은_초대는_민감정보_없이_표시명과_고정_권한만() throws Exception {
        inviteOk("memdad");
        String body = mvc.perform(as(get("/api/v1/users/me/invitations"), dad)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invitations.length()").value(1))
                .andExpect(jsonPath("$.data.invitations[0].inviterDisplayName").value("김영희"))
                .andExpect(jsonPath("$.data.invitations[0].subjectName").value("가나"))
                .andExpect(jsonPath("$.data.invitations[0].permissions.length()").value(3))
                .andExpect(jsonPath("$.data.invitations[0].restrictedItems.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("010-0000-0000").doesNotContain("SUJI").doesNotContain("2001");
        mvc.perform(as(get("/api/v1/users/me/invitations"), mom)).andExpect(jsonPath("$.data.invitations.length()").value(0));
    }

    @Test
    void 수락하면_공동_관리자가_되고_다시_수락할_수_없다() throws Exception {
        long id = inviteOk("memdad");
        respond(dad, id, "accept").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectId").value(subjectId))
                .andExpect(jsonPath("$.data.role").value("CO_MANAGER"));
        assertThat(jdbc.sql("SELECT count(*) FROM co_manager WHERE subject_id = :s AND user_id = :u")
                .param("s", subjectId).param("u", dad).query(Integer.class).single()).isEqualTo(1);
        respond(dad, id, "accept").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVITE_NOT_PENDING"));
        respond(dad, id, "reject").andExpect(status().isConflict());
        mvc.perform(as(get("/api/v1/subjects/" + subjectId + "/shares"), dad)).andExpect(status().isOk());
    }

    @Test
    void 남에게_온_초대와_없는_초대는_403() throws Exception {
        long id = inviteOk("memdad");
        respond(mom, id, "accept").andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("INVITE_FORBIDDEN"));
        respond(owner, id, "accept").andExpect(status().isForbidden());
        respond(mom, id, "reject").andExpect(status().isForbidden());
        respond(dad, 999_999, "accept").andExpect(status().isForbidden());
        assertThat(jdbc.sql("SELECT status FROM invitation WHERE invitation_id = :i").param("i", id).query(String.class).single())
                .isEqualTo("PENDING");
    }

    @Test
    void 거절하면_공동_관리자가_되지_않고_다시_초대할_수_있다() throws Exception {
        long id = inviteOk("memdad");
        respond(dad, id, "reject").andExpect(status().isOk());
        mvc.perform(as(get("/api/v1/subjects/" + subjectId + "/members"), dad)).andExpect(status().isForbidden());
        inviteOk("memdad");
    }

    @Test
    void 동시에_수락해도_한_번만_성공한다() throws Exception {
        long id = inviteOk("memdad");
        var pool = Executors.newFixedThreadPool(10);
        var fs = new ArrayList<Future<Integer>>();
        for (int i = 0; i < 10; i++) {
            fs.add(pool.submit(() -> respond(dad, id, "accept").andReturn().getResponse().getStatus()));
        }
        int ok = 0;
        for (var f : fs) {
            int c = f.get();
            if (c == 200) ok++;
            else assertThat(c).isEqualTo(409);
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }

    // MEMBER-02·03

    @Test
    void 목록은_OWNER에게_모든_loginId를_CO에게는_본인_것만() throws Exception {
        accepted(dad);
        accepted(mom);
        mvc.perform(as(get("/api/v1/subjects/" + subjectId + "/members"), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner.loginId").value("memowner"))
                .andExpect(jsonPath("$.data.owner.relation").value("PARENT"))
                .andExpect(jsonPath("$.data.coManagers.length()").value(2))
                .andExpect(jsonPath("$.data.coManagers[0].loginId").value("memdad"))
                .andExpect(jsonPath("$.data.coManagers[1].loginId").value("memmom"));
        mvc.perform(as(get("/api/v1/subjects/" + subjectId + "/members"), dad)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner.loginId").doesNotExist())
                .andExpect(jsonPath("$.data.owner.displayName").value("김영희"))
                .andExpect(jsonPath("$.data.coManagers[0].loginId").value("memdad"))
                .andExpect(jsonPath("$.data.coManagers[1].loginId").doesNotExist())
                .andExpect(jsonPath("$.data.coManagers[1].displayName").value("박순이"));
        mvc.perform(as(get("/api/v1/subjects/" + subjectId + "/members"), stranger)).andExpect(status().isForbidden());
    }

    @Test
    void 삭제하면_그_사람이_만든_활성_공유만_철회하고_계정은_남는다() throws Exception {
        long inv = accepted(dad);
        accepted(mom);
        long dadActive = share(dad, "1 hour", "6 days", false);
        long dadRevoked = share(dad, "1 hour", "6 days", true);
        long dadExpired = share(dad, "8 days", "-1 day", false);
        long momActive = share(mom, "1 hour", "6 days", false);
        long ownerActive = share(owner, "1 hour", "6 days", false);

        mvc.perform(as(delete("/api/v1/subjects/" + subjectId + "/members/" + dad), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revokedShareCount").value(1));

        assertThat(revoked(dadActive)).isTrue();
        assertThat(revoked(momActive)).isFalse();
        assertThat(revoked(ownerActive)).isFalse();
        assertThat(jdbc.sql("SELECT revoked_at IS NULL FROM share WHERE share_id = :i").param("i", dadExpired).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT status FROM invitation WHERE invitation_id = :i").param("i", inv).query(String.class).single()).isEqualTo("REVOKED");
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE user_id = :u").param("u", dad).query(Integer.class).single()).isEqualTo(1);
        // 삭제 뒤에는 접근할 수 없고, 다시 초대할 수 있다
        mvc.perform(as(get("/api/v1/subjects/" + subjectId + "/members"), dad)).andExpect(status().isForbidden());
        inviteOk("memdad");
        assertThat(dadRevoked).isPositive();
    }

    @Test
    void 삭제_오류_코드와_권한() throws Exception {
        accepted(dad);
        mvc.perform(as(delete("/api/v1/subjects/" + subjectId + "/members/" + owner), owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MEMBER_CANNOT_REMOVE_OWNER"));
        mvc.perform(as(delete("/api/v1/subjects/" + subjectId + "/members/" + mom), owner))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
        mvc.perform(as(delete("/api/v1/subjects/" + subjectId + "/members/" + dad), dad)).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/v1/subjects/" + subjectId + "/members/" + dad), stranger)).andExpect(status().isForbidden());
        assertThat(jdbc.sql("SELECT count(*) FROM co_manager WHERE subject_id = :s").param("s", subjectId).query(Integer.class).single()).isEqualTo(1);
    }

    private boolean revoked(long shareId) {
        return jdbc.sql("SELECT revoked_at IS NOT NULL FROM share WHERE share_id = :i").param("i", shareId).query(Boolean.class).single();
    }

    private long share(long createdBy, String ago, String exp, boolean revoked) {
        String hash = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
        return jdbc.sql("""
                INSERT INTO share (subject_id, created_by, document_type, snapshot_json, token_hash, approved_at, expires_at, revoked_at, created_at)
                VALUES (:s, :u, 'GUIDE', '{}'::jsonb, :h, now() - cast(:ago as interval), now() + cast(:exp as interval),
                        CASE WHEN :rev THEN now() ELSE NULL END, now()) RETURNING share_id""")
                .param("s", subjectId).param("u", createdBy).param("h", hash.substring(0, 64))
                .param("ago", ago).param("exp", exp).param("rev", revoked).query(Long.class).single();
    }

    // INVITE-02

    @Test
    void 보낸_초대_목록은_OWNER만_보고_status로_거른다() throws Exception {
        long cancelled = inviteOk("memdad");
        mvc.perform(as(post("/api/v1/subjects/" + subjectId + "/invitations/" + cancelled + "/cancel"), owner)).andExpect(status().isOk());
        long pending = inviteOk("memmom");
        String url = "/api/v1/subjects/" + subjectId + "/invitations";
        mvc.perform(as(get(url), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invitations.length()").value(2))
                .andExpect(jsonPath("$.data.invitations[0].invitationId").value(pending))
                .andExpect(jsonPath("$.data.invitations[0].inviteeLoginId").value("memmom"))
                .andExpect(jsonPath("$.data.invitations[0].respondedAt").doesNotExist())
                .andExpect(jsonPath("$.data.invitations[1].status").value("CANCELED"));
        mvc.perform(as(get(url).param("status", "PENDING"), owner))
                .andExpect(jsonPath("$.data.invitations.length()").value(1))
                .andExpect(jsonPath("$.data.invitations[0].status").value("PENDING"));
        mvc.perform(as(get(url).param("status", "NOPE"), owner)).andExpect(status().isBadRequest());
        mvc.perform(as(get(url), stranger)).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
}
