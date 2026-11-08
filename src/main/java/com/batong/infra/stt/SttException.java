package com.batong.infra.stt;

/**
 * STT 호출 실패. 둘 다 API에서는 STT_FAILED(502)로 응답한다.
 * 메시지에 공급자 응답 본문을 넣지 않는다.
 */
public class SttException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Type {
        /** 20초 타임아웃 */
        TIMEOUT,
        /** 공급자 오류, 빈 결과 등 그 밖의 실패 */
        FAILED
    }

    private final Type type;

    public SttException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public SttException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
