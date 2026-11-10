package com.batong.infra.stt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SttAudioFormatTest {

    @Test
    void 브라우저가_붙이는_코덱_파라미터를_뗀다() {
        assertThat(SttAudioFormat.normalize("audio/webm;codecs=opus")).contains("audio/webm");
        assertThat(SttAudioFormat.normalize("Audio/MP4")).contains("audio/mp4");
        assertThat(SttAudioFormat.normalize("audio/m4a")).contains("audio/m4a");
    }

    @Test
    void 허용하지_않는_형식은_empty() {
        assertThat(SttAudioFormat.normalize("audio/wav")).isEmpty();
        assertThat(SttAudioFormat.normalize("video/webm")).isEmpty();
        assertThat(SttAudioFormat.normalize("")).isEmpty();
        assertThat(SttAudioFormat.normalize(null)).isEmpty();
    }

    @Test
    void 크기_제한은_10MB() {
        assertThat(SttAudioFormat.isTooLarge(10L * 1024 * 1024)).isFalse();
        assertThat(SttAudioFormat.isTooLarge(10L * 1024 * 1024 + 1)).isTrue();
    }

    @Test
    void Mock은_평가셋_E01_문장을_돌려준다() {
        assertThat(new MockSttClient().transcribe(new byte[0], "audio/webm"))
                .startsWith("출근길에 버스가 평소보다 늦게 왔다");
    }
}
