package com.batong.diary.controller;

import com.batong.diary.dto.DiaryDtos.SttResponse;
import com.batong.diary.service.SttService;
import com.batong.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "일기·AI 분석")
@RestController
@RequestMapping("/api/v1/stt")
@RequiredArgsConstructor
public class SttController {

    private final SttService sttService;

    @Operation(summary = "STT-01 음성 → 텍스트",
            description = "multipart 필드 audio. audio/webm, audio/mp4, audio/m4a. 3분·10MB 이내. 음성은 저장하지 않는다")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SttResponse> transcribe(@RequestPart("audio") MultipartFile audio) {
        return ApiResponse.ok(sttService.transcribe(audio));
    }
}
