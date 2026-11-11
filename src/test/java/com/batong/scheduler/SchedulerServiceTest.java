package com.batong.scheduler;

import com.batong.TestcontainersConfiguration;
import com.batong.diary.repository.DiaryRepository;
import com.batong.global.enums.AnalysisStatus;
import com.batong.scheduler.service.SchedulerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRD 12-6 스케줄러 작업 3개: 공유 초안 정리, 고정본 파기, 분석 상태 복구.
 * 기준 시각(now)을 넘겨 시간을 고정한다. 결과는 DB 상태로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class SchedulerServiceTest {

    @Autowired
    SchedulerService service;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    DiaryRepository diaries;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    ApplicationContext context;

    long subjectId;
    Instant now;

    @BeforeEach
    void seed() {
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long ownerId = jdbc.sql("""
                INSERT INTO users (login_id, password_hash, created_at, updated_at)
                VALUES ('sch_owner', 'x', now(), now()) RETURNING user_id""")
            .query(Long.class).single();
        subjectId = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '민지', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
            .param("o", ownerId).query(Long.class).single();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'sch\\_%'").update();
    }

    @Test
    void 스케줄링이_켜져_있다() {
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isNotEmpty();
    }

    // 공유 초안 정리: 승인 안 된 지 1일 지난 share 삭제

    @Test
    void 하루_지난_초안은_지우고_섹션도_함께_지운다() {
        long stale = draft(Duration.ofHours(25));
        section(stale, "COMMUNICATION");

        service.purgeStaleDrafts(now);

        assertThat(shareExists(stale)).isFalse();
        assertThat(sectionCount(stale)).isZero();
    }

    @Test
    void 하루가_안_된_초안은_남긴다() {
        long fresh = draft(Duration.ofHours(23));
        section(fresh, "COMMUNICATION");

        service.purgeStaleDrafts(now);

        assertThat(shareExists(fresh)).isTrue();
        assertThat(sectionCount(fresh)).isEqualTo(1);
    }

    @Test
    void 승인된_공유는_오래됐어도_초안_정리에서_지우지_않는다() {
        long approved = approved(Duration.ofDays(3), Duration.ofDays(4));

        service.purgeStaleDrafts(now);

        assertThat(shareExists(approved)).isTrue();
    }

    // 고정본 파기: 만료 30일 지난 share의 snapshot_json NULL

    @Test
    void 만료_30일_지난_고정본만_비우고_행은_남긴다() {
        long old = approved(Duration.ofDays(40), Duration.ofDays(-31));
        long recent = approved(Duration.ofDays(36), Duration.ofDays(-29));
        long active = approved(Duration.ofHours(1), Duration.ofDays(6));

        service.clearExpiredSnapshots(now);

        assertThat(shareExists(old)).isTrue();
        assertThat(hasSnapshot(old)).isFalse();
        assertThat(hasSnapshot(recent)).isTrue();
        assertThat(hasSnapshot(active)).isTrue();
    }

    @Test
    void 초안의_고정본은_파기_대상이_아니다() {
        long draft = draft(Duration.ofHours(1));

        service.clearExpiredSnapshots(now);

        assertThat(hasSnapshot(draft)).isTrue();
    }

    // 분석 상태 복구: 2분 넘게 PROCESSING인 diary → FAILED

    @Test
    void 두_분_넘게_PROCESSING인_일기는_FAILED로_바뀐다() {
        long stuck = diary(AnalysisStatus.PROCESSING, Duration.ofMinutes(3));

        service.recoverStuckAnalyses(now);

        assertThat(statusOf(stuck)).isEqualTo(AnalysisStatus.FAILED);
        assertThat(updatedAtOf(stuck)).isEqualTo(now);
    }

    @Test
    void 분석_중인_일기와_다른_상태의_일기는_건드리지_않는다() {
        long running = diary(AnalysisStatus.PROCESSING, Duration.ofMinutes(1));
        long done = diary(AnalysisStatus.DONE, Duration.ofMinutes(30));
        long pending = diary(AnalysisStatus.PENDING, Duration.ofMinutes(30));
        long failed = diary(AnalysisStatus.FAILED, Duration.ofMinutes(30));

        service.recoverStuckAnalyses(now);

        assertThat(statusOf(running)).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(statusOf(done)).isEqualTo(AnalysisStatus.DONE);
        assertThat(statusOf(pending)).isEqualTo(AnalysisStatus.PENDING);
        assertThat(statusOf(failed)).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void 복구되면_막혀_있던_일기_삭제가_가능해진다() {
        long stuck = diary(AnalysisStatus.PROCESSING, Duration.ofMinutes(3));
        long running = diary(AnalysisStatus.PROCESSING, Duration.ofMinutes(1));
        assertThat(deleteUnlessAnalyzing(stuck)).isZero(); // 복구 전에는 분석 중이라 삭제가 막힌다 (DIARY-05)

        service.recoverStuckAnalyses(now);

        assertThat(deleteUnlessAnalyzing(stuck)).isEqualTo(1);
        assertThat(deleteUnlessAnalyzing(running)).isZero(); // 아직 분석 중인 일기는 계속 막힌다
    }

    @Test
    void 복구되면_일기를_다시_분석할_수_있다() {
        long stuck = diary(AnalysisStatus.PROCESSING, Duration.ofMinutes(3));
        assertThat(startAnalysis(stuck)).isZero(); // 복구 전에는 이미 분석 중이라 시작할 수 없다 (DIARY-02)

        service.recoverStuckAnalyses(now);

        assertThat(startAnalysis(stuck)).isEqualTo(1);
    }

    @Test
    void 두_번_실행해도_두_번째는_바꿀_게_없다() {
        draft(Duration.ofHours(25));
        approved(Duration.ofDays(40), Duration.ofDays(-31));
        diary(AnalysisStatus.PROCESSING, Duration.ofMinutes(3));

        service.purgeStaleDrafts(now);
        service.clearExpiredSnapshots(now);
        service.recoverStuckAnalyses(now);

        assertThat(service.purgeStaleDrafts(now)).isZero();
        assertThat(service.clearExpiredSnapshots(now)).isZero();
        assertThat(service.recoverStuckAnalyses(now)).isZero();
    }

    // 데이터 준비

    private long draft(Duration age) {
        return jdbc.sql("""
                INSERT INTO share (subject_id, document_type, snapshot_json, created_at)
                VALUES (:s, 'GUIDE', '{}'::jsonb, :at) RETURNING share_id""")
            .param("s", subjectId).param("at", Timestamp.from(now.minus(age)))
            .query(Long.class).single();
    }

    /** 승인된 공유. expiresIn이 음수면 이미 만료된 것 */
    private long approved(Duration approvedAgo, Duration expiresIn) {
        Instant approvedAt = now.minus(approvedAgo);
        return jdbc.sql("""
                INSERT INTO share (subject_id, document_type, snapshot_json, token_hash, approved_at, expires_at, created_at)
                VALUES (:s, 'GUIDE', '{}'::jsonb, :h, :ap, :ex, :ap) RETURNING share_id""")
            .param("s", subjectId).param("h", tokenHash())
            .param("ap", Timestamp.from(approvedAt)).param("ex", Timestamp.from(now.plus(expiresIn)))
            .query(Long.class).single();
    }

    private void section(long shareId, String code) {
        jdbc.sql("INSERT INTO share_section (share_id, section_code) VALUES (:i, :c)")
            .param("i", shareId).param("c", code).update();
    }

    private long diary(AnalysisStatus status, Duration updatedAgo) {
        return jdbc.sql("""
                INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at)
                VALUES (:s, CURRENT_DATE, '테스트 일기', 'VOICE', :st, :at, :at) RETURNING diary_id""")
            .param("s", subjectId).param("st", status.name()).param("at", Timestamp.from(now.minus(updatedAgo)))
            .query(Long.class).single();
    }

    /** token_hash는 CHAR(64) UNIQUE. 가상 값만 쓴다 */
    private static String tokenHash() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2);
    }

    // 상태 확인

    private boolean shareExists(long shareId) {
        return count("SELECT count(*) FROM share WHERE share_id = :i", shareId) == 1;
    }

    private boolean hasSnapshot(long shareId) {
        return count("SELECT count(*) FROM share WHERE share_id = :i AND snapshot_json IS NOT NULL", shareId) == 1;
    }

    private long sectionCount(long shareId) {
        return count("SELECT count(*) FROM share_section WHERE share_id = :i", shareId);
    }

    private long count(String sql, long id) {
        return jdbc.sql(sql).param("i", id).query(Long.class).single();
    }

    private AnalysisStatus statusOf(long diaryId) {
        return AnalysisStatus.valueOf(jdbc.sql("SELECT analysis_status FROM diary WHERE diary_id = :i")
            .param("i", diaryId).query(String.class).single());
    }

    private Instant updatedAtOf(long diaryId) {
        return jdbc.sql("SELECT updated_at FROM diary WHERE diary_id = :i")
            .param("i", diaryId).query((rs, n) -> rs.getTimestamp("updated_at").toInstant()).single();
    }

    /** DiaryService와 같이 트랜잭션 안에서 호출한다 */
    private int deleteUnlessAnalyzing(long diaryId) {
        return tx.execute(s -> diaries.deleteUnlessAnalyzing(diaryId, subjectId, AnalysisStatus.PROCESSING));
    }

    private int startAnalysis(long diaryId) {
        return tx.execute(s -> diaries.startAnalysis(diaryId, subjectId, AnalysisStatus.PROCESSING, now));
    }
}
