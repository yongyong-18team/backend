package com.batong.diary.service;

import com.batong.diary.dto.DiaryDtos.SttResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 스텁: API 명세 예시 문장을 돌려준다. 형식·크기 검사와 SttClient 호출은 #24 */
@Service
public class SttService {

    public SttResponse transcribe(MultipartFile audio) {
        return new SttResponse("오후에 옆 건물 공사 소리가 나니까...");
    }
}
