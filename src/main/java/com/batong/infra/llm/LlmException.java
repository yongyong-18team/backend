package com.batong.infra.llm;

/**
 * LLM 호출 실패.
 * 메시지에 공급자 응답 본문을 넣지 않는다 (TRD 13장 "외부 LLM·STT 예외 로그에 응답 본문 포함 금지").
 * JSON 파싱 실패는 이 예외가 아니라 analysis 쪽에서 판단한다.
 */
public class LlmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Type {
        /** 타임아웃. 재시도하지 않는다 (AI 명세 2-1) */
        TIMEOUT,
        /** 공급자 5xx. 1회 재시도 대상 */
        SERVER_ERROR,
        /** 공급자 4xx (키 오류, 요청 형식 오류, 한도 초과 등). 재시도해도 같으므로 재시도하지 않는다 */
        CLIENT_ERROR,
        /** 연결 실패 등 네트워크 오류 */
        NETWORK
    }

    private final Type type;

    public LlmException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public LlmException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    /** AI 명세 2-1의 "즉시 실패" 중 공급자 쪽 원인(5xx)만 재시도 대상이다. */
    public boolean isRetryable() {
        return type == Type.SERVER_ERROR;
    }
}
