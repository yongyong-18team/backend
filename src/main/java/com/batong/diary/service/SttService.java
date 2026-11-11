package com.batong.diary.service;

import com.batong.diary.dto.DiaryDtos.SttResponse;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.infra.stt.SttAudioFormat;
import com.batong.infra.stt.SttClient;
import com.batong.infra.stt.SttException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * STT-01. 형식 → 크기 → SttClient 순서로 처리한다. 음성은 메모리에서만 다루고 저장하지 않으며,
 * 변환된 텍스트도 저장하지 않고 돌려주기만 한다(보호자가 확인·수정 후 DIARY-01로 저장).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SttService {

    private final SttClient sttClient;

    public SttResponse transcribe(MultipartFile audio) {
        // 빈 파일도 "녹음 파일을 읽을 수 없어요"가 맞다
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ErrorCode.STT_UNSUPPORTED_FORMAT);
        }
        String contentType = SttAudioFormat.normalize(audio.getContentType())
                .orElseThrow(() -> new BusinessException(ErrorCode.STT_UNSUPPORTED_FORMAT));
        if (SttAudioFormat.isTooLarge(audio.getSize())) {
            throw new BusinessException(ErrorCode.STT_FILE_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STT_UNSUPPORTED_FORMAT);
        }
        try {
            return new SttResponse(sttClient.transcribe(bytes, contentType));
        } catch (SttException e) {
            // 공급자 응답 본문은 메시지에 없다. 종류만 남긴다
            log.warn("STT-01 failed type={} error={}", e.getType(), e.getMessage());
            throw new BusinessException(ErrorCode.STT_FAILED);
        }
    }
}
