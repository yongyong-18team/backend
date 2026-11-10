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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REPORT-01·02: 승인 항목만, 원문·근거 미노출, OWNER·CO 허용, 다른 대상자 차단, 기본 구간, 섹션·항목 순서 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class ReportApiTest {

    private static final String RAW_TEXT = "비밀원문문장";
    private static final String EVIDENCE = "비밀근거문장";

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
        long ownerId = user("report_owner");
        long coId = user("report_co");
        long strangerId = user("report_stranger");
        long otherOwnerId = user("report_other_owner");
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
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'report\\_%'").update();
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

    private void item(long diaryId, int itemNo, String category, String content, String timeSlot, Integer durationMin,
                      String status) {
        jdbc.sql("""
                INSERT INTO extracted_item (diary_id, item_no, category, content, time_slot, duration_min, evidence_quote, status)
                VALUES (:d, :n, :c, :t, :ts, :dm, :e, :st)""")
            .param("d", diaryId).param("n", itemNo).param("c", category).param("t", content)
            .param("ts", timeSlot).param("dm", durationMin).param("e", EVIDENCE).param("st", status).update();
    }

    private String list(long subject) {
        return "/api/v1/subjects/" + subject + "/daily-records";
    }

    @Test
    void REPORT_01은_승인_항목이_있는_일기만_날짜_내림차순으로_주고_summary는_승인_항목을_item_no_순으로_잇는다() throws Exception {
        long older = diary(subjectId, LocalDate.of(2026, 11, 1));
        item(older, 1, "TRIGGER", "공사 소리에 불안해함", "AFTERNOON", null, "APPROVED");
        item(older, 2, "BEHAVIOR", "30분 넘게 소리를 지름", "AFTERNOON", 30, "APPROVED");
        item(older, 3, "RESPONSE", "제외한 항목", null, null, "EXCLUDED");
        long newer = diary(subjectId, LocalDate.of(2026, 11, 3));
        item(newer, 1, "RESPONSE", "음악을 틀어주니 안정됨", null, null, "APPROVED");
        long pendingOnly = diary(subjectId, LocalDate.of(2026, 11, 2));
        item(pendingOnly, 1, "TRIGGER", "검토 대기 항목", null, null, "PENDING");
        diary(subjectId, LocalDate.of(2026, 11, 2)); // 항목 없는 일기

        mvc.perform(get(list(subjectId)).param("from", "2026-10-28").param("to", "2026-11-03")
                .header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.from").value("2026-10-28"))
            .andExpect(jsonPath("$.data.to").value("2026-11-03"))
            .andExpect(jsonPath("$.data.records", hasSize(2)))
            .andExpect(jsonPath("$.data.records[0].diaryId").value(newer))
            .andExpect(jsonPath("$.data.records[0].approvedCount").value(1))
            .andExpect(jsonPath("$.data.records[0].summary").value("음악을 틀어주니 안정됨"))
            .andExpect(jsonPath("$.data.records[1].diaryId").value(older))
            .andExpect(jsonPath("$.data.records[1].approvedCount").value(2))
            .andExpect(jsonPath("$.data.records[1].summary").value("공사 소리에 불안해함 · 30분 넘게 소리를 지름"))
            .andExpect(content().string(not(containsString(RAW_TEXT))))
            .andExpect(content().string(not(containsString(EVIDENCE))))
            .andExpect(content().string(not(containsString("검토 대기 항목"))))
            .andExpect(content().string(not(containsString("제외한 항목"))));
    }

    @Test
    void REPORT_01은_구간_밖_일기와_다른_대상자_일기를_빼고_공동_관리자도_같은_응답을_받는다() throws Exception {
        long inRange = diary(subjectId, LocalDate.of(2026, 11, 3));
        item(inRange, 1, "BEHAVIOR", "범위 안", null, null, "APPROVED");
        long outRange = diary(subjectId, LocalDate.of(2026, 10, 1));
        item(outRange, 1, "BEHAVIOR", "범위 밖", null, null, "APPROVED");
        long otherSubjectDiary = diary(otherSubjectId, LocalDate.of(2026, 11, 3));
        item(otherSubjectDiary, 1, "BEHAVIOR", "다른 대상자", null, null, "APPROVED");

        mvc.perform(get(list(subjectId)).param("from", "2026-10-28").param("to", "2026-11-03")
                .header("Authorization", coManager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records", hasSize(1)))
            .andExpect(jsonPath("$.data.records[0].diaryId").value(inRange))
            .andExpect(jsonPath("$.data.records[0].summary").value("범위 안"));
    }

    @Test
    void REPORT_01은_from_to를_생략하면_오늘_포함_최근_7일을_적용하고_응답에_그_구간을_준다() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        long inside = diary(subjectId, today.minusDays(6));
        item(inside, 1, "BEHAVIOR", "7일 안", null, null, "APPROVED");
        long outside = diary(subjectId, today.minusDays(7));
        item(outside, 1, "BEHAVIOR", "7일 밖", null, null, "APPROVED");

        mvc.perform(get(list(subjectId)).header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.from").value(today.minusDays(6).toString()))
            .andExpect(jsonPath("$.data.to").value(today.toString()))
            .andExpect(jsonPath("$.data.records", hasSize(1)))
            .andExpect(jsonPath("$.data.records[0].diaryId").value(inside));
    }

    @Test
    void REPORT_01은_to만_주면_to_포함_7일_from만_주면_오늘까지다() throws Exception {
        mvc.perform(get(list(subjectId)).param("to", "2026-11-03").header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.from").value("2026-10-28"))
            .andExpect(jsonPath("$.data.to").value("2026-11-03"));
        mvc.perform(get(list(subjectId)).param("from", "2026-11-01").header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.from").value("2026-11-01"))
            .andExpect(jsonPath("$.data.to").value(LocalDate.now(ZoneId.of("Asia/Seoul")).toString()));
    }

    @Test
    void REPORT_01은_from이_to보다_늦거나_날짜_형식이_틀리면_400이다() throws Exception {
        mvc.perform(get(list(subjectId)).param("from", "2026-11-03").param("to", "2026-11-01")
                .header("Authorization", owner))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
        mvc.perform(get(list(subjectId)).param("from", "11/03").header("Authorization", owner))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION"));
    }

    @Test
    void REPORT_01은_기록이_없으면_빈_목록이다() throws Exception {
        mvc.perform(get(list(subjectId)).param("from", "2026-10-28").param("to", "2026-11-03")
                .header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records", hasSize(0)));
    }

    @Test
    void REPORT_02는_승인_항목만_섹션_순서와_item_no_순서로_주고_원문_공감_근거는_넣지_않는다() throws Exception {
        long diaryId = diary(subjectId, LocalDate.of(2026, 11, 3));
        item(diaryId, 1, "PREFERENCE", "담요를 덮어주면 안정됨", null, null, "APPROVED");
        item(diaryId, 2, "TRIGGER", "공사 소리에 불안해함", "AFTERNOON", null, "APPROVED");
        item(diaryId, 3, "BEHAVIOR", "30분 넘게 소리를 지름", "AFTERNOON", 30, "APPROVED");
        item(diaryId, 4, "RESPONSE", "검토 대기 항목", null, null, "PENDING");
        item(diaryId, 5, "COMMUNICATION", "제외한 항목", null, null, "EXCLUDED");

        for (String token : new String[]{owner, coManager}) {
            mvc.perform(get(list(subjectId) + "/" + diaryId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diaryId").value(diaryId))
                .andExpect(jsonPath("$.data.diaryDate").value("2026-11-03"))
                .andExpect(jsonPath("$.data.sections", hasSize(2)))
                .andExpect(jsonPath("$.data.sections[0].section").value("BEHAVIOR_RESPONSE"))
                .andExpect(jsonPath("$.data.sections[0].items", hasSize(2)))
                .andExpect(jsonPath("$.data.sections[0].items[0].content").value("공사 소리에 불안해함"))
                .andExpect(jsonPath("$.data.sections[0].items[0].timeSlot").value("AFTERNOON"))
                .andExpect(jsonPath("$.data.sections[0].items[1].content").value("30분 넘게 소리를 지름"))
                .andExpect(jsonPath("$.data.sections[0].items[1].durationMin").value(30))
                .andExpect(jsonPath("$.data.sections[1].section").value("PREFERENCE"))
                .andExpect(jsonPath("$.data.sections[1].items[0].content").value("담요를 덮어주면 안정됨"))
                .andExpect(content().string(not(containsString(RAW_TEXT))))
                .andExpect(content().string(not(containsString(EVIDENCE))))
                .andExpect(content().string(not(containsString("empathy"))))
                .andExpect(content().string(not(containsString("검토 대기 항목"))))
                .andExpect(content().string(not(containsString("제외한 항목"))));
        }
    }

    @Test
    void REPORT_02는_없는_일기_다른_대상자_일기_승인_항목_없는_일기를_모두_404로_준다() throws Exception {
        long noApproved = diary(subjectId, LocalDate.of(2026, 11, 3));
        item(noApproved, 1, "BEHAVIOR", "대기", null, null, "PENDING");
        long otherDiary = diary(otherSubjectId, LocalDate.of(2026, 11, 3));
        item(otherDiary, 1, "BEHAVIOR", "다른 대상자", null, null, "APPROVED");

        for (long id : new long[]{noApproved, otherDiary, 999999L}) {
            mvc.perform(get(list(subjectId) + "/" + id).header("Authorization", owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
        }
    }

    @Test
    void 대상자와_무관한_사용자와_다른_대상자의_보호자는_403이고_토큰이_없으면_401이다() throws Exception {
        long diaryId = diary(subjectId, LocalDate.of(2026, 11, 3));
        item(diaryId, 1, "BEHAVIOR", "항목", null, null, "APPROVED");

        for (String token : new String[]{stranger, otherOwner}) {
            mvc.perform(get(list(subjectId)).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(list(subjectId) + "/" + diaryId).header("Authorization", token))
                .andExpect(status().isForbidden());
        }
        mvc.perform(get(list(subjectId))).andExpect(status().isUnauthorized());
    }
}
