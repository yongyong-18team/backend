package com.batong.infra.stt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI_MODE=mock(기본값)일 때 쓰는 SttClient. 고정 문장을 돌려준다 (TRD 8-2).
 *
 * <p>고정 문장은 평가셋 E01과 같다. mock 모드에서 녹음 → 저장 → 분석까지 이어 가면
 * MockLlmClient가 E01의 저장된 응답(TRIGGER·BEHAVIOR·RESPONSE 3건)을 돌려주므로 시연 흐름이 끊기지 않는다.
 * 실제 대상자 이름이 "민지"가 아니면 마스킹 결과가 달라지지만, Mock 키는 원문 기준이라 그대로 찾는다.
 */
@Component
@ConditionalOnProperty(name = "app.ai-mode", havingValue = "mock", matchIfMissing = true)
public class MockSttClient implements SttClient {

    static final String FIXED_TEXT =
            "출근길에 버스가 평소보다 늦게 왔다. 민지가 정류장에서 계속 귀를 막고 소리를 질렀다. "
                    + "버스가 오고 자리에 앉으니까 조금 조용해졌다.";

    @Override
    public String transcribe(byte[] audio, String contentType) {
        return FIXED_TEXT;
    }
}
