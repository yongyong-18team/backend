package com.batong.share;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/** SHARE-04 내역, SHARE-05 접근 중지, PUB-01 수신자 열람 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ShareLifecycleTest {

    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long ownerId;
    long coId;
    long otherId;
    long subjectId;

    @BeforeEach
    void setUp() {
        ownerId = user("life_owner", "김영희");
        coId = user("life_co", null);
        otherId = user("life_other", null);
        subjectId = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
                .param("o", ownerId).query(Long.class).single();
        long inv = jdbc.sql("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id")
                .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
                .param("s", subjectId).param("c", coId).param("i", inv).update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'life\\_%'").update();
    }

    private long user(String loginId, String displayName) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, display_name, created_at, updated_at) VALUES (:l, 'x', :d, now(), now()) RETURNING user_id")
                .param("l", loginId).param("d", displayName).query(Long.class).single();
    }

    /** token은 43자 base64url. 해시는 SHA-256 hex. */
    private long share(Long createdBy, String token, String approvedAgo, String expiresIn, boolean revoked) throws Exception {
        String hash = token == null ? null : java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes()));
        return jdbc.sql("""
                INSERT INTO share (subject_id, created_by, document_type, recipient_label, snapshot_json, token_hash,
                                   approved_at, expires_at, revoked_at, created_at)
                VALUES (:s, :u, 'GUIDE', '선생님', '{"basicInfo":{"name":"가나","age":25,"emergencyContact":null},"summaryLines":[],"sections":[{"section":"COMMUNICATION","entries":["손짓"]}]}'::jsonb,
                        :h, now() - cast(:ago as interval), now() + cast(:exp as interval),
                        CASE WHEN :rev THEN now() ELSE NULL END, now())
                RETURNING share_id""")
                .param("s", subjectId).param("u", createdBy).param("h", hash)
                .param("ago", approvedAgo).param("exp", expiresIn).param("rev", revoked)
                .query(Long.class).single();
    }

    private static String token(char c) {
        return String.valueOf(c).repeat(43);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, long userId) {
        return b.header("Authorization", "Bearer " + jwt.issue(userId));
    }

    private ResultActions revoke(long userId, long shareId) throws Exception {
        return mvc.perform(auth(post("/api/v1/subjects/" + subjectId + "/shares/" + shareId + "/revoke"), userId));
    }

    private ResultActions pub(String token) throws Exception {
        String ip = "10.9." + (IP.incrementAndGet() / 250) + "." + (IP.get() % 250);
        return mvc.perform(get("/api/v1/public/shares/" + token).with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    // SHARE-04

    @Test
    void 내역은_승인된_것만_최신순이고_상태를_계산하며_초안과_링크는_없다() throws Exception {
        jdbc.sql("INSERT INTO share (subject_id, created_by, document_type, snapshot_json, created_at) VALUES (:s, :u, 'GUIDE', '{}'::jsonb, now())")
                .param("s", subjectId).param("u", ownerId).update();
        long active = share(ownerId, token('a'), "1 hour", "6 days", false);
        long expired = share(ownerId, token('b'), "8 days", "-1 day", false);
        long revoked = share(coId, token('c'), "2 hours", "6 days", true);
        mvc.perform(auth(get("/api/v1/subjects/" + subjectId + "/shares"), ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shares.length()").value(3))
                .andExpect(jsonPath("$.data.shares[0].shareId").value(active))
                .andExpect(jsonPath("$.data.shares[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.shares[0].createdByName").value("김영희"))
                .andExpect(jsonPath("$.data.shares[0].revocable").value(true))
                .andExpect(jsonPath("$.data.shares[1].shareId").value(revoked))
                .andExpect(jsonPath("$.data.shares[1].status").value("REVOKED"))
                .andExpect(jsonPath("$.data.shares[1].revocable").value(false))
                .andExpect(jsonPath("$.data.shares[2].shareId").value(expired))
                .andExpect(jsonPath("$.data.shares[2].status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.shares[0].shareUrl").doesNotExist())
                .andExpect(jsonPath("$.data.shares[0].tokenHash").doesNotExist());
    }

    @Test
    void 내역의_revocable은_CO_본인_생성분에만_true다() throws Exception {
        share(ownerId, token('a'), "1 hour", "6 days", false);
        long mine = share(coId, token('b'), "2 hours", "6 days", false);
        mvc.perform(auth(get("/api/v1/subjects/" + subjectId + "/shares"), coId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shares[0].revocable").value(false))
                .andExpect(jsonPath("$.data.shares[1].shareId").value(mine))
                .andExpect(jsonPath("$.data.shares[1].revocable").value(true));
    }

    @Test
    void 내역은_무관한_사용자_403_토큰_없음_401() throws Exception {
        mvc.perform(auth(get("/api/v1/subjects/" + subjectId + "/shares"), otherId)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/subjects/" + subjectId + "/shares")).andExpect(status().isUnauthorized());
    }

    // SHARE-05

    @Test
    void OWNER는_누가_만들었든_중지할_수_있고_이후_열람은_410이다() throws Exception {
        long id = share(coId, token('a'), "1 hour", "6 days", false);
        pub(token('a')).andExpect(status().isOk());
        revoke(ownerId, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareId").value(id))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        pub(token('a')).andExpect(status().isGone()).andExpect(jsonPath("$.error.code").value("SHARE_REVOKED"));
    }

    @Test
    void CO는_본인_생성분만_중지할_수_있다() throws Exception {
        long others = share(ownerId, token('a'), "1 hour", "6 days", false);
        long mine = share(coId, token('b'), "1 hour", "6 days", false);
        revoke(coId, others).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SHARE_FORBIDDEN"));
        revoke(coId, mine).andExpect(status().isOk());
    }

    @Test
    void 이미_중지됐거나_만료된_링크는_409다() throws Exception {
        long revoked = share(ownerId, token('a'), "1 hour", "6 days", true);
        long expired = share(ownerId, token('b'), "8 days", "-1 day", false);
        revoke(ownerId, revoked).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SHARE_ALREADY_INACTIVE"));
        revoke(ownerId, expired).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SHARE_ALREADY_INACTIVE"));
    }

    @Test
    void 없는_공유_초안_무관한_사용자는_거절한다() throws Exception {
        long draft = jdbc.sql("INSERT INTO share (subject_id, created_by, document_type, snapshot_json, created_at) VALUES (:s, :u, 'GUIDE', '{}'::jsonb, now()) RETURNING share_id")
                .param("s", subjectId).param("u", ownerId).query(Long.class).single();
        long active = share(ownerId, token('a'), "1 hour", "6 days", false);
        revoke(ownerId, 999_999).andExpect(status().isNotFound());
        revoke(ownerId, draft).andExpect(status().isNotFound());
        revoke(otherId, active).andExpect(status().isForbidden());
        assertThat(jdbc.sql("SELECT revoked_at IS NULL FROM share WHERE share_id = :i").param("i", active)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void 동시에_중지해도_한_번만_성공한다() throws Exception {
        long id = share(ownerId, token('a'), "1 hour", "6 days", false);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(10);
        var fs = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
        for (int i = 0; i < 10; i++) {
            fs.add(pool.submit(() -> revoke(ownerId, id).andReturn().getResponse().getStatus()));
        }
        int ok = 0;
        int conflict = 0;
        for (var f : fs) {
            int c = f.get();
            if (c == 200) ok++;
            else if (c == 409) conflict++;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(9);
    }

    // PUB-01

    @Test
    void 열람은_고정본만_주고_보안_헤더가_붙는다() throws Exception {
        share(ownerId, token('a'), "1 hour", "6 days", false);
        pub(token('a')).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(jsonPath("$.data.documentType").value("GUIDE"))
                .andExpect(jsonPath("$.data.sharedAt").exists())
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.content.basicInfo.name").value("가나"))
                .andExpect(jsonPath("$.data.content.basicInfo.age").value(25))
                .andExpect(jsonPath("$.data.content.sections[0].section").value("COMMUNICATION"))
                .andExpect(jsonPath("$.data.content.sections[0].entries[0]").value("손짓"))
                .andExpect(jsonPath("$.data.shareId").doesNotExist())
                .andExpect(jsonPath("$.data.subjectId").doesNotExist());
    }

    @Test
    void 없는_토큰과_형식이_틀린_토큰은_404_만료는_410() throws Exception {
        share(ownerId, token('e'), "8 days", "-1 day", false);
        pub(token('z')).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("SHARE_NOT_FOUND"));
        pub("short").andExpect(status().isNotFound());
        // NUL이 든 경로는 Spring Security 방화벽이 400으로 막는다. 500이 아니면 된다
        pub("%00" + "a".repeat(40)).andExpect(status().is4xxClientError());
        pub(token('e')).andExpect(status().isGone()).andExpect(jsonPath("$.error.code").value("SHARE_EXPIRED"));
    }

    @Test
    void 초안의_토큰_해시는_NULL이라_어떤_토큰으로도_열리지_않는다() throws Exception {
        jdbc.sql("INSERT INTO share (subject_id, created_by, document_type, snapshot_json, created_at) VALUES (:s, :u, 'GUIDE', '{}'::jsonb, now())")
                .param("s", subjectId).param("u", ownerId).update();
        pub(token('a')).andExpect(status().isNotFound());
    }

    @Test
    void 같은_IP는_분당_30회_넘으면_429다() throws Exception {
        share(ownerId, token('a'), "1 hour", "6 days", false);
        for (int i = 0; i < 30; i++) {
            mvc.perform(get("/api/v1/public/shares/" + token('a')).with(r -> { r.setRemoteAddr("10.77.0.1"); return r; }))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/public/shares/" + token('a')).with(r -> { r.setRemoteAddr("10.77.0.1"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("COMMON_TOO_MANY_REQUESTS"));
        mvc.perform(get("/api/v1/public/shares/" + token('a')).with(r -> { r.setRemoteAddr("10.77.0.2"); return r; }))
                .andExpect(status().isOk());
    }
}
