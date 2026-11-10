package com.batong.analysis.service;

import com.batong.analysis.service.AnalysisResponseParser.ParsedAnalysis;
import com.batong.analysis.service.AnalysisResponseParser.ParsedItem;
import com.batong.global.enums.ItemCategory;
import com.batong.global.enums.SafetyLevel;
import com.batong.global.enums.TimeSlot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisResponseParserTest {

    /** 파서 규칙 검증용 고정 문장(평가셋 30건 이전의 E01). 평가셋이 바뀌어도 이 테스트들은 그대로 둔다 */
    private static final String MASKED_E01 =
            "오후에 옆 건물 공사 소리가 나니까 대상자가 귀를 막더니 30분 넘게 소리를 질렀다. 차에 태워서 트로트 틀어주니까 조용해졌다.";
    /** 지금 평가셋 E01을 마스킹한 문장 */
    private static final String MASKED_EVAL_E01 =
            "출근길에 버스가 평소보다 늦게 왔다. 대상자가 정류장에서 계속 귀를 막고 소리를 질렀다. 버스가 오고 자리에 앉으니까 조금 조용해졌다.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalysisResponseParser parser = new AnalysisResponseParser(objectMapper);

    private static String item(String category, String label, String content, String timeSlot,
                               String durationMin, String episodeNo, String evidence) {
        return "{\"category\":" + category + ",\"label\":" + label + ",\"content\":" + content
                + ",\"timeSlot\":" + timeSlot + ",\"durationMin\":" + durationMin + ",\"episodeNo\":" + episodeNo
                + ",\"evidenceQuote\":" + evidence + "}";
    }

    private static String normal(String... items) {
        return "{\"safetyLevel\":\"NORMAL\",\"empathy\":\"오후 내내 긴장하셨겠어요.\",\"items\":["
                + String.join(",", items) + "],\"burnoutSignal\":false}";
    }

    private static String validTrigger() {
        return item("\"TRIGGER\"", "null", "\"공사 소리에 불안해함\"", "\"AFTERNOON\"", "null", "1",
                "\"오후에 옆 건물 공사 소리가 나니까\"");
    }

    @Test
    void E01_Mock_응답은_3건_모두_통과한다() throws Exception {
        String json = new String(new DefaultResourceLoader().getResource("classpath:ai-eval/mock/E01.json")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        ParsedAnalysis result = parser.parse(json, MASKED_EVAL_E01);

        assertThat(result.safetyLevel()).isEqualTo(SafetyLevel.NORMAL);
        assertThat(result.empathy()).isEqualTo("출근길 정류장에서 마음 졸이는 아침이었겠어요.");
        assertThat(result.items()).extracting(ParsedItem::category)
                .containsExactly(ItemCategory.TRIGGER, ItemCategory.BEHAVIOR, ItemCategory.RESPONSE);
        assertThat(result.items().get(1).label()).isEqualTo("소리 지름");
        // 새 E01은 지속 시간이 적혀 있지 않다
        assertThat(result.items().get(1).durationMin()).isNull();
        assertThat(result.droppedItemCount()).isZero();
    }

    /** 평가셋의 모든 Mock 응답은 마스킹된 원문 기준으로 하나도 버려지지 않아야 한다(시연 흐름이 실제 검증을 그대로 탄다) */
    @Test
    void 평가셋_Mock_응답은_검증에서_버려지는_항목이_없다() throws Exception {
        var loader = new DefaultResourceLoader();
        try (var reader = new BufferedReader(new InputStreamReader(
                loader.getResource("classpath:ai-eval/analyze_diary.jsonl").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode evalCase = objectMapper.readTree(line);
                var mock = loader.getResource("classpath:ai-eval/mock/" + evalCase.path("id").asText() + ".json");
                if (!mock.exists()) {
                    continue;
                }
                String masked = DiaryMasker.mask(evalCase.path("text").asText(), evalCase.path("name").asText());
                ParsedAnalysis result = parser.parse(new String(mock.getInputStream().readAllBytes(), StandardCharsets.UTF_8), masked);

                assertThat(result.safetyLevel().name()).as(evalCase.path("id").asText())
                        .isEqualTo(evalCase.at("/expected/safetyLevel").asText());
                assertThat(result.droppedItemCount()).as(evalCase.path("id").asText()).isZero();
            }
        }
    }

    @Test
    void CRISIS면_공감_문장과_항목을_쓰지_않는다() {
        String json = "{\"safetyLevel\":\"CRISIS\",\"empathy\":\"힘드셨겠어요\",\"items\":[" + validTrigger()
                + "],\"burnoutSignal\":true}";

        ParsedAnalysis result = parser.parse(json, MASKED_E01);

        assertThat(result.safetyLevel()).isEqualTo(SafetyLevel.CRISIS);
        assertThat(result.empathy()).isNull();
        assertThat(result.items()).isEmpty();
        assertThat(result.burnoutSignal()).isTrue();
    }

    @Test
    void JSON이_아니거나_safetyLevel을_알_수_없으면_재시도_대상_예외() {
        for (String json : new String[]{"설명: {}", "", "[]", "{\"items\":[]}", "{\"safetyLevel\":\"DANGER\"}",
                "{\"safetyLevel\":null}"}) {
            assertThatThrownBy(() -> parser.parse(json, MASKED_E01)).as(json)
                    .isInstanceOf(AnalysisParseException.class);
        }
        assertThatThrownBy(() -> parser.parse(null, MASKED_E01)).isInstanceOf(AnalysisParseException.class);
    }

    @Test
    void 예외_메시지에_응답_본문이_들어가지_않는다() {
        assertThatThrownBy(() -> parser.parse("{\"비밀 일기 본문", MASKED_E01))
                .isInstanceOf(AnalysisParseException.class)
                .hasMessageNotContaining("비밀")
                .hasNoCause();
    }

    @Test
    void 근거_문장은_공백과_문장부호를_빼고_마스킹된_원문과_비교한다() {
        String spaced = item("\"BEHAVIOR\"", "\"소리 지름\"", "\"소리를 지름\"", "null", "null", "null",
                "\"대상자가 귀를  막더니, 30분 넘게!\"");
        String withName = item("\"BEHAVIOR\"", "\"소리 지름\"", "\"소리를 지름\"", "null", "null", "null",
                "\"민지가 귀를 막더니\"");
        String invented = item("\"RESPONSE\"", "null", "\"안아줌\"", "null", "null", "null", "\"꼭 안아줬다\"");

        ParsedAnalysis result = parser.parse(normal(spaced, withName, invented), MASKED_E01);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).evidenceQuote()).isEqualTo("대상자가 귀를  막더니, 30분 넘게!");
        assertThat(result.droppedItemCount()).isEqualTo(2);
    }

    @Test
    void category나_timeSlot이_목록에_없으면_항목을_버린다() {
        String badCategory = item("\"GUARDIAN_MOOD\"", "null", "\"보호자가 지침\"", "null", "null", "null",
                "\"조용해졌다\"");
        String badTimeSlot = item("\"TRIGGER\"", "null", "\"공사 소리\"", "\"DAWN\"", "null", "null",
                "\"공사 소리가 나니까\"");

        ParsedAnalysis result = parser.parse(normal(badCategory, badTimeSlot, validTrigger()), MASKED_E01);

        assertThat(result.items()).extracting(ParsedItem::category).containsExactly(ItemCategory.TRIGGER);
        assertThat(result.items().get(0).timeSlot()).isEqualTo(TimeSlot.AFTERNOON);
        assertThat(result.droppedItemCount()).isEqualTo(2);
    }

    @Test
    void BEHAVIOR가_아니면_label은_null_목록에_없는_label은_기타_행동() {
        String triggerWithLabel = item("\"TRIGGER\"", "\"소리 지름\"", "\"공사 소리\"", "null", "null", "null",
                "\"공사 소리가 나니까\"");
        String unknownLabel = item("\"BEHAVIOR\"", "\"귀 막기\"", "\"귀를 막음\"", "null", "null", "null",
                "\"귀를 막더니\"");
        String missingLabel = item("\"BEHAVIOR\"", "null", "\"소리를 지름\"", "null", "null", "null",
                "\"소리를 질렀다\"");

        ParsedAnalysis result = parser.parse(normal(triggerWithLabel, unknownLabel, missingLabel), MASKED_E01);

        assertThat(result.items()).extracting(ParsedItem::label).containsExactly(null, "기타 행동", "기타 행동");
    }

    @Test
    void content가_비었거나_80자를_넘으면_자르지_않고_버린다() {
        String longContent = item("\"TRIGGER\"", "null", "\"" + "가".repeat(81) + "\"", "null", "null", "null",
                "\"공사 소리가 나니까\"");
        String blank = item("\"TRIGGER\"", "null", "\"  \"", "null", "null", "null", "\"공사 소리가 나니까\"");
        String exactly80 = item("\"TRIGGER\"", "null", "\"" + "가".repeat(80) + "\"", "null", "null", "null",
                "\"공사 소리가 나니까\"");

        ParsedAnalysis result = parser.parse(normal(longContent, blank, exactly80), MASKED_E01);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).content()).hasSize(80);
    }

    @Test
    void 공감_문장이_60자를_넘으면_null이고_항목은_남는다() {
        String json = "{\"safetyLevel\":\"NORMAL\",\"empathy\":\"" + "가".repeat(61) + "\",\"items\":["
                + validTrigger() + "],\"burnoutSignal\":false}";

        ParsedAnalysis result = parser.parse(json, MASKED_E01);

        assertThat(result.empathy()).isNull();
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void durationMin과_episodeNo는_범위_밖이면_값만_버린다() {
        String zero = item("\"BEHAVIOR\"", "\"소리 지름\"", "\"소리를 지름\"", "null", "0", "0", "\"소리를 질렀다\"");
        String tooLong = item("\"BEHAVIOR\"", "\"소리 지름\"", "\"소리를 지름\"", "null", "601", "\"1\"",
                "\"소리를 질렀다\"");
        String ok = item("\"BEHAVIOR\"", "\"소리 지름\"", "\"소리를 지름\"", "null", "600", "2", "\"소리를 질렀다\"");

        ParsedAnalysis result = parser.parse(normal(zero, tooLong, ok), MASKED_E01);

        assertThat(result.items()).hasSize(3);
        assertThat(result.items()).extracting(ParsedItem::durationMin).containsExactly(null, null, 600);
        assertThat(result.items()).extracting(ParsedItem::episodeNo).containsExactly(null, null, 2);
    }

    @Test
    void 항목은_15개까지만_남긴다() {
        String[] many = new String[16];
        java.util.Arrays.fill(many, validTrigger());

        ParsedAnalysis result = parser.parse(normal(many), MASKED_E01);

        assertThat(result.items()).hasSize(15);
        assertThat(result.droppedItemCount()).isEqualTo(1);
    }

    @Test
    void burnoutSignal이_없거나_boolean이_아니면_false() {
        assertThat(parser.parse("{\"safetyLevel\":\"NORMAL\",\"items\":[]}", MASKED_E01).burnoutSignal()).isFalse();
        assertThat(parser.parse("{\"safetyLevel\":\"NORMAL\",\"items\":[],\"burnoutSignal\":\"true\"}", MASKED_E01)
                .burnoutSignal()).isFalse();
        assertThat(parser.parse("{\"safetyLevel\":\"NORMAL\",\"items\":[],\"burnoutSignal\":true}", MASKED_E01)
                .burnoutSignal()).isTrue();
    }

    @Test
    void items가_없으면_빈_목록() {
        ParsedAnalysis result = parser.parse("{\"safetyLevel\":\"NORMAL\",\"empathy\":\"고마워요.\"}", MASKED_E01);

        assertThat(result.items()).isEmpty();
        assertThat(result.empathy()).isEqualTo("고마워요.");
    }
}
