package com.batong.support.service;

import com.batong.global.enums.ProgramType;
import com.batong.resource.domain.SupportProgram;
import com.batong.resource.repository.SupportProgramRepository;
import com.batong.support.dto.SupportDtos.BurnoutGuideResponse;
import com.batong.support.dto.SupportDtos.Contact;
import com.batong.support.dto.SupportDtos.CrisisGuideResponse;
import com.batong.support.dto.SupportDtos.FollowUp;
import com.batong.support.dto.SupportDtos.ProgramSummary;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소진 지원 안내(5.4)와 위기 안전 안내(5.5).
 * 문구와 연락처는 LLM이 만들지 않는 코드 상수다(AI 명세 5장, 서버 응답 문구 확정안).
 * 제도 이름과 id만 support_program에서 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {

    static final List<String> BURNOUT_MESSAGE = List.of(
        "요즘 기록에 지치신 날이 자주 보여요.",
        "보호자가 쉴 수 있도록 돕는 제도가 있어요.");

    /** 소진 안내에 보여주는 제도와 순서 */
    static final List<ProgramType> BURNOUT_PROGRAM_ORDER = List.of(
        ProgramType.PARENT_COUNSELING, ProgramType.FAMILY_REST, ProgramType.EMERGENCY_CARE);

    static final List<String> CRISIS_MESSAGE = List.of(
        "지금 많이 힘드신 것 같아요.",
        "혼자 감당하지 않으셔도 됩니다. 지금 바로 이야기할 수 있는 곳이 있어요.");

    /** 연락처는 시연 전에 공식 출처로 다시 확인한다(AI 명세 5-1) */
    static final List<Contact> CRISIS_CONTACTS = List.of(
        new Contact("자살예방 상담전화", "109", "24시간", null),
        new Contact("정신건강 위기상담", "1577-0199", "24시간", null),
        new Contact("119", "119", null, "지금 누군가 다쳤거나 위험할 때"),
        new Contact("112", "112", null, "지금 누군가 다쳤거나 위험할 때"));

    static final ProgramType CRISIS_FOLLOW_UP = ProgramType.PARENT_COUNSELING;

    private final SupportProgramRepository programRepository;

    /** 등록되지 않은 제도는 건너뛴다. 안내 문구는 제도가 없어도 나간다 */
    public BurnoutGuideResponse getBurnoutGuide() {
        List<ProgramSummary> programs = programRepository.findByProgramTypeIn(BURNOUT_PROGRAM_ORDER).stream()
            .sorted(Comparator.comparingInt(p -> BURNOUT_PROGRAM_ORDER.indexOf(p.getProgramType())))
            .map(p -> new ProgramSummary(p.getId(), p.getName(), p.getProgramType()))
            .toList();
        return new BurnoutGuideResponse(BURNOUT_MESSAGE, programs);
    }

    /**
     * 위기 때 가장 먼저 보이는 화면이라 연락처와 문구는 DB 없이도 나간다.
     * 부모상담 제도가 없으면 followUp만 null로 둔다.
     */
    public CrisisGuideResponse getCrisisGuide() {
        FollowUp followUp = programRepository.findByProgramTypeIn(List.of(CRISIS_FOLLOW_UP)).stream()
            .findFirst()
            .map(p -> new FollowUp(p.getId(), p.getName()))
            .orElse(null);
        return new CrisisGuideResponse(CRISIS_MESSAGE, CRISIS_CONTACTS, followUp);
    }
}
