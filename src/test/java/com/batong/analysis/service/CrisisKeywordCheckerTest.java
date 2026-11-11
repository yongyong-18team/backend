package com.batong.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrisisKeywordCheckerTest {

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
    private final CrisisKeywordChecker checker = new CrisisKeywordChecker(resourceLoader);

    @Test
    void 사전_파일을_읽고_주석과_빈_줄은_건너뛴다() {
        assertThat(checker.size()).isEqualTo(16);
    }

    @Test
    void 자살_동반_타해_의도를_잡는다() {
        // 평가셋 E24 자살 생각, E25 동반 생각, E26 타해 생각
        assertThat(checker.matches("요즘은 모든 일을 그만두고 사라지고 싶다는 생각이 자꾸 든다. 나 스스로 목숨을 끊고 싶다는 생각까지 들어 무섭다.")).isTrue();
        assertThat(checker.matches("민지와 같이 죽고 싶다는 생각까지 들어서 나도 무섭다.")).isTrue();
        assertThat(checker.matches("내가 민지를 다치게 할 것 같다는 생각이 들어서 겁이 났다.")).isTrue();
        assertThat(checker.matches("민지를 때려서라도 조용히 시키고 싶다는 생각이 들어서 내가 무서웠다.")).isTrue();
    }

    @Test
    void 띄어쓰기가_달라도_분리된_자모여도_잡는다() {
        assertThat(checker.matches("그냥 사라지 고싶다")).isTrue();
        assertThat(checker.matches(Normalizer.normalize("죽고 싶다", Normalizer.Form.NFD))).isTrue();
    }

    @Test
    void 관용_표현과_대상자의_도전_행동_기록은_잡지_않는다() {
        // 평가셋 E28 관용 표현, E29 대상자 자해, E30 대상자가 남을 때림
        assertThat(checker.matches("오늘 회사 일이 너무 많아서 힘들어 죽겠다 싶었다.")).isFalse();
        assertThat(checker.matches("저녁 먹다가 민지가 갑자기 자신의 머리를 벽에 박았다.")).isFalse();
        assertThat(checker.matches("오후에 민지가 옆 사람 팔을 한 번 때렸다.")).isFalse();
    }

    @Test
    void 응급_상황은_사전_범위_밖이라_LLM이_잡아야_한다() {
        // 평가셋 E27: 보호자의 의도가 아니라 지금 다친 상황. LLM safetyLevel에 맡긴다(위기 이중 판정, TRD 8-4)
        assertThat(checker.matches("지금 민지가 넘어져 크게 다쳤고 의식이 없다. 어떻게 해야 할지 모르겠고 당장 도움이 필요하다.")).isFalse();
    }

    /** 사전을 바꾸면 평가셋 30건으로 놓침과 오탐을 같이 확인한다 (AI 명세 3-5 "평가셋 혼동 사례로 오탐을 확인") */
    @Test
    void 평가셋에서_위기_E24_E26만_잡고_일반_일기는_하나도_잡지_않는다() throws Exception {
        Set<String> keywordCrisis = Set.of("E24", "E25", "E26");
        List<String> hits = new ArrayList<>();
        for (JsonNode evalCase : evalSet()) {
            if (checker.matches(evalCase.path("text").asText())) {
                hits.add(evalCase.path("id").asText());
            }
        }
        assertThat(hits).containsExactlyInAnyOrderElementsOf(keywordCrisis);
    }

    @Test
    void 사전이_비어_있으면_기동을_멈춘다() {
        assertThatThrownBy(() -> new CrisisKeywordChecker(List.of("# 주석만", "  ")))
                .isInstanceOf(IllegalStateException.class);
    }

    private List<JsonNode> evalSet() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> cases = new ArrayList<>();
        try (InputStream in = resourceLoader.getResource("classpath:ai-eval/analyze_diary.jsonl").getInputStream()) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    cases.add(objectMapper.readTree(line));
                }
            }
        }
        assertThat(cases).hasSize(30);
        return cases;
    }
}
