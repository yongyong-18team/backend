package com.batong.infra.stt;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * STT-01이 받는 녹음 형식 (API 명세 STT-01, TRD 8-2).
 *
 * <p>브라우저 MediaRecorder는 {@code audio/webm;codecs=opus}처럼 코덱 파라미터를 붙여 보낸다.
 * 파라미터를 떼고 소문자로 바꾼 뒤 허용 목록과 비교한다.
 */
public final class SttAudioFormat {

    /** Chrome·Android: audio/webm, Safari·iOS: audio/mp4, audio/m4a */
    public static final Set<String> SUPPORTED = Set.of("audio/webm", "audio/mp4", "audio/m4a");

    /** 10MB. application.yml multipart.max-file-size와 같은 값 */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private SttAudioFormat() {
    }

    /**
     * @return 허용 형식이면 정규화된 MIME 타입, 아니면 empty (→ STT_UNSUPPORTED_FORMAT)
     */
    public static Optional<String> normalize(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return Optional.empty();
        }
        String base = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return SUPPORTED.contains(base) ? Optional.of(base) : Optional.empty();
    }

    public static boolean isTooLarge(long sizeBytes) {
        return sizeBytes > MAX_BYTES;
    }
}
