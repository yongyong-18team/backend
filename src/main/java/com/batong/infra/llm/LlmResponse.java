package com.batong.infra.llm;

/**
 * LLM 호출 한 번의 결과.
 *
 * @param content   모델이 돌려준 본문(JSON 문자열). 파싱·검증은 호출한 쪽(analysis)이 한다
 * @param modelName ai_analysis.model_name에 기록할 모델 이름. Mock이면 "mock"
 * @param latencyMs 호출에 걸린 시간(ms). 로그·평가용
 */
public record LlmResponse(String content, String modelName, long latencyMs) {

    /** 응답 본문은 로그에 남기지 않는다 (AI 명세 2-6). */
    @Override
    public String toString() {
        return "LlmResponse[modelName=" + modelName
                + ", contentLength=" + (content == null ? 0 : content.length())
                + ", latencyMs=" + latencyMs + "]";
    }
}
