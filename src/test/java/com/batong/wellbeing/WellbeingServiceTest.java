package com.batong.wellbeing;

import com.batong.TestcontainersConfiguration;
import com.batong.global.config.JacksonConfig;
import com.batong.global.enums.SignalType;
import com.batong.wellbeing.service.WellbeingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** AI 명세 3-6: 소진·위기 신호 저장과 소진 안내 누적 판정 (WellbeingService.record) */
@SpringBootTest
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class WellbeingServiceTest {

    @Autowired
    WellbeingService wellbeing;
    @Autowired
    JdbcClient jdbc;

    long userId;
    LocalDate today;

    @BeforeEach
    void setUp() {
        userId = jdbc.sql("INSERT INTO users (login_id, password_hash, display_name, relation, created_at, updated_at) VALUES ('wbuser', 'x', '김영희', 'PARENT', now(), now()) RETURNING user_id")
            .query(Long.class).single();
        today = LocalDate.now(JacksonConfig.KST);
    }

    @AfterEach
    void tearDown() {
        // wellbeing_signal은 ON DELETE CASCADE
        jdbc.sql("DELETE FROM users WHERE user_id = :u").param("u", userId).update();
    }

    @Test
    void BURNOUT은_오늘_날짜로_저장하고_같은_날_다시_불러도_예외_없이_한_건만_남는다() {
        wellbeing.record(userId, null, SignalType.BURNOUT);
        wellbeing.record(userId, null, SignalType.BURNOUT);

        assertThat(signalCount(SignalType.BURNOUT)).isEqualTo(1);
        assertThat(signalDates(SignalType.BURNOUT)).containsExactly(today);
    }

    @Test
    void 서로_다른_3일째가_되는_호출에서_true이고_그_전에는_false다() {
        signal(SignalType.BURNOUT, today.minusDays(2));

        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isFalse(); // 2일째
        signal(SignalType.BURNOUT, today.minusDays(1));
        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isTrue();  // 3일째
    }

    @Test
    void 오늘_포함_14일_창에서_13일_전은_세고_14일_전은_세지_않는다() {
        // 13일 전 + 어제 + 오늘 = 3일 → true
        signal(SignalType.BURNOUT, today.minusDays(13));
        signal(SignalType.BURNOUT, today.minusDays(1));
        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isTrue();
    }

    @Test
    void 창_밖인_14일_전_기록은_누적에_넣지_않는다() {
        // 14일 전 + 어제 + 오늘 = 창 안에서는 2일 → false
        signal(SignalType.BURNOUT, today.minusDays(14));
        signal(SignalType.BURNOUT, today.minusDays(1));
        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isFalse();
        assertThat(lastNoticeAt()).isNull();
    }

    @Test
    void true이면_last_burnout_notice_at을_갱신하고_바로_다시_불러도_false다() {
        signal(SignalType.BURNOUT, today.minusDays(2));
        signal(SignalType.BURNOUT, today.minusDays(1));

        Instant before = Instant.now();
        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isTrue();
        assertThat(lastNoticeAt()).isAfterOrEqualTo(before.minusSeconds(1));

        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isFalse();
    }

    @Test
    void 마지막_안내가_14일_안이면_false이고_그_전이면_true다() {
        signal(SignalType.BURNOUT, today.minusDays(2));
        signal(SignalType.BURNOUT, today.minusDays(1));

        setLastNoticeAt(Instant.now().minus(Duration.ofDays(13)));
        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isFalse();

        setLastNoticeAt(Instant.now().minus(Duration.ofDays(14)).minus(Duration.ofMinutes(1)));
        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isTrue();
    }

    @Test
    void CRISIS는_저장만_하고_항상_false이며_누적_판정에_넣지_않는다() {
        signal(SignalType.CRISIS, today.minusDays(2));
        signal(SignalType.CRISIS, today.minusDays(1));

        assertThat(wellbeing.record(userId, null, SignalType.CRISIS)).isFalse();
        assertThat(signalCount(SignalType.CRISIS)).isEqualTo(3);
        assertThat(lastNoticeAt()).isNull();

        // CRISIS 3일 + BURNOUT 오늘 1일 → BURNOUT은 1일뿐이라 false
        assertThat(wellbeing.record(userId, null, SignalType.BURNOUT)).isFalse();
    }

    @Test
    void CRISIS도_같은_날_중복은_무시한다() {
        wellbeing.record(userId, null, SignalType.CRISIS);
        wellbeing.record(userId, null, SignalType.CRISIS);

        assertThat(signalCount(SignalType.CRISIS)).isEqualTo(1);
    }

    @Test
    void 같은_날_BURNOUT과_CRISIS는_서로_다른_신호로_각각_저장된다() {
        wellbeing.record(userId, null, SignalType.BURNOUT);
        wellbeing.record(userId, null, SignalType.CRISIS);

        assertThat(signalCount(SignalType.BURNOUT)).isEqualTo(1);
        assertThat(signalCount(SignalType.CRISIS)).isEqualTo(1);
    }

    @Test
    void 일기_id가_있으면_함께_저장한다() {
        long subjectId = jdbc.sql("INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at) VALUES (:o, '가나', 2001, 'SUJI', now(), now()) RETURNING subject_id")
            .param("o", userId).query(Long.class).single();
        long diaryId = jdbc.sql("""
                INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at)
                VALUES (:s, :d, '테스트 일기', 'VOICE', 'DONE', now(), now()) RETURNING diary_id""")
            .param("s", subjectId).param("d", today).query(Long.class).single();

        wellbeing.record(userId, diaryId, SignalType.BURNOUT);

        Long saved = jdbc.sql("SELECT diary_id FROM wellbeing_signal WHERE user_id = :u AND signal_type = 'BURNOUT'")
            .param("u", userId).query(Long.class).single();
        assertThat(saved).isEqualTo(diaryId);
    }

    private void signal(SignalType type, LocalDate date) {
        jdbc.sql("INSERT INTO wellbeing_signal (user_id, signal_type, detected_date, created_at) VALUES (:u, :t, :d, now())")
            .param("u", userId).param("t", type.name()).param("d", date).update();
    }

    private int signalCount(SignalType type) {
        return jdbc.sql("SELECT count(*) FROM wellbeing_signal WHERE user_id = :u AND signal_type = :t")
            .param("u", userId).param("t", type.name()).query(Integer.class).single();
    }

    private java.util.List<LocalDate> signalDates(SignalType type) {
        return jdbc.sql("SELECT detected_date FROM wellbeing_signal WHERE user_id = :u AND signal_type = :t ORDER BY detected_date")
            .param("u", userId).param("t", type.name()).query(LocalDate.class).list();
    }

    private Instant lastNoticeAt() {
        // 컬럼이 NULL이면 optional()은 빈 값이다
        return jdbc.sql("SELECT last_burnout_notice_at FROM users WHERE user_id = :u")
            .param("u", userId).query(Timestamp.class).optional()
            .map(Timestamp::toInstant).orElse(null);
    }

    private void setLastNoticeAt(Instant at) {
        jdbc.sql("UPDATE users SET last_burnout_notice_at = :a WHERE user_id = :u")
            .param("a", Timestamp.from(at)).param("u", userId).update();
    }
}
