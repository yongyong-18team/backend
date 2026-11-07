package com.batong.infra.llm;

/**
 * LLM 공급자 어댑터 (TRD 8-6).
 *
 * <p>구현체는 {@code app.ai-mode}와 {@code LLM_PROVIDER}로 고른다. 구현체는 호출을 <b>딱 한 번</b> 한다.
 * 재시도(5xx·파싱 실패 시 1회), JSON 파싱, 스키마·근거 문장 검증, 전체 25초 상한은
 * 호출하는 쪽(analysis)이 책임진다. 그래야 공급자가 바뀌어도 정책이 한 곳에 남는다.
 *
 * <p>구현체는 요청·응답 본문을 로그에 남기지 않는다 (AI 명세 2-6).
 */
public interface LlmClient {

    /**
     * @return 모델이 돌려준 본문과 메타데이터
     * @throws LlmException 타임아웃, 5xx, 4xx, 네트워크 오류
     */
    LlmResponse complete(LlmRequest request);
}
