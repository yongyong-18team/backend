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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R__seed_demo.sql (#37, #96, #98)이 데모 계정 2개와 대상자·동의·일기를 넣는지 본다.
 * Flyway가 테스트 DB에 시드를 이미 적용한 상태에서 시작하고, 각 테스트는 롤백된다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DemoSeedTest {

    /** 주 계정, 예비 계정 */
    private static final List<String> DEMO_LOGIN_IDS = List.of("batongdemo01", "batongdemo02");
    private static final String SEED_SCRIPT = "db/migration/R__seed_demo.sql";
    private static final String EVAL_SET = "ai-eval/analyze_diary.jsonl";
    private static final int DIARY_COUNT = 26;
    /** 시연일(2026-11-15) 기준 최근 7일. 주간 리포트가 오늘(KST) 기준 최근 7일을 조회한다(#97) */
    private static final LocalDate WINDOW_START = LocalDate.of(2026, 11, 9);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 11, 15);

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 데모_계정_두_개와_대상자와_필수_동의_4종이_있다() {
        for (String loginId : DEMO_LOGIN_IDS) {
            // BCrypt 해시($2a/$2b/$2y, 60자)가 아니면 자리 표시가 남은 것이다
            String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE login_id = ?", String.class, loginId);
            assertThat(hash).as(loginId).matches("\\$2[aby]\\$\\d\\d\\$.{53}");

            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM care_subject s JOIN users u ON u.user_id = s.owner_user_id
                    WHERE u.login_id = ? AND s.name = '민지'""", Long.class, loginId)).as(loginId).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(DISTINCT consent_type) FROM consent c JOIN users u ON u.user_id = c.user_id
                    WHERE u.login_id = ? AND c.terms_version = '1.0'""", Long.class, loginId)).as(loginId).isEqualTo(4);
        }
    }

    @Test
    void 두_계정은_같은_비밀번호_해시를_쓴다() {
        // 비밀번호를 같게 두기로 했다(#98). 한 번 붙여넣으면 어느 계정으로든 로그인된다
        Long distinctHashes = jdbc.queryForObject(
            "SELECT count(DISTINCT password_hash) FROM users WHERE login_id IN (?, ?)",
            Long.class, DEMO_LOGIN_IDS.get(0), DEMO_LOGIN_IDS.get(1));
        assertThat(distinctHashes).isOne();
    }

    @Test
    void 계정마다_일기_26건이_시연_주간에_분석_전_상태로_있다() {
        for (String loginId : DEMO_LOGIN_IDS) {
            assertThat(diaryCount(loginId)).as(loginId).isEqualTo(DIARY_COUNT);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM diary d JOIN care_subject s ON s.subject_id = d.subject_id
                    JOIN users u ON u.user_id = s.owner_user_id
                    WHERE u.login_id = ? AND (d.diary_date < ? OR d.diary_date > ?)""",
                Long.class, loginId, WINDOW_START, WINDOW_END)).as("시연 주간 밖의 일기: " + loginId).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM diary d JOIN care_subject s ON s.subject_id = d.subject_id
                    JOIN users u ON u.user_id = s.owner_user_id
                    WHERE u.login_id = ? AND d.analysis_status = 'PENDING'""",
                Long.class, loginId)).as(loginId).isEqualTo(DIARY_COUNT);
        }
    }

    @Test
    void 두_계정의_일기는_날짜와_본문이_같다() {
        // 배열 목록이라 equals 가 아니라 값으로 비교한다
        assertThat(seededDiaries(DEMO_LOGIN_IDS.get(1)))
            .usingRecursiveComparison()
            .isEqualTo(seededDiaries(DEMO_LOGIN_IDS.get(0)));
    }

    @Test
    void 시드_일기는_모두_평가셋_본문과_글자까지_같다() throws Exception {
        Map<String, JsonNode> evalByText = evalSetByText();

        for (String loginId : DEMO_LOGIN_IDS) {
            List<String[]> seeded = seededDiaries(loginId);
            assertThat(seeded).isNotEmpty();
            for (String[] diary : seeded) {
                // Mock 응답은 원문 정규화 해시로 찾으므로 한 글자라도 다르면 응답을 못 찾는다(#87). 날짜는 키에 들어가지 않는다
                assertThat(evalByText).as("평가셋에 없는 시드 문장: %s %s", loginId, diary[1])
                    .containsKey(diary[1]);
            }
        }
    }

    @Test
    void 위기_키워드에_걸리는_시드_일기는_평가셋이_위기로_정한_것뿐이다() throws Exception {
        CrisisKeywordChecker checker = new CrisisKeywordChecker(new DefaultResourceLoader());
        Map<String, JsonNode> evalByText = evalSetByText();

        for (String loginId : DEMO_LOGIN_IDS) {
            for (String[] diary : seededDiaries(loginId)) {
                boolean crisisInEvalSet = "CRISIS".equals(evalByText.get(diary[1])
                    .at("/expected/safetyLevel").asText());
                // 시연 화면에 의도하지 않은 위기 안내가 뜨지 않게 한다(#92 리뷰 2번)
                assertThat(checker.matches(diary[1])).as("%s %s", loginId, diary[1]).isEqualTo(crisisInEvalSet);
            }
        }
    }

    @Test
    void 같은_시드를_다시_실행하면_리허설_데이터가_지워지고_중복이_생기지_않는다() {
        // 리허설 중 시연 계정에 직접 입력한 일기를 흉내 낸다
        jdbc.update("""
            INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at)
            SELECT s.subject_id, DATE '2026-11-12', '리허설 중 직접 입력한 일기', 'TEXT', 'PENDING', now(), now()
            FROM care_subject s JOIN users u ON u.user_id = s.owner_user_id
            WHERE u.login_id = ?""", DEMO_LOGIN_IDS.get(0));
        assertThat(diaryCount(DEMO_LOGIN_IDS.get(0))).isEqualTo(DIARY_COUNT + 1);

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                new EncodedResource(new ClassPathResource(SEED_SCRIPT), StandardCharsets.UTF_8));
            return null;
        });

        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE login_id IN (?, ?)",
            Long.class, DEMO_LOGIN_IDS.get(0), DEMO_LOGIN_IDS.get(1))).isEqualTo(2);
        for (String loginId : DEMO_LOGIN_IDS) {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM care_subject s JOIN users u ON u.user_id = s.owner_user_id
                    WHERE u.login_id = ?""", Long.class, loginId)).as(loginId).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM consent c JOIN users u ON u.user_id = c.user_id
                    WHERE u.login_id = ?""", Long.class, loginId)).as(loginId).isEqualTo(4);
            assertThat(diaryCount(loginId)).as(loginId).isEqualTo(DIARY_COUNT);
        }
    }

    private long diaryCount(String loginId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM diary d JOIN care_subject s ON s.subject_id = d.subject_id
                JOIN users u ON u.user_id = s.owner_user_id WHERE u.login_id = ?""", Long.class, loginId);
    }

    /** {날짜, 본문} 목록 */
    private List<String[]> seededDiaries(String loginId) {
        return jdbc.query("""
                SELECT d.diary_date::text, d.content FROM diary d
                JOIN care_subject s ON s.subject_id = d.subject_id
                JOIN users u ON u.user_id = s.owner_user_id
                WHERE u.login_id = ? ORDER BY d.diary_date, d.diary_id""",
            (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, loginId);
    }

    /** 본문 -> 평가셋 한 줄 */
    private Map<String, JsonNode> evalSetByText() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> byText = new HashMap<>();
        try (InputStream in = new ClassPathResource(EVAL_SET).getInputStream()) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode row = mapper.readTree(line);
                byText.put(row.get("text").asText(), row);
            }
        }
        return byText;
    }
}
