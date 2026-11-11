package com.batong.analysis;

import com.batong.TestcontainersConfiguration;
import com.batong.global.enums.SignalType;
import com.batong.global.security.JwtProvider;
import com.batong.infra.llm.LlmClient;
import com.batong.infra.llm.LlmException;
import com.batong.infra.llm.LlmRequest;
import com.batong.infra.llm.LlmResponse;
import com.batong.infra.llm.MockLlmClient;
import com.batong.wellbeing.service.WellbeingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DIARY-02 (TRD 8-3, AI 명세 3-4·7장). 기본은 실제 MockLlmClient(평가셋 Mock 응답)를 그대로 쓰고,
 * 실패 상황만 LlmClient를 바꿔 끼운다. 한 컨텍스트로 돌리려고 테스트를 한 클래스에 모았다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class DiaryAnalyzeApiTest {

    private static final String E01 =
            "출근길에 버스가 평소보다 늦게 왔다. 민지가 정류장에서 계속 귀를 막고 소리를 질렀다. 버스가 오고 자리에 앉으니까 조금 조용해졌다.";
    private static final String E20 = "어제도 잠을 설쳤는데 아침부터 세탁기까지 고장 났다. 민지는 오후에 장난감 상자를 정리하며 잘 놀았다. 나는 밤에 몇 번씩 깨니까 출근해서도 머리가 멍하고 오늘은 정말 좀 쉬고 싶었다.";
    /** 위기 키워드("사라지고 싶")에 걸리는 사례 */
    private static final String E24 = "어젯밤에도 거의 못 잤다. 요즘은 모든 일을 그만두고 사라지고 싶다는 생각이 자꾸 든다. 나 스스로 목숨을 끊고 싶다는 생각까지 들어 무섭다.";
    /** 위기 키워드에는 없고 LLM(Mock)만 CRISIS로 보는 사례(응급 상황은 키워드 범위 밖) */
    private static final String E27 = "지금 민지가 넘어져 크게 다쳤고 의식이 없다. 어떻게 해야 할지 모르겠고 당장 도움이 필요하다.";

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtProvider jwt;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    LlmClient llmClient;
    @MockitoBean
    WellbeingService wellbeingService;

    long ownerId;
    long subjectId;
    String owner;
    String coManager;

    @BeforeEach
    void seed() {
        MockLlmClient realMock = new MockLlmClient(objectMapper, new DefaultResourceLoader());
        reset(llmClient, wellbeingService);
        when(llmClient.complete(any())).thenAnswer(inv -> realMock.complete(inv.getArgument(0)));

        ownerId = user("anl_owner");
        long coId = user("anl_co");
        subjectId = jdbc.sql("""
                INSERT INTO care_subject (owner_user_id, name, birth_year, district, created_at, updated_at)
                VALUES (:o, '민지', 2001, 'SUJI', now(), now()) RETURNING subject_id""")
                .param("o", ownerId).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO consent (user_id, subject_id, consent_type, terms_version, agreed_at)
                VALUES (:u, :s, 'AI_PROCESSING', '1.0', now())""")
                .param("u", ownerId).param("s", subjectId).update();
        long invitationId = jdbc.sql("""
                INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                VALUES (:s, :c, 'ACCEPTED', now()) RETURNING invitation_id""")
                .param("s", subjectId).param("c", coId).query(Long.class).single();
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :c, :i, now())")
                .param("s", subjectId).param("c", coId).param("i", invitationId).update();
        owner = "Bearer " + jwt.issue(ownerId);
        coManager = "Bearer " + jwt.issue(coId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM users WHERE login_id LIKE 'anl\\_%'").update();
    }

    private long user(String loginId) {
        return jdbc.sql("INSERT INTO users (login_id, password_hash, created_at, updated_at) VALUES (:l, 'x', now(), now()) RETURNING user_id")
                .param("l", loginId).query(Long.class).single();
    }

    private long diary(String content) {
        return jdbc.sql("""
                INSERT INTO diary (subject_id, diary_date, content, input_type, analysis_status, created_at, updated_at)
                VALUES (:s, DATE '2026-11-03', :c, 'TEXT', 'PENDING', now(), now()) RETURNING diary_id""")
                .param("s", subjectId).param("c", content).query(Long.class).single();
    }

    private ResultActions analyze(String token, long diaryId) throws Exception {
        return mvc.perform(post("/api/v1/subjects/" + subjectId + "/diaries/" + diaryId + "/analyze")
                .header("Authorization", token));
    }

    private String diaryStatus(long diaryId) {
        return jdbc.sql("SELECT analysis_status FROM diary WHERE diary_id = :d").param("d", diaryId)
                .query(String.class).single();
    }

    private List<Map<String, Object>> analyses(long diaryId) {
        return jdbc.sql("SELECT analysis_no, safety_level, empathy_text, raw_json::text AS raw_json, prompt_version, model_name "
                        + "FROM ai_analysis WHERE diary_id = :d ORDER BY analysis_no")
                .param("d", diaryId).query().listOfRows();
    }

    private List<Map<String, Object>> items(long diaryId) {
        return jdbc.sql("SELECT item_no, category, label, content, evidence_quote, status FROM extracted_item "
                        + "WHERE diary_id = :d ORDER BY item_no")
                .param("d", diaryId).query().listOfRows();
    }

    // ---- 정상 흐름 ------------------------------------------------------------

    @Test
    void 평가셋_문장은_REVIEW와_3개_항목이고_LLM에는_마스킹된_본문만_간다() throws Exception {
        long diaryId = diary(E01);

        analyze(owner, diaryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisNo").value(1))
                .andExpect(jsonPath("$.data.result").value("REVIEW"))
                .andExpect(jsonPath("$.data.empathyText").value("출근길 정류장에서 마음 졸이는 아침이었겠어요."))
                .andExpect(jsonPath("$.data.itemCount").value(3))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].itemNo").value(1))
                .andExpect(jsonPath("$.data.items[0].section").value("BEHAVIOR_RESPONSE"))
                .andExpect(jsonPath("$.data.items[1].label").value("소리 지름"))
                .andExpect(jsonPath("$.data.items[1].status").value("PENDING"))
                .andExpect(jsonPath("$.data.burnoutNotice").value(false));

        ArgumentCaptor<LlmRequest> sent = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).complete(sent.capture());
        assertThat(sent.getValue().userPrompt()).contains("대상자가 정류장에서").doesNotContain("민지");

        assertThat(diaryStatus(diaryId)).isEqualTo("DONE");
        Map<String, Object> analysis = analyses(diaryId).get(0);
        assertThat(analysis.get("safety_level")).isEqualTo("NORMAL");
        assertThat(analysis.get("prompt_version")).isEqualTo("v1");
        assertThat(analysis.get("model_name")).isEqualTo("mock");
        assertThat(analysis.get("raw_json")).isNotNull();
        assertThat(items(diaryId)).extracting(r -> r.get("status")).containsExactly("PENDING", "PENDING", "PENDING");
        verify(wellbeingService, never()).record(anyLong(), anyLong(), any());
    }

    @Test
    void 저장할_때_대상자를_원래_이름으로_되돌린다() throws Exception {
        long diaryId = diary("아침에 민지가 밥을 잘 먹었다. 오후엔 산책했다.");

        analyze(owner, diaryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].evidenceQuote").value("아침에 민지가 밥을 잘 먹었다"));

        assertThat(items(diaryId).get(0).get("evidence_quote")).isEqualTo("아침에 민지가 밥을 잘 먹었다");
        assertThat(items(diaryId).get(0).get("content")).isEqualTo("아침에 민지가 밥을 잘 먹었다");
    }

    @Test
    void 소진_신호가_있으면_BURNOUT을_기록하고_그_결과를_burnoutNotice로_준다() throws Exception {
        when(wellbeingService.record(anyLong(), anyLong(), eq(SignalType.BURNOUT))).thenReturn(true);
        long diaryId = diary(E20);

        analyze(owner, diaryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("REVIEW"))
                .andExpect(jsonPath("$.data.burnoutNotice").value(true));

        verify(wellbeingService).record(ownerId, diaryId, SignalType.BURNOUT);
    }

    // ---- 위기 -----------------------------------------------------------------

    @Test
    void 위기면_공감과_항목을_저장하지_않고_CRISIS만_기록한다() throws Exception {
        long diaryId = diary(E24);

        analyze(owner, diaryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("CRISIS"))
                .andExpect(jsonPath("$.data.empathyText").doesNotExist())
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.burnoutNotice").value(false));

        Map<String, Object> analysis = analyses(diaryId).get(0);
        assertThat(analysis.get("safety_level")).isEqualTo("CRISIS");
        assertThat(analysis.get("empathy_text")).isNull();
        assertThat(analysis.get("prompt_version")).isEqualTo("v1");
        assertThat(items(diaryId)).isEmpty();
        assertThat(diaryStatus(diaryId)).isEqualTo("DONE");
        // Mock 응답은 burnoutSignal=true지만 위기에는 BURNOUT을 기록하지 않는다(TRD 8-3)
        verify(wellbeingService).record(ownerId, diaryId, SignalType.CRISIS);
        verify(wellbeingService, never()).record(anyLong(), anyLong(), eq(SignalType.BURNOUT));
    }

    @Test
    void 키워드에_없어도_LLM이_CRISIS면_위기다() throws Exception {
        long diaryId = diary(E27);

        analyze(owner, diaryId).andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("CRISIS"));
    }

    @Test
    void LLM이_실패해도_키워드가_맞으면_keyword_only_CRISIS로_저장한다() throws Exception {
        doThrow(new LlmException(LlmException.Type.SERVER_ERROR, "5xx")).when(llmClient).complete(any());
        long diaryId = diary(E24);

        analyze(owner, diaryId).andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("CRISIS"));

        Map<String, Object> analysis = analyses(diaryId).get(0);
        assertThat(analysis.get("prompt_version")).isEqualTo("keyword-only");
        assertThat(analysis.get("raw_json")).isNull();
        assertThat(analysis.get("model_name")).isNull();
        verify(llmClient, times(2)).complete(any());
    }

    // ---- 실패와 재시도 ---------------------------------------------------------

    @Test
    void 타임아웃은_재시도하지_않고_FAILED와_AI_TIMEOUT이다() throws Exception {
        doThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout")).when(llmClient).complete(any());
        long diaryId = diary(E01);

        analyze(owner, diaryId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));

        verify(llmClient, times(1)).complete(any());
        assertThat(diaryStatus(diaryId)).isEqualTo("FAILED");
        assertThat(analyses(diaryId)).isEmpty();
    }

    @Test
    void 서버_오류는_한_번_재시도해서_성공하면_정상_응답() throws Exception {
        MockLlmClient realMock = new MockLlmClient(objectMapper, new DefaultResourceLoader());
        doThrow(new LlmException(LlmException.Type.SERVER_ERROR, "5xx"))
                .doAnswer(inv -> realMock.complete(inv.getArgument(0)))
                .when(llmClient).complete(any());
        long diaryId = diary(E01);

        analyze(owner, diaryId).andExpect(status().isOk()).andExpect(jsonPath("$.data.itemCount").value(3));

        verify(llmClient, times(2)).complete(any());
    }

    @Test
    void 파싱_실패가_두_번이면_FAILED와_AI_TIMEOUT이다() throws Exception {
        doReturn(new LlmResponse("설명부터 쓰는 응답", "mock", 1)).when(llmClient).complete(any());
        long diaryId = diary(E01);

        analyze(owner, diaryId).andExpect(status().isBadGateway());

        verify(llmClient, times(2)).complete(any());
        assertThat(diaryStatus(diaryId)).isEqualTo("FAILED");
    }

    @Test
    void 클라이언트_오류는_재시도하지_않는다() throws Exception {
        doThrow(new LlmException(LlmException.Type.CLIENT_ERROR, "429")).when(llmClient).complete(any());
        long diaryId = diary(E01);

        analyze(owner, diaryId).andExpect(status().isBadGateway());

        verify(llmClient, times(1)).complete(any());
    }

    @Test
    void 예상하지_못한_오류에도_PROCESSING에_묶이지_않는다() throws Exception {
        doThrow(new IllegalStateException("boom")).when(llmClient).complete(any());
        long diaryId = diary(E01);

        analyze(owner, diaryId).andExpect(status().isInternalServerError());

        assertThat(diaryStatus(diaryId)).isEqualTo("FAILED");
    }

    @Test
    void 실패한_일기는_다시_분석할_수_있다() throws Exception {
        doThrow(new LlmException(LlmException.Type.TIMEOUT, "timeout")).when(llmClient).complete(any());
        long diaryId = diary(E01);
        analyze(owner, diaryId).andExpect(status().isBadGateway());

        MockLlmClient realMock = new MockLlmClient(objectMapper, new DefaultResourceLoader());
        doAnswer(inv -> realMock.complete(inv.getArgument(0))).when(llmClient).complete(any());
        analyze(owner, diaryId).andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisNo").value(1));
    }

    // ---- 재분석 ---------------------------------------------------------------

    @Test
    void 재분석은_analysisNo를_올리고_PENDING만_교체하며_승인_항목은_남긴다() throws Exception {
        long diaryId = diary(E01);
        analyze(owner, diaryId).andExpect(status().isOk());
        jdbc.sql("UPDATE extracted_item SET status = 'APPROVED' WHERE diary_id = :d AND item_no = 1")
                .param("d", diaryId).update();

        analyze(owner, diaryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisNo").value(2))
                .andExpect(jsonPath("$.data.items[0].itemNo").value(4));

        assertThat(analyses(diaryId)).hasSize(2);
        assertThat(items(diaryId)).extracting(r -> ((Number) r.get("item_no")).intValue() + ":" + r.get("status"))
                .containsExactly("1:APPROVED", "4:PENDING", "5:PENDING", "6:PENDING");
    }

    // ---- 막는 경우 ------------------------------------------------------------

    @Test
    void AI_처리_동의가_없으면_403_AI_CONSENT_REQUIRED이고_상태는_그대로다() throws Exception {
        jdbc.sql("DELETE FROM consent WHERE subject_id = :s").param("s", subjectId).update();
        long diaryId = diary(E01);

        analyze(owner, diaryId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AI_CONSENT_REQUIRED"));

        assertThat(diaryStatus(diaryId)).isEqualTo("PENDING");
        verify(llmClient, never()).complete(any());
    }

    @Test
    void 분석_중이면_409_DIARY_ALREADY_ANALYZING() throws Exception {
        long diaryId = diary(E01);
        jdbc.sql("UPDATE diary SET analysis_status = 'PROCESSING' WHERE diary_id = :d").param("d", diaryId).update();

        analyze(owner, diaryId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DIARY_ALREADY_ANALYZING"));

        verify(llmClient, never()).complete(any());
    }

    @Test
    void 없거나_다른_대상자의_일기는_404() throws Exception {
        analyze(owner, 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
    }

    @Test
    void 공동_관리자는_403() throws Exception {
        long diaryId = diary(E01);

        analyze(coManager, diaryId).andExpect(status().isForbidden());

        assertThat(diaryStatus(diaryId)).isEqualTo("PENDING");
    }
}
