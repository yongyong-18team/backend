package com.batong.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.batong.global.config.JacksonConfig;
import com.batong.global.enums.CareTerm;
import com.batong.global.enums.District;
import com.batong.global.enums.FacilitySource;
import com.batong.global.enums.ProgramType;
import com.batong.global.enums.ResourceKind;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.resource.domain.Facility;
import com.batong.resource.domain.SupportProgram;
import com.batong.resource.dto.ResourceDtos.DetailResponse;
import com.batong.resource.dto.ResourceDtos.ResultItem;
import com.batong.resource.dto.ResourceDtos.SearchResponse;
import com.batong.resource.repository.FacilityRepository;
import com.batong.resource.repository.ResourceRepository;
import com.batong.resource.repository.SupportProgramRepository;
import com.batong.subject.domain.CareSubject;
import com.batong.subject.repository.CareSubjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    CareSubjectRepository subjectRepository;
    @Mock
    SupportProgramRepository programRepository;
    @Mock
    FacilityRepository facilityRepository;
    @Mock
    ResourceRepository resourceRepository;

    ResourceService service;

    SupportProgram emergency;
    SupportProgram dayActivity;
    SupportProgram activitySupport;
    SupportProgram familyRest;
    SupportProgram parentCounseling;

    @BeforeEach
    void setUp() {
        service = new ResourceService(subjectRepository, programRepository, facilityRepository, resourceRepository);
        // 시드와 같은 구성: 긴급돌봄·가족휴식(SHORT)은 시설 매핑 없음
        emergency = program(1001L, "발달장애인 긴급돌봄", ProgramType.EMERGENCY_CARE, CareTerm.SHORT,
            Map.of("minAge", 6, "maxAge", 65), Set.of());
        dayActivity = program(1002L, "발달장애인 주간활동", ProgramType.DAY_ACTIVITY, CareTerm.LONG,
            Map.of("minAge", 18), Set.of("주간이용센터"));
        activitySupport = program(1003L, "장애인 활동지원", ProgramType.ACTIVITY_SUPPORT, CareTerm.LONG,
            Map.of("minAge", 6), Set.of("자립생활지원센터(IL)", "장애인복지관"));
        familyRest = program(1004L, "발달장애인 가족휴식지원", ProgramType.FAMILY_REST, CareTerm.SHORT,
            Map.of(), Set.of());
        parentCounseling = program(1005L, "발달장애인 부모상담지원", ProgramType.PARENT_COUNSELING, CareTerm.LONG,
            Map.of(), Set.of("장애인복지관", "장애아재활치료시설"));
        lenient().when(programRepository.findAllByOrderByIdAsc())
            .thenReturn(List.of(emergency, dayActivity, activitySupport, familyRest, parentCounseling));
    }

    @Test
    void 단기_검색은_제도만_나오고_시설은_조회하지_않는다() {
        givenSubject(1L, 30, District.SUJI);

        SearchResponse res = service.search(1L, CareTerm.SHORT, null, null, null, null);

        assertThat(res.results()).extracting(ResultItem::name)
            .containsExactly("발달장애인 긴급돌봄", "발달장애인 가족휴식지원");
        assertThat(res.results()).allSatisfy(r -> {
            assertThat(r.kind()).isEqualTo(ResourceKind.PROGRAM);
            assertThat(r.reasonText()).isEqualTo("단기 돌봄과 관련된 제도예요.");
        });
        assertThat(res.disclaimer()).isEqualTo("실제 신청 자격과 이용 가능 여부는 해당 기관에 확인이 필요합니다.");
        verify(facilityRepository, never()).findByFacilityTypeIn(anyCollection());
    }

    @Test
    void 장기_검색은_제도_뒤에_관련_시설이_나오고_같은_구가_먼저다() {
        givenSubject(1L, 30, District.SUJI);
        when(facilityRepository.findByFacilityTypeIn(anyCollection())).thenReturn(List.of(
            facility(3003L, "처인 복지관", "장애인복지관", District.CHEOIN),
            facility(3002L, "수지 복지관", "장애인복지관", District.SUJI),
            facility(3001L, "기흥 주간센터", "주간이용센터", District.GIHEUNG),
            facility(3004L, "수지 주간센터", "주간이용센터", District.SUJI)));

        SearchResponse res = service.search(1L, CareTerm.LONG, null, null, null, null);

        List<ResultItem> programs = res.results().stream().filter(r -> r.kind() == ResourceKind.PROGRAM).toList();
        List<ResultItem> facilities = res.results().stream().filter(r -> r.kind() == ResourceKind.FACILITY).toList();
        assertThat(programs).extracting(ResultItem::name)
            .containsExactly("발달장애인 주간활동", "장애인 활동지원", "발달장애인 부모상담지원");
        assertThat(res.results().get(0).kind()).isEqualTo(ResourceKind.PROGRAM);
        assertThat(facilities).extracting(ResultItem::name)
            .containsExactly("수지 복지관", "수지 주간센터", "처인 복지관", "기흥 주간센터");
        assertThat(facilities.get(0).reasonText()).isEqualTo("수지구에 있는 장애인복지관이에요.");
    }

    @Test
    void 연령이_맞지_않는_제도는_빠진다() {
        givenSubject(1L, 5, District.SUJI);

        SearchResponse res = service.search(1L, CareTerm.SHORT, null, null, null, null);

        assertThat(res.results()).extracting(ResultItem::name).containsExactly("발달장애인 가족휴식지원");
    }

    @Test
    void careTerm이_없으면_연령이_맞는_제도_모두와_안내_문구를_준다() {
        givenSubject(1L, 30, District.SUJI);

        SearchResponse res = service.search(1L, null, null, null, null, ResourceKind.PROGRAM);

        assertThat(res.results()).hasSize(5);
        assertThat(res.results()).allSatisfy(r ->
            assertThat(r.reasonText()).isEqualTo("돌봄과 관련된 지원제도예요."));
    }

    @Test
    void kind가_FACILITY면_제도는_빼고_시설만_준다() {
        givenSubject(1L, 30, District.SUJI);
        when(facilityRepository.findByFacilityTypeIn(anyCollection()))
            .thenReturn(List.of(facility(3001L, "수지 주간센터", "주간이용센터", District.SUJI)));

        SearchResponse onlyFacility = service.search(1L, CareTerm.LONG, null, null, null, ResourceKind.FACILITY);
        assertThat(onlyFacility.results()).extracting(ResultItem::kind).containsOnly(ResourceKind.FACILITY);
    }

    @Test
    void kind가_PROGRAM이면_시설을_조회하지_않는다() {
        givenSubject(1L, 30, District.SUJI);

        SearchResponse onlyProgram = service.search(1L, CareTerm.LONG, null, null, null, ResourceKind.PROGRAM);

        assertThat(onlyProgram.results()).extracting(ResultItem::kind).containsOnly(ResourceKind.PROGRAM);
        verify(facilityRepository, never()).findByFacilityTypeIn(anyCollection());
    }

    @Test
    void 지역을_생략하면_대상자_등록_지역을_쓰고_조건에_담아_돌려준다() {
        givenSubject(1L, 30, District.GIHEUNG);

        SearchResponse res = service.search(1L, CareTerm.SHORT, LocalDate.of(2026, 11, 20), null, null, null);

        assertThat(res.condition().district()).isEqualTo(District.GIHEUNG);
        assertThat(res.condition().careTerm()).isEqualTo(CareTerm.SHORT);
        assertThat(res.condition().neededDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(res.condition().dong()).isNull();
    }

    @Test
    void careTerm이_BOTH면_조건_오류다() {
        assertThatThrownBy(() -> service.search(1L, CareTerm.BOTH, null, null, null, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_INVALID_CONDITION);
    }

    @Test
    void 어린_대상자는_연령_제한이_없는_제도만_나온다() {
        givenSubject(1L, 3, District.SUJI);

        SearchResponse res = service.search(1L, CareTerm.LONG, null, null, null, ResourceKind.PROGRAM);

        // 3세는 주간활동(18세 이상)·활동지원(6세 이상)이 아니고 연령 제한이 없는 부모상담지원만 맞는다
        assertThat(res.results()).extracting(ResultItem::name).containsExactly("발달장애인 부모상담지원");
    }

    @Test
    void 제도도_시설도_없으면_에러가_아니라_빈_배열이다() {
        givenSubject(1L, 30, District.SUJI);
        when(programRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        SearchResponse res = service.search(1L, CareTerm.LONG, null, null, null, null);

        assertThat(res.results()).isEmpty();
        verify(facilityRepository, never()).findByFacilityTypeIn(anyCollection());
    }

    @Test
    void 상세는_시설과_제도를_구분해_돌려준다() {
        Facility f = facility(2034L, "수지 주간센터", "주간이용센터", District.SUJI);
        when(resourceRepository.findById(2034L)).thenReturn(Optional.of(f));
        when(resourceRepository.findById(1001L)).thenReturn(Optional.of(emergency));

        DetailResponse facility = service.getDetail(2034L);
        assertThat(facility.kind()).isEqualTo(ResourceKind.FACILITY);
        assertThat(facility.facilityType()).isEqualTo("주간이용센터");
        assertThat(facility.programType()).isNull();

        DetailResponse program = service.getDetail(1001L);
        assertThat(program.kind()).isEqualTo(ResourceKind.PROGRAM);
        assertThat(program.programType()).isEqualTo(ProgramType.EMERGENCY_CARE);
        assertThat(program.careTerm()).isEqualTo(CareTerm.SHORT);
        assertThat(program.docVersion()).isEqualTo("2026");
        assertThat(program.facilityType()).isNull();
    }

    @Test
    void 없는_자원의_상세는_NOT_FOUND다() {
        when(resourceRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(9999L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_NOT_FOUND);
    }

    private void givenSubject(Long id, int age, District district) {
        int birthYear = LocalDate.now(JacksonConfig.KST).getYear() - age;
        CareSubject subject = CareSubject.create(10L, "테스트", birthYear, district, null);
        when(subjectRepository.findById(id)).thenReturn(Optional.of(subject));
    }

    private static SupportProgram program(Long id, String name, ProgramType type, CareTerm term,
                                          Map<String, Object> conditions, Set<String> facilityTypes) {
        SupportProgram p = SupportProgram.create(name, null, "https://example.com", type, term, "요약", "자격",
            conditions, "절차", "근거 문서", "2026", facilityTypes);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static Facility facility(Long id, String name, String type, District district) {
        Facility f = Facility.create(FacilitySource.MANUAL, "ext-" + id, name, "031-000-0000", null, type, district,
            "주소", null, null, null, Instant.parse("2026-11-03T03:00:00Z"));
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }
}
