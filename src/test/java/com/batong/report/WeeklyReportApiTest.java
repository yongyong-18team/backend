package com.batong.report;

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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REPORT-03: 승인 항목만 집계, 7일 추이, 일기 수와 행동 횟수 구분, keyChanges 기간, 원문·근거 미노출,
 * OWNER·CO 허용, 다른 대상자 차단. 로컬 프로필은 AI_MODE=mock이라 summaryLines는 항상 빈 배열이다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class WeeklyReportApiTest {

    private static final String RAW_TEXT = "비밀원문문장";
    private static final String EVIDENCE = "비밀근거문장";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;

    long subjectId;
    long otherSubjectId;
    String owner;
    String coManager;
    String stranger;
    String otherOwner;

    @BeforeEach
    void seed() {
        long ownerId = user("weekly_owner");
        long coId = user("weekly_co");
        long strangerId = user("weekly_stranger");
        long otherOwnerId = user("weekly_other_owner");
        subjectId = subject(ownerId);
        otherSubjectId = subject(otherOwnerId);
        long invitationId = jdbc.sql("""
                INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id""")
            .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
            .param("s", subjectId).param("c", coId).param("i", invitationId).update();
        owner = "Bearer " + jwt.issue(ownerId);
        coManager = "Bearer " + jwt.issue(coId);
        stranger = "Bearer " + jwt.issue(strangerId);
        otherOwner = "Bearer " + jwt.issue(otherOwnerId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'weekly\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
            .param("l", loginId).query(Long.class).single();
    }

    private long subject(long ownerId) {
        return jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '민지', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
            .param("o", ownerId).query(Long.class).single();
    }

    private long diary(long subject, LocalDate date) {
        return jdbc.sql("""
                INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at)
                VALUES (:s, :d, :c, 'TEXT', 'DONE', now(), now()) RETURNING diary_id""")
            .param("s", subject).param("d", date).param("c", RAW_TEXT).query(Long.class).single();
    }

    private void item(long diaryId, int itemNo, String category, String label, String content, String timeSlot,
                      String status) {
        jdbc.sql("""
                INSERT INTO extracted_item (diary_id, item_no, category, label, content, time_slot, evidence_quote, status)
                VALUES (:d, :n, :c, :l, :t, :ts, :e, :st)""")
            .param("d", diaryId).param("n", itemNo).param("c", category).param("l", label).param("t", content)
            .param("ts", timeSlot).param("e", EVIDENCE).param("st", status).update();
    }

    /** DIARY 출처 설명서 항목. createdAt은 KST 날짜와 시각으로 준다 */
    private void guideFromDiary(long subject, long diaryId, int itemNo, String section, String content,
                                String createdAtKst) {
        jdbc.sql("""
                INSERT INTO guide_entry (subject_id, section, content, source_type, src_diary_id, src_item_no,
                                         created_at, updated_at)
                VALUES (:s, :sec, :c, 'DIARY', :d, :n, CAST(:at AS timestamp) AT TIME ZONE 'Asia/Seoul',
                        CAST(:at AS timestamp) AT TIME ZONE 'Asia/Seoul')""")
            .param("s", subject).param("sec", section).param("c", content).param("d", diaryId).param("n", itemNo)
            .param("at", createdAtKst).update();
    }

    private String weekly(long subject) {
        return "/api/v1/subjects/" + subject + "/weekly-reports";
    }

    @Test
    void 승인_항목만_집계하고_일기_수와_행동_횟수를_구분하며_7일_추이를_모두_준다() throws Exception {
        long d1 = diary(subjectId, LocalDate.of(2026, 10, 28));
        item(d1, 1, "BEHAVIOR", "소리 지름", "소리를 지름", "AFTERNOON", "APPROVED");
        item(d1, 2, "BEHAVIOR", "소리 지름", "또 소리를 지름", "EVENING", "APPROVED");
        item(d1, 3, "BEHAVIOR", "물건 파손", "제외한 행동", "AFTERNOON", "EXCLUDED");
        item(d1, 4, "TRIGGER", null, "공사 소리", null, "APPROVED");
        long d2 = diary(subjectId, LocalDate.of(2026, 11, 3));
        item(d2, 1, "BEHAVIOR", "소리 지름", "소리를 지름", "AFTERNOON", "APPROVED");
        item(d2, 2, "BEHAVIOR", "반복 행동", "대기 중인 행동", "NIGHT", "PENDING");
        long pendingOnly = diary(subjectId, LocalDate.of(2026, 11, 1));
        item(pendingOnly, 1, "BEHAVIOR", "소리 지름", "대기", "MORNING", "PENDING");
        diary(subjectId, LocalDate.of(2026, 10, 30)); // 항목 없는 일기
        long outOfRange = diary(subjectId, LocalDate.of(2026, 10, 27));
        item(outOfRange, 1, "BEHAVIOR", "소리 지름", "기간 밖", "AFTERNOON", "APPROVED");
        long otherDiary = diary(otherSubjectId, LocalDate.of(2026, 11, 2));
        item(otherDiary, 1, "BEHAVIOR", "소리 지름", "다른 대상자", "AFTERNOON", "APPROVED");

        mvc.perform(get(weekly(subjectId)).param("endDate", "2026-11-03").header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.periodStart").value("2026-10-28"))
            .andExpect(jsonPath("$.data.periodEnd").value("2026-11-03"))
            .andExpect(jsonPath("$.data.recordCount").value(2))
            .andExpect(jsonPath("$.data.behaviorCount").value(3))
            .andExpect(jsonPath("$.data.summaryLines", hasSize(0)))
            .andExpect(jsonPath("$.data.dailyTrend", hasSize(7)))
            .andExpect(jsonPath("$.data.dailyTrend[0].date").value("2026-10-28"))
            .andExpect(jsonPath("$.data.dailyTrend[0].recordCount").value(1))
            .andExpect(jsonPath("$.data.dailyTrend[0].behaviorCount").value(2))
            .andExpect(jsonPath("$.data.dailyTrend[1].recordCount").value(0))
            .andExpect(jsonPath("$.data.dailyTrend[6].date").value("2026-11-03"))
            .andExpect(jsonPath("$.data.dailyTrend[6].behaviorCount").value(1))
            .andExpect(jsonPath("$.data.repeatedBehaviors", hasSize(1)))
            .andExpect(jsonPath("$.data.repeatedBehaviors[0].label").value("소리 지름"))
            .andExpect(jsonPath("$.data.repeatedBehaviors[0].count").value(3))
            .andExpect(jsonPath("$.data.timePattern", hasSize(2)))
            .andExpect(jsonPath("$.data.timePattern[0].timeSlot").value("AFTERNOON"))
            .andExpect(jsonPath("$.data.timePattern[0].count").value(2))
            .andExpect(jsonPath("$.data.timePattern[1].timeSlot").value("EVENING"))
            .andExpect(content().string(not(containsString(RAW_TEXT))))
            .andExpect(content().string(not(containsString(EVIDENCE))));
    }

    @Test
    void keyChanges는_기간_안에_반영된_일기_출처_설명서_항목만_KST_기준으로_준다() throws Exception {
        long diaryId = diary(subjectId, LocalDate.of(2026, 10, 31));
        item(diaryId, 1, "PREFERENCE", null, "담요를 덮어주면 안정됨", null, "APPROVED");
        item(diaryId, 2, "PREFERENCE", null, "기간 시작 직전", null, "APPROVED");
        item(diaryId, 3, "PREFERENCE", null, "기간 끝 직후", null, "APPROVED");
        item(diaryId, 4, "PREFERENCE", null, "기간 끝 직전", null, "APPROVED");
        guideFromDiary(subjectId, diaryId, 1, "PREFERENCE", "담요를 덮어주면 안정됨", "2026-10-31 21:10:00");
        guideFromDiary(subjectId, diaryId, 2, "PREFERENCE", "기간 시작 직전", "2026-10-27 23:59:59");
        guideFromDiary(subjectId, diaryId, 3, "PREFERENCE", "기간 끝 직후", "2026-11-04 00:00:00");
        guideFromDiary(subjectId, diaryId, 4, "PREFERENCE", "기간 끝 직전", "2026-11-03 23:59:59");
        jdbc.sql("""
                INSERT INTO guide_entry (subject_id, section, content, source_type, created_at, updated_at)
                VALUES (:s, 'PROHIBITED', '직접 입력 항목', 'MANUAL', now(), now())""")
            .param("s", subjectId).update();

        mvc.perform(get(weekly(subjectId)).param("endDate", "2026-11-03").header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.recordCount").value(1))
            .andExpect(jsonPath("$.data.keyChanges", hasSize(2)))
            .andExpect(jsonPath("$.data.keyChanges[0].diaryId").value(diaryId))
            .andExpect(jsonPath("$.data.keyChanges[0].diaryDate").value("2026-10-31"))
            .andExpect(jsonPath("$.data.keyChanges[0].section").value("PREFERENCE"))
            .andExpect(jsonPath("$.data.keyChanges[0].content").value("담요를 덮어주면 안정됨"))
            .andExpect(jsonPath("$.data.keyChanges[1].content").value("기간 끝 직전"))
            .andExpect(content().string(not(containsString("직접 입력 항목"))))
            .andExpect(content().string(not(containsString("기간 시작 직전"))))
            .andExpect(content().string(not(containsString("기간 끝 직후"))));
    }

    @Test
    void 기록이_0건이면_건수_0과_빈_배열을_준다() throws Exception {
        long pendingOnly = diary(subjectId, LocalDate.of(2026, 11, 3));
        item(pendingOnly, 1, "BEHAVIOR", "소리 지름", "대기", "AFTERNOON", "PENDING");

        mvc.perform(get(weekly(subjectId)).param("endDate", "2026-11-03").header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.periodStart").value("2026-10-28"))
            .andExpect(jsonPath("$.data.periodEnd").value("2026-11-03"))
            .andExpect(jsonPath("$.data.recordCount").value(0))
            .andExpect(jsonPath("$.data.behaviorCount").value(0))
            .andExpect(jsonPath("$.data.summaryLines", hasSize(0)))
            .andExpect(jsonPath("$.data.dailyTrend", hasSize(0)))
            .andExpect(jsonPath("$.data.keyChanges", hasSize(0)))
            .andExpect(jsonPath("$.data.repeatedBehaviors", hasSize(0)))
            .andExpect(jsonPath("$.data.timePattern", hasSize(0)));
    }

    @Test
    void endDate를_생략하면_오늘_KST_포함_최근_7일이고_공동_관리자도_같은_응답을_받는다() throws Exception {
        LocalDate today = LocalDate.now(KST);
        long diaryId = diary(subjectId, today);
        item(diaryId, 1, "BEHAVIOR", "소리 지름", "소리를 지름", "AFTERNOON", "APPROVED");

        mvc.perform(get(weekly(subjectId)).header("Authorization", coManager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.periodStart").value(today.minusDays(6).toString()))
            .andExpect(jsonPath("$.data.periodEnd").value(today.toString()))
            .andExpect(jsonPath("$.data.recordCount").value(1))
            .andExpect(jsonPath("$.data.dailyTrend", hasSize(7)));
    }

    @Test
    void 날짜_형식이_틀리면_400이고_권한이_없으면_403_토큰이_없으면_401이다() throws Exception {
        mvc.perform(get(weekly(subjectId)).param("endDate", "11/03").header("Authorization", owner))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        for (String token : new String[]{stranger, otherOwner}) {
            mvc.perform(get(weekly(subjectId)).header("Authorization", token)).andExpect(status().isForbidden());
        }
        mvc.perform(get(weekly(subjectId))).andExpect(status().isUnauthorized());
    }
    @Test
    void 기록이_3건_이상이면_실제_프롬프트를_읽어_요약을_호출하고_mock이라_빈_배열을_준다() throws Exception {
        for (int day = 1; day <= 3; day++) {
            long diaryId = diary(subjectId, LocalDate.of(2026, 11, day));
            item(diaryId, 1, "BEHAVIOR", "소리 지름", "소리를 지름", "AFTERNOON", "APPROVED");
        }

        mvc.perform(get(weekly(subjectId)).param("endDate", "2026-11-03").header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.recordCount").value(3))
            .andExpect(jsonPath("$.data.behaviorCount").value(3))
            .andExpect(jsonPath("$.data.summaryLines", hasSize(0)));
    }
}
