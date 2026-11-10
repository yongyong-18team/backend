package com.batong.subject;

import com.batong.TestcontainersConfiguration;
import com.batong.global.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ONB-01: 일괄 저장, 빈 답변 생략, 입력 검증, 필수 동의, 중복 등록 409, 실패 시 전체 롤백, 동시 요청 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class OnboardingApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ObjectMapper objectMapper;

    long userId;
    String token;

    @BeforeEach
    void seed() {
        userId = user("onb_user");
        token = "Bearer " + jwt.issue(userId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'onb\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    /** API 명세 ONB-01 예시와 같은 모양. 값은 가상 데이터 */
    private ObjectNode validRequest() throws Exception {
        return (ObjectNode) objectMapper.readTree("""
                {"guardian":{"displayName":"김영희","relation":"PARENT"},
                 "subject":{"name":"민지","birthYear":2001,"district":"SUJI","dong":"풍덕천동"},
                 "profile":{"Q1":"아침 7시에 일어나 8시에 주간활동센터에 가요.",
                            "Q2":"매운 음식을 못 먹고, 저녁 약은 식후 30분에 먹어요.",
                            "Q3":"큰 소리가 나면 귀를 막고 소리를 질러요.",
                            "Q4":"차에 태워 트로트를 틀어주면 진정돼요.",
                            "Q5":null},
                 "consents":[{"consentType":"PRIVACY","termsVersion":"1.0"},
                             {"consentType":"SENSITIVE_INFO","termsVersion":"1.0"},
                             {"consentType":"AI_PROCESSING","termsVersion":"1.0"},
                             {"consentType":"SUBJECT_AUTHORITY","termsVersion":"1.0"}]}""");
    }

    private ObjectNode request(Consumer<ObjectNode> change) throws Exception {
        ObjectNode body = validRequest();
        change.accept(body);
        return body;
    }

    private ResultActions onboard(String auth, Object body) throws Exception {
        var req = post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body));
        if (auth != null) {
            req.header("Authorization", auth);
        }
        return mvc.perform(req);
    }

    private long count(String sql) {
        return jdbc.sql(sql).param("u", userId).query(Long.class).single();
    }

    private void assertNothingSaved() {
        assertThat(count("SELECT count(*) FROM care_subject WHERE owner_user_id = :u")).isZero();
        assertThat(count("SELECT count(*) FROM consent WHERE user_id = :u")).isZero();
        assertThat(count("SELECT count(*) FROM users WHERE user_id = :u AND display_name IS NOT NULL")).isZero();
    }

    @Test
    void 보호자_대상자_동의_설명서를_한_번에_저장한다() throws Exception {
        String body = onboard(token, validRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdGuideEntryCount").value(4))
                .andReturn().getResponse().getContentAsString();
        long subjectId = objectMapper.readTree(body).path("data").path("subjectId").asLong();

        Map<String, Object> user = jdbc.sql("SELECT display_name, relation FROM users WHERE user_id = :u")
                .param("u", userId).query().singleRow();
        assertThat(user).containsEntry("display_name", "김영희").containsEntry("relation", "PARENT");

        Map<String, Object> subject = jdbc.sql("SELECT owner_user_id, name, birth_year, district, dong, emergency_contact FROM care_subject WHERE subject_id = :s")
                .param("s", subjectId).query().singleRow();
        assertThat(subject).containsEntry("owner_user_id", userId).containsEntry("name", "민지")
                .containsEntry("district", "SUJI").containsEntry("dong", "풍덕천동");
        assertThat(((Number) subject.get("birth_year")).intValue()).isEqualTo(2001);
        assertThat(subject.get("emergency_contact")).isNull();

        assertThat(jdbc.sql("SELECT consent_type FROM consent WHERE user_id = :u AND subject_id = :s AND terms_version = '1.0'")
                .param("u", userId).param("s", subjectId).query(String.class).list())
                .containsExactlyInAnyOrder("PRIVACY", "SENSITIVE_INFO", "AI_PROCESSING", "SUBJECT_AUTHORITY");

        List<Map<String, Object>> entries = jdbc.sql("""
                SELECT section, content, source_type, src_question_code, src_diary_id FROM guide_entry
                WHERE subject_id = :s ORDER BY src_question_code""")
                .param("s", subjectId).query().listOfRows();
        assertThat(entries).hasSize(4);
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.get("source_type")).isEqualTo("ONBOARDING");
            assertThat(e.get("src_diary_id")).isNull();
        });
        assertThat(entries).extracting(e -> e.get("src_question_code") + ":" + e.get("section"))
                .containsExactly("Q1:DAILY_LIFE", "Q2:MEDICATION", "Q3:BEHAVIOR_RESPONSE", "Q4:BEHAVIOR_RESPONSE");
        assertThat(entries.get(1).get("content")).isEqualTo("매운 음식을 못 먹고, 저녁 약은 식후 30분에 먹어요.");

        // 온보딩 후에는 주 보호자로 대상자 API를 쓸 수 있다
        mvc.perform(get("/api/v1/subjects/" + subjectId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("민지"));
    }

    @Test
    void 빈_답변은_항목을_만들지_않고_앞뒤_공백은_지운다() throws Exception {
        String body = onboard(token, request(r -> {
            ObjectNode p = (ObjectNode) r.get("profile");
            p.put("Q1", "  ");
            p.put("Q2", "");
            p.putNull("Q3");
            p.remove("Q4");
            p.put("Q5", "  손짓으로 의사를 표현해요.\n");
            ((ObjectNode) r.get("subject")).put("name", "  민지 ");
        }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdGuideEntryCount").value(1))
                .andReturn().getResponse().getContentAsString();
        long subjectId = objectMapper.readTree(body).path("data").path("subjectId").asLong();

        assertThat(jdbc.sql("SELECT section || ':' || content FROM guide_entry WHERE subject_id = :s")
                .param("s", subjectId).query(String.class).list())
                .containsExactly("COMMUNICATION:손짓으로 의사를 표현해요.");
        assertThat(jdbc.sql("SELECT name FROM care_subject WHERE subject_id = :s").param("s", subjectId)
                .query(String.class).single()).isEqualTo("민지");
    }

    @Test
    void profile이_없거나_dong이_비면_항목_없이_등록된다() throws Exception {
        String body = onboard(token, request(r -> {
            r.remove("profile");
            ((ObjectNode) r.get("subject")).put("dong", " ");
        }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdGuideEntryCount").value(0))
                .andReturn().getResponse().getContentAsString();
        long subjectId = objectMapper.readTree(body).path("data").path("subjectId").asLong();
        assertThat(jdbc.sql("SELECT dong FROM care_subject WHERE subject_id = :s").param("s", subjectId)
                .query((rs, n) -> String.valueOf(rs.getString("dong"))).single()).isEqualTo("null");
    }

    @Test
    void 대상자_이름과_출생_연도_오류는_전용_코드로_거절하고_아무것도_남기지_않는다() throws Exception {
        for (String name : new String[]{"민", " 민 ", "가".repeat(21), "   "}) {
            onboard(token, request(r -> ((ObjectNode) r.get("subject")).put("name", name)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SUBJECT_INVALID_NAME"));
        }
        onboard(token, request(r -> ((ObjectNode) r.get("subject")).remove("name")))
                .andExpect(jsonPath("$.error.code").value("SUBJECT_INVALID_NAME"));
        for (int year : new int[]{1929, 3000}) {
            onboard(token, request(r -> ((ObjectNode) r.get("subject")).put("birthYear", year)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SUBJECT_INVALID_BIRTH_YEAR"));
        }
        onboard(token, request(r -> ((ObjectNode) r.get("subject")).remove("birthYear")))
                .andExpect(jsonPath("$.error.code").value("SUBJECT_INVALID_BIRTH_YEAR"));
        assertNothingSaved();
    }

    @Test
    void 필수_동의가_빠지거나_버전이_다르면_SUBJECT_CONSENT_REQUIRED() throws Exception {
        List<Consumer<ObjectNode>> bad = List.of(
                r -> ((ArrayNode) r.get("consents")).remove(3),
                r -> r.remove("consents"),
                r -> r.putArray("consents"),
                r -> ((ObjectNode) r.get("consents").get(2)).put("termsVersion", "0.9"),
                r -> ((ObjectNode) r.get("consents").get(2)).remove("termsVersion"),
                r -> ((ObjectNode) r.get("consents").get(0)).putNull("consentType"));
        for (Consumer<ObjectNode> change : bad) {
            onboard(token, request(change))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SUBJECT_CONSENT_REQUIRED"));
        }
        assertNothingSaved();
    }

    @Test
    void 같은_동의가_두_번_와도_한_번만_저장한다() throws Exception {
        onboard(token, request(r -> ((ArrayNode) r.get("consents")).addObject()
                .put("consentType", "PRIVACY").put("termsVersion", "1.0")))
                .andExpect(status().isCreated());
        assertThat(count("SELECT count(*) FROM consent WHERE user_id = :u")).isEqualTo(4);
    }

    @Test
    void 그_밖의_잘못된_입력은_COMMON_VALIDATION이고_아무것도_남기지_않는다() throws Exception {
        List<Object> bad = new ArrayList<>(List.of(
                request(r -> r.remove("guardian")),
                request(r -> r.remove("subject")),
                request(r -> ((ObjectNode) r.get("guardian")).remove("displayName")),
                request(r -> ((ObjectNode) r.get("guardian")).remove("relation")),
                request(r -> ((ObjectNode) r.get("guardian")).put("displayName", "가".repeat(31))),
                request(r -> ((ObjectNode) r.get("subject")).remove("district")),
                request(r -> ((ObjectNode) r.get("subject")).put("district", "SEOUL")),
                request(r -> ((ObjectNode) r.get("subject")).put("dong", "가".repeat(21))),
                request(r -> ((ObjectNode) r.get("profile")).put("Q3", "가".repeat(501))),
                request(r -> ((ObjectNode) r.get("profile")).put("Q3", "소리\u0000지름")),
                request(r -> ((ObjectNode) r.get("subject")).put("name", "민\u0000지")),
                "{}", "not json"));
        for (Object body : bad) {
            assertThat(onboard(token, body).andReturn().getResponse().getStatus()).as(String.valueOf(body)).isEqualTo(400);
        }
        assertNothingSaved();

        // 경계값은 통과한다: 이모지 20자 dong, 500자 답변
        onboard(token, request(r -> {
            ((ObjectNode) r.get("subject")).put("dong", "😀".repeat(20));
            ((ObjectNode) r.get("profile")).put("Q3", "가".repeat(500));
        })).andExpect(status().isCreated());
    }

    @Test
    void 이미_대상자가_있으면_입력과_관계없이_409이고_기존_데이터를_바꾸지_않는다() throws Exception {
        onboard(token, validRequest()).andExpect(status().isCreated());

        onboard(token, request(r -> ((ObjectNode) r.get("guardian")).put("displayName", "다른이름")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ONB_ALREADY_COMPLETED"));
        onboard(token, "{}").andExpect(status().isConflict());

        assertThat(count("SELECT count(*) FROM care_subject WHERE owner_user_id = :u")).isOne();
        assertThat(jdbc.sql("SELECT display_name FROM users WHERE user_id = :u").param("u", userId)
                .query(String.class).single()).isEqualTo("김영희");
    }

    @Test
    void 공동_관리자인_사용자도_자기_대상자를_등록할_수_있다() throws Exception {
        long otherOwner = user("onb_owner");
        long otherSubject = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
                .param("o", otherOwner).query(Long.class).single();
        long inv = jdbc.sql("INSERT INTO invitation (subject_id, invitee_user_id, status, created_at) VALUES (:s, :u, 'ACCEPTED', now()) RETURNING invitation_id")
                .param("s", otherSubject).param("u", userId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :u, :i, now())")
                .param("s", otherSubject).param("u", userId).param("i", inv).update();

        onboard(token, validRequest()).andExpect(status().isCreated());
    }

    @Test
    void 동시에_두_번_보내도_대상자는_하나만_생긴다() throws Exception {
        String body = objectMapper.writeValueAsString(validRequest());
        var pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<Integer> call = () -> onboard(token, body).andReturn().getResponse().getStatus();
            results.add(pool.submit(call));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 409);
        assertThat(count("SELECT count(*) FROM care_subject WHERE owner_user_id = :u")).isOne();
        assertThat(count("SELECT count(*) FROM consent WHERE user_id = :u")).isEqualTo(4);
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        onboard(null, validRequest()).andExpect(status().isUnauthorized());
    }
}
