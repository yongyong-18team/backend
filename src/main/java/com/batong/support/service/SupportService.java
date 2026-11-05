package com.batong.support.service;

import com.batong.global.enums.ProgramType;
import com.batong.support.dto.SupportDtos.BurnoutGuideResponse;
import com.batong.support.dto.SupportDtos.Contact;
import com.batong.support.dto.SupportDtos.CrisisGuideResponse;
import com.batong.support.dto.SupportDtos.FollowUp;
import com.batong.support.dto.SupportDtos.ProgramSummary;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SupportService {

    // 스텁: API 명세 SUP-01 예시 응답. 실제 구현에서 support_program 조회로 교체
    public BurnoutGuideResponse getBurnoutGuide() {
        return new BurnoutGuideResponse(
            List.of("요즘 기록에 지치신 날이 자주 보여요.", "보호자가 쉴 수 있도록 돕는 제도가 있어요."),
            List.of(
                new ProgramSummary(1101L, "발달장애인 부모상담 지원", ProgramType.PARENT_COUNSELING),
                new ProgramSummary(1102L, "가족휴식지원", ProgramType.FAMILY_REST),
                new ProgramSummary(1001L, "발달장애인 긴급돌봄", ProgramType.EMERGENCY_CARE)));
    }

    // 스텁: API 명세 SUP-02 예시 응답. 문구는 AI 명세 5-1과 맞춰야 함
    public CrisisGuideResponse getCrisisGuide() {
        return new CrisisGuideResponse(
            List.of("지금 많이 힘드신 것 같아요.", "혼자 감당하지 않으셔도 됩니다. 지금 바로 이야기할 수 있는 곳이 있어요."),
            List.of(
                new Contact("자살예방 상담전화", "109", "24시간", null),
                new Contact("정신건강 위기상담", "1577-0199", "24시간", null),
                new Contact("119", "119", null, "지금 누군가 다쳤거나 위험할 때"),
                new Contact("112", "112", null, "지금 누군가 다쳤거나 위험할 때")),
            new FollowUp(1101L, "발달장애인 부모상담 지원"));
    }
}
