package com.batong.wellbeing.service;

import com.batong.global.config.JacksonConfig;
import com.batong.global.enums.SignalType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class WellbeingService {

    /** 소진 안내 판정 창(오늘 포함 14일)과 안내 재노출 간격 */
    private static final int WINDOW_DAYS = 14;
    /** 창 안에서 BURNOUT이 기록된 날짜가 이 값 이상이면 안내 대상 */
    private static final int NOTICE_THRESHOLD_DAYS = 3;

    private final JdbcClient jdbc;

    public WellbeingService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 소진·위기 신호를 저장하고, 소진 안내를 띄울지 판정한다. (AI 명세 3-6)
     *
     * 호출: DIARY-02 분석 트랜잭션(TRD 8-3 Tx2) 안에서 호출한다. 이 메서드는 트랜잭션을 새로 열지 않는다.
     *
     * - BURNOUT: wellbeing_signal(user, BURNOUT, 오늘 KST) 저장. 같은 날 이미 있으면 무시한다.
     *   최근 14일(오늘 포함) 중 BURNOUT 기록 날짜가 3일 이상이고
     *   users.last_burnout_notice_at이 NULL이거나 14일 이전이면 true를 반환하고
     *   last_burnout_notice_at을 now()로 갱신한다.
     * - CRISIS: wellbeing_signal(user, CRISIS, 오늘 KST) 저장(같은 날 중복 무시). 누적 판정에는 넣지 않고 항상 false.
     *
     * @param userId  주 보호자 users.user_id
     * @param diaryId 신호가 나온 일기. 일기가 삭제되면 NULL이 될 수 있다.
     * @param type    BURNOUT 또는 CRISIS
     * @return DIARY-02 응답의 burnoutNotice 값
     */
    public boolean record(Long userId, Long diaryId, SignalType type) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, JacksonConfig.KST);

        saveSignal(userId, diaryId, type, today, now);

        if (type != SignalType.BURNOUT) {
            return false;
        }
        if (countBurnoutDays(userId, today) < NOTICE_THRESHOLD_DAYS) {
            return false;
        }
        return markNoticed(userId, now);
    }

    /**
     * 같은 날 중복(UQ user_id, signal_type, detected_date)은 예외로 잡지 않고 DB에서 무시한다.
     * 이 메서드는 DIARY-02 Tx2 안에서 불리므로, 제약 위반 예외가 나면 PostgreSQL이 트랜잭션 전체를
     * 실패 상태로 만들어 분석 결과 저장까지 롤백된다(#62 리뷰).
     */
    private void saveSignal(Long userId, Long diaryId, SignalType type, LocalDate today, Instant now) {
        jdbc.sql("""
                INSERT INTO wellbeing_signal (user_id, diary_id, signal_type, detected_date, created_at)
                VALUES (:u, :d, :t, :date, :now)
                ON CONFLICT (user_id, signal_type, detected_date) DO NOTHING""")
            .param("u", userId)
            .param("d", diaryId)
            .param("t", type.name())
            .param("date", today)
            .param("now", Timestamp.from(now))
            .update();
    }

    /** 최근 14일(오늘 포함, 오늘-13일 ~ 오늘) 중 BURNOUT이 기록된 날짜 수. 하루 1행이 UQ로 보장된다. */
    private int countBurnoutDays(Long userId, LocalDate today) {
        return jdbc.sql("""
                SELECT count(DISTINCT detected_date) FROM wellbeing_signal
                WHERE user_id = :u AND signal_type = :t
                  AND detected_date BETWEEN :from AND :to""")
            .param("u", userId)
            .param("t", SignalType.BURNOUT.name())
            .param("from", today.minusDays(WINDOW_DAYS - 1))
            .param("to", today)
            .query(Integer.class)
            .single();
    }

    /**
     * last_burnout_notice_at이 NULL이거나 14일 이전일 때만 now()로 갱신한다.
     * 조건을 UPDATE에 같이 넣어서, 동시에 두 요청이 와도 안내는 한 번만 true가 된다.
     */
    private boolean markNoticed(Long userId, Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(WINDOW_DAYS));
        int updated = jdbc.sql("""
                UPDATE users SET last_burnout_notice_at = :now
                WHERE user_id = :u
                  AND (last_burnout_notice_at IS NULL OR last_burnout_notice_at <= :cutoff)""")
            .param("now", Timestamp.from(now))
            .param("u", userId)
            .param("cutoff", Timestamp.from(cutoff))
            .update();
        return updated == 1;
    }
}
