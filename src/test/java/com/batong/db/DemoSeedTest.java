package com.batong.db;

import com.batong.TestcontainersConfiguration;
import com.batong.analysis.service.CrisisKeywordChecker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R__seed_demo.sql (#37)이 데모 계정·대상자·동의·일기를 넣는지 본다.
 * Flyway가 테스트 DB에 시드를 이미 적용한 상태에서 시작하고, 각 테스트는 롤백된다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DemoSeedTest {

    private static final String DEMO_LOGIN_ID = "batongdemo01";
    private static final String SEED_SCRIPT = "db/migration/R__seed_demo.sql";
    private static final String EVAL_SET = "ai-eval/analyze_diary.jsonl";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 데모_계정과_대상자와_필수_동의_4종이_있다() {
        // BCrypt 해시($2a/$2b/$2y, 60자)가 아니면 자리 표시(REPLACE_WITH_BCRYPT_HASH)가 남은 것이다
        String hash = jdbc.queryForObject(
            "SELECT password_hash FROM users WHERE login_id = ?", String.class, DEMO_LOGIN_ID);
        assertThat(hash).matches("\\$2[aby]\\$\\d\\d\\$.{53}");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM care_subject s JOIN users u ON u.user_id = s.owner_user_id
                WHERE u.login_id = ? AND s.name = '민지'""", Long.class, DEMO_LOGIN_ID)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT consent_type) FROM consent c JOIN users u ON u.user_id = c.user_id
                WHERE u.login_id = ? AND c.terms_version = '1.0'""", Long.class, DEMO_LOGIN_ID)).isEqualTo(4);
    }

    @Test
    void 시드_일기는_모두_평가셋_문장과_글자까지_같다() throws Exception {
        Map<String, JsonNode> evalByKey = evalSetByKey();

        List<String[]> seeded = seededDiaries();

        assertThat(seeded).isNotEmpty();
        for (String[] diary : seeded) {
            // Mock 응답은 원문 정규화 해시로 찾으므로 한 글자라도 다르면 응답을 못 찾는다(#87)
            assertThat(evalByKey).as("평가셋에 없는 시드 문장: %s %s", diary[0], diary[1])
                .containsKey(diary[0] + "|" + diary[1]);
        }
    }

    @Test
    void 위기_키워드에_걸리는_시드_일기는_평가셋이_위기로_정한_것뿐이다() throws Exception {
        CrisisKeywordChecker checker = new CrisisKeywordChecker(new DefaultResourceLoader());
        Map<String, JsonNode> evalByKey = evalSetByKey();

        for (String[] diary : seededDiaries()) {
            boolean crisisInEvalSet = "CRISIS".equals(evalByKey.get(diary[0] + "|" + diary[1])
                .at("/expected/safetyLevel").asText());
            // 시연 화면에 의도하지 않은 위기 안내가 뜨지 않게 한다(#92 리뷰 2번)
            assertThat(checker.matches(diary[1])).as("%s %s", diary[0], diary[1]).isEqualTo(crisisInEvalSet);
        }
    }

    @Test
    void 같은_시드를_다시_실행해도_중복이_생기지_않는다() {
        long users = count("SELECT count(*) FROM users WHERE login_id = ?");
        long subjects = count("""
                SELECT count(*) FROM care_subject s JOIN users u ON u.user_id = s.owner_user_id
                WHERE u.login_id = ?""");
        long consents = count("""
                SELECT count(*) FROM consent c JOIN users u ON u.user_id = c.user_id WHERE u.login_id = ?""");
        long diaries = count("""
                SELECT count(*) FROM diary d JOIN care_subject s ON s.subject_id = d.subject_id
                JOIN users u ON u.user_id = s.owner_user_id WHERE u.login_id = ?""");

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                new EncodedResource(new ClassPathResource(SEED_SCRIPT), StandardCharsets.UTF_8));
            return null;
        });

        assertThat(count("SELECT count(*) FROM users WHERE login_id = ?")).isEqualTo(users);
        assertThat(count("""
                SELECT count(*) FROM care_subject s JOIN users u ON u.user_id = s.owner_user_id
                WHERE u.login_id = ?""")).isEqualTo(subjects);
        assertThat(count("""
                SELECT count(*) FROM consent c JOIN users u ON u.user_id = c.user_id WHERE u.login_id = ?""")).isEqualTo(consents);
        assertThat(count("""
                SELECT count(*) FROM diary d JOIN care_subject s ON s.subject_id = d.subject_id
                JOIN users u ON u.user_id = s.owner_user_id WHERE u.login_id = ?""")).isEqualTo(diaries);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class, DEMO_LOGIN_ID);
    }

    /** {날짜, 본문} 목록 */
    private List<String[]> seededDiaries() {
        return jdbc.query("""
                SELECT d.diary_date::text, d.content FROM diary d
                JOIN care_subject s ON s.subject_id = d.subject_id
                JOIN users u ON u.user_id = s.owner_user_id
                WHERE u.login_id = ? ORDER BY d.diary_date, d.diary_id""",
            (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, DEMO_LOGIN_ID);
    }

    /** "날짜|본문" -> 평가셋 한 줄 */
    private Map<String, JsonNode> evalSetByKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> byKey = new HashMap<>();
        try (InputStream in = new ClassPathResource(EVAL_SET).getInputStream()) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode row = mapper.readTree(line);
                byKey.put(row.get("diaryDate").asText() + "|" + row.get("text").asText(), row);
            }
        }
        return byKey;
    }
}
