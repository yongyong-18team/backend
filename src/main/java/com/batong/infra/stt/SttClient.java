package com.batong.infra.stt;

/**
 * STT 공급자 어댑터 (TRD 8-2).
 *
 * <p>음성은 메모리(byte[])로만 받고 디스크·DB에 쓰지 않는다. 형식 변환은 하지 않는다.
 * 형식·크기 검사는 호출 전에 컨트롤러가 한다({@link SttAudioFormat}).
 * 타임아웃 20초, 재시도 없음.
 */
public interface SttClient {

    /**
     * @param audio       녹음 파일 바이트
     * @param contentType {@link SttAudioFormat#normalize(String)}를 거친 값 (audio/webm, audio/mp4, audio/m4a)
     * @return 변환된 텍스트
     * @throws SttException 타임아웃, 공급자 오류
     */
    String transcribe(byte[] audio, String contentType);
}
