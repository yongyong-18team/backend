package com.batong.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.batong.global.enums.CareTerm;
import com.batong.global.enums.ProgramType;
import com.batong.resource.domain.SupportProgram;
import com.batong.resource.repository.SupportProgramRepository;
import com.batong.support.dto.SupportDtos.BurnoutGuideResponse;
import com.batong.support.dto.SupportDtos.Contact;
import com.batong.support.dto.SupportDtos.CrisisGuideResponse;
import com.batong.support.dto.SupportDtos.ProgramSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock
    SupportProgramRepository programRepository;

    SupportService service;

    SupportProgram emergency;
    SupportProgram familyRest;
    SupportProgram parentCounseling;

    @BeforeEach
    void setUp() {
        service = new SupportService(programRepository);
        emergency = program(1L, "발달장애인 긴급돌봄", ProgramType.EMERGENCY_CARE, CareTerm.SHORT);
        familyRest = program(4L, "발달장애인 가족휴식지원", ProgramType.FAMILY_REST, CareTerm.SHORT);
        parentCounseling = program(5L, "발달장애인 부모상담지원", ProgramType.PARENT_COUNSELING, CareTerm.LONG);
    }

    @Test
    void 소진_안내는_부모상담_가족휴식_긴급돌봄_순서로_제도를_준다() {
        // 저장소는 id 순서로 돌려줘도 응답은 정해진 순서를 따른다
        when(programRepository.findByProgramTypeIn(anyCollection()))
            .thenReturn(List.of(emergency, familyRest, parentCounseling));

        BurnoutGuideResponse res = service.getBurnoutGuide();

        assertThat(res.message()).containsExactly(
            "요즘 기록에 지치신 날이 자주 보여요.", "보호자가 쉴 수 있도록 돕는 제도가 있어요.");
        assertThat(res.programs()).extracting(ProgramSummary::programType)
            .containsExactly(ProgramType.PARENT_COUNSELING, ProgramType.FAMILY_REST, ProgramType.EMERGENCY_CARE);
        assertThat(res.programs()).extracting(ProgramSummary::resourceId).containsExactly(5L, 4L, 1L);
        assertThat(res.programs()).extracting(ProgramSummary::name)
            .containsExactly("발달장애인 부모상담지원", "발달장애인 가족휴식지원", "발달장애인 긴급돌봄");
    }

    @Test
    void 소진_안내는_등록되지_않은_제도를_건너뛴다() {
        when(programRepository.findByProgramTypeIn(anyCollection())).thenReturn(List.of(parentCounseling));

        BurnoutGuideResponse res = service.getBurnoutGuide();

        assertThat(res.programs()).extracting(ProgramSummary::programType)
            .containsExactly(ProgramType.PARENT_COUNSELING);
        assertThat(res.message()).hasSize(2);
    }

    @Test
    void 소진_안내는_제도가_없어도_문구를_준다() {
        when(programRepository.findByProgramTypeIn(anyCollection())).thenReturn(List.of());

        BurnoutGuideResponse res = service.getBurnoutGuide();

        assertThat(res.programs()).isEmpty();
        assertThat(res.message()).hasSize(2);
    }

    @Test
    void 소진_안내_문구에는_진단으로_들리는_단어가_없다() {
        // 문구정책: 소진·번아웃·우울은 화면에 쓰지 않는다
        assertThat(String.join(" ", SupportService.BURNOUT_MESSAGE))
            .doesNotContain("소진", "번아웃", "우울");
    }

    @Test
    void 위기_안내는_고정_문구와_연락처_4개와_부모상담을_준다() {
        when(programRepository.findByProgramTypeIn(anyCollection())).thenReturn(List.of(parentCounseling));

        CrisisGuideResponse res = service.getCrisisGuide();

        assertThat(res.message()).containsExactly(
            "지금 많이 힘드신 것 같아요.",
            "혼자 감당하지 않으셔도 됩니다. 지금 바로 이야기할 수 있는 곳이 있어요.");
        assertThat(res.contacts()).extracting(Contact::phone)
            .containsExactly("109", "1577-0199", "119", "112");
        assertThat(res.contacts().get(0).hours()).isEqualTo("24시간");
        assertThat(res.contacts().get(2).note()).isEqualTo("지금 누군가 다쳤거나 위험할 때");
        assertThat(res.followUp().resourceId()).isEqualTo(5L);
        assertThat(res.followUp().name()).isEqualTo("발달장애인 부모상담지원");
    }

    @Test
    void 위기_안내는_부모상담_제도가_없어도_문구와_연락처를_준다() {
        when(programRepository.findByProgramTypeIn(anyCollection())).thenReturn(List.of());

        CrisisGuideResponse res = service.getCrisisGuide();

        assertThat(res.followUp()).isNull();
        assertThat(res.contacts()).hasSize(4);
        assertThat(res.message()).hasSize(2);
    }

    private SupportProgram program(Long id, String name, ProgramType type, CareTerm term) {
        SupportProgram p = SupportProgram.create(name, null, "https://example.com", type, term, "요약", "자격",
            Map.of(), "절차", "출처", "2026", Set.of());
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
