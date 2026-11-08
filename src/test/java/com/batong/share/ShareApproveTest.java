package com.batong.share;

import com.batong.TestcontainersConfiguration;
import com.batong.global.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SHARE-03: 토큰은 응답에만 원문으로 나가고 DB에는 SHA-256 해시만, 생성자만 승인, 한 번만 승인, 7일 만료. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ShareApproveTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ObjectMapper objectMapper;

    long ownerId;
    long coId;
    long strangerId;
    long subjectId;
    long otherSubjectId;
    long shareId;

    @BeforeEach
    void setUp() {
        ownerId = user("appr_owner");
        coId = user("appr_co");
        strangerId = user("appr_other");
        subjectId = subject(ownerId);
        otherSubjectId = subject(strangerId);
        long invitationId = jdbc.sql("""
                INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id""")
                .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
                .param("s", subjectId).param("c", coId).param("i", invitationId).update();
        jdbc.sql("INSERT INTO guide_entry (subject_id, section, content, source_type, created_at, updated_at) VALUES (:s, 'COMMUNICATION', '손짓으로 알려줌', 'MANUAL', now(), now())")
                .param("s", subjectId).update();
        shareId = draftBy(ownerId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'appr\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private long subject(long owner) {
        return jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
                .param("o", owner).query(Long.class).single();
    }

    private long draftBy(long userId) {
        return jdbc.sql("""
                INSERT INTO share (subject_id, created_by, document_type, snapshot_json, created_at)
                VALUES (:s, :u, 'GUIDE', '{"basicInfo":{"name":"가나","age":25},"summaryLines":[],"sections":[]}'::jsonb, now())
                RETURNING share_id""").param("s", subjectId).param("u", userId).query(Long.class).single();
    }

    private String bearer(long userId) {
        return "Bearer " + jwt.issue(userId);
    }

    private org.springframework.test.web.servlet.ResultActions approve(long userId, long subject, long share) throws Exception {
        return mvc.perform(post("/api/v1/subjects/" + subject + "/shares/" + share + "/approve")
                .header("Authorization", bearer(userId)).contentType(MediaType.APPLICATION_JSON));
    }

    private static String sha256(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 승인하면_링크를_한_번만_주고_DB에는_해시만_저장한다() throws Exception {
        MvcResult result = approve(ownerId, subjectId, shareId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareId").value(shareId))
                .andExpect(jsonPath("$.data.documentType").value("GUIDE"))
                .andExpect(jsonPath("$.data.notice").value("이 링크는 지금만 복사할 수 있어요."))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String url = data.path("shareUrl").asText();
        assertThat(url).startsWith("http://localhost:5173/s/");
        String token = url.substring(url.lastIndexOf('/') + 1);

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        var row = jdbc.sql("SELECT token_hash, approved_at, expires_at FROM share WHERE share_id = :i")
                .param("i", shareId).query().singleRow();
        assertThat(row.get("token_hash")).isEqualTo(sha256(token));
        assertThat(((String) row.get("token_hash")).trim()).hasSize(64);
        // 원문 토큰은 share 테이블 어디에도 없다
        assertThat(jdbc.sql("SELECT count(*) FROM share WHERE share::text LIKE :t").param("t", "%" + token + "%")
                .query(Integer.class).single()).isZero();
        Instant approvedAt = ((java.sql.Timestamp) row.get("approved_at")).toInstant();
        Instant expiresAt = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        assertThat(Duration.between(approvedAt, expiresAt)).isEqualTo(Duration.ofDays(7));
        assertThat(Duration.between(approvedAt, Instant.now()).abs()).isLessThan(Duration.ofSeconds(30));
    }

    @Test
    void 토큰은_매번_다르다() throws Exception {
        long second = draftBy(ownerId);
        String a = urlOf(approve(ownerId, subjectId, shareId).andReturn());
        String b = urlOf(approve(ownerId, subjectId, second).andReturn());
        assertThat(a).isNotEqualTo(b);
    }

    private String urlOf(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("shareUrl").asText();
    }

    @Test
    void 이미_승인한_초안은_409이고_토큰이_바뀌지_않는다() throws Exception {
        approve(ownerId, subjectId, shareId).andExpect(status().isOk());
        String before = jdbc.sql("SELECT token_hash FROM share WHERE share_id = :i").param("i", shareId)
                .query(String.class).single();
        approve(ownerId, subjectId, shareId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SHARE_ALREADY_APPROVED"));
        assertThat(jdbc.sql("SELECT token_hash FROM share WHERE share_id = :i").param("i", shareId)
                .query(String.class).single()).isEqualTo(before);
    }

    @Test
    void 동시에_15번_승인해도_한_번만_성공한다() throws Exception {
        var pool = Executors.newFixedThreadPool(15);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            futures.add(pool.submit(() -> approve(ownerId, subjectId, shareId).andReturn().getResponse().getStatus()));
        }
        int ok = 0;
        int conflict = 0;
        for (var f : futures) {
            int code = f.get();
            if (code == 200) {
                ok++;
            } else if (code == 409) {
                conflict++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(14);
    }

    @Test
    void 초안을_만든_사람만_승인할_수_있다() throws Exception {
        long coDraft = draftBy(coId);
        // OWNER라도 남이 만든 초안은 승인할 수 없고, CO도 OWNER의 초안은 승인할 수 없다
        approve(ownerId, subjectId, coDraft).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SHARE_FORBIDDEN"));
        approve(coId, subjectId, shareId).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SHARE_FORBIDDEN"));
        approve(coId, subjectId, coDraft).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT approved_at IS NULL FROM share WHERE share_id = :i").param("i", shareId)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void 없는_초안과_다른_대상자의_초안은_404다() throws Exception {
        approve(ownerId, subjectId, 999_999).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SHARE_NOT_FOUND"));
        long othersDraft = jdbc.sql("""
                INSERT INTO share (subject_id, created_by, document_type, snapshot_json, created_at)
                VALUES (:s, :u, 'GUIDE', '{}'::jsonb, now()) RETURNING share_id""")
                .param("s", otherSubjectId).param("u", strangerId).query(Long.class).single();
        approve(ownerId, subjectId, othersDraft).andExpect(status().isNotFound());
        approve(ownerId, otherSubjectId, othersDraft).andExpect(status().isForbidden());
    }

    @Test
    void 무관한_사용자와_토큰_없는_요청은_거절한다() throws Exception {
        approve(strangerId, subjectId, shareId).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/subjects/" + subjectId + "/shares/" + shareId + "/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 공동_관리자에서_해제되면_자기_초안도_승인할_수_없다() throws Exception {
        long coDraft = draftBy(coId);
        jdbc.sql("DELETE FROM co_manager WHERE subject_id = :s AND user_id = :u")
                .param("s", subjectId).param("u", coId).update();
        approve(coId, subjectId, coDraft).andExpect(status().isForbidden());
    }

    @Test
    void 만든_사람이_탈퇴해_created_by가_NULL이면_아무도_승인할_수_없다() throws Exception {
        jdbc.sql("UPDATE share SET created_by = NULL WHERE share_id = :i").param("i", shareId).update();
        approve(ownerId, subjectId, shareId).andExpect(status().isForbidden());
    }
}
