package com.batong.resource.service;

import com.batong.global.enums.CareTerm;
import com.batong.global.enums.District;
import com.batong.global.enums.ResourceKind;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.resource.domain.Facility;
import com.batong.resource.domain.Resource;
import com.batong.resource.domain.SupportProgram;
import com.batong.resource.dto.ResourceDtos.Condition;
import com.batong.resource.dto.ResourceDtos.DetailResponse;
import com.batong.resource.dto.ResourceDtos.ResultItem;
import com.batong.resource.dto.ResourceDtos.SearchResponse;
import com.batong.resource.repository.FacilityRepository;
import com.batong.resource.repository.ResourceRepository;
import com.batong.resource.repository.SupportProgramRepository;
import com.batong.subject.domain.CareSubject;
import com.batong.subject.repository.CareSubjectRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RES-01·02. 저장하지 않고 DB에 적재된 제도·시설만 읽는다 (TRD 9-3) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceService {

    private static final String DISCLAIMER = "실제 신청 자격과 이용 가능 여부는 해당 기관에 확인이 필요합니다.";

    private final CareSubjectRepository subjectRepository;
    private final SupportProgramRepository programRepository;
    private final FacilityRepository facilityRepository;
    private final ResourceRepository resourceRepository;

    /**
     * 제도는 careTerm과 대상자 나이로 거르고, 시설은 남은 제도에 매핑된 시설 유형에서 가져온다.
     * 시설은 검색 지역(생략하면 대상자 등록 지역)을 먼저, 다음 다른 구 순서다.
     * 단기 검색에서 "확인된 시설만" 보여주는 규칙은 시드 매핑으로 지킨다. 단기 제도에는 시설 유형을
     * 연결하지 않아서 시설이 나오지 않고, 안내 문구는 화면이 보여준다.
     */
    public SearchResponse search(Long subjectId, CareTerm careTerm, LocalDate neededDate,
                                 District district, String dong, ResourceKind kind) {
        if (careTerm == CareTerm.BOTH) {
            throw new BusinessException(ErrorCode.RESOURCE_INVALID_CONDITION);
        }
        CareSubject subject = subjectRepository.findById(subjectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
        District searchDistrict = district != null ? district : subject.getDistrict();

        List<SupportProgram> programs = programRepository.findAllByOrderByIdAsc().stream()
            .filter(p -> ResourceSearchRules.matchesCareTerm(p.getCareTerm(), careTerm))
            .filter(p -> ResourceSearchRules.matchesAge(p.getTargetConditions(), subject.age()))
            .toList();

        List<ResultItem> results = new ArrayList<>();
        if (kind != ResourceKind.FACILITY) {
            programs.forEach(p -> results.add(toItem(p, careTerm)));
        }
        if (kind != ResourceKind.PROGRAM) {
            findFacilities(programs, searchDistrict).forEach(f -> results.add(toItem(f)));
        }
        return new SearchResponse(new Condition(careTerm, neededDate, searchDistrict, dong), DISCLAIMER, results);
    }

    public DetailResponse getDetail(Long resourceId) {
        Resource resource = resourceRepository.findById(resourceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
        if (resource instanceof Facility f) {
            return DetailResponse.builder()
                .resourceId(f.getId()).kind(ResourceKind.FACILITY).name(f.getName())
                .phone(f.getPhone()).officialUrl(f.getOfficialUrl())
                .facilityType(f.getFacilityType()).district(f.getDistrict()).address(f.getAddress())
                .lat(f.getLat()).lng(f.getLng()).services(f.getServices())
                .source(f.getSource()).fetchedAt(f.getFetchedAt())
                .build();
        }
        if (resource instanceof SupportProgram p) {
            return DetailResponse.builder()
                .resourceId(p.getId()).kind(ResourceKind.PROGRAM).name(p.getName())
                .phone(p.getPhone()).officialUrl(p.getOfficialUrl())
                .programType(p.getProgramType()).careTerm(p.getCareTerm()).summary(p.getSummary())
                .eligibilitySummary(p.getEligibilitySummary()).procedure(p.getProcedure())
                .sourceDoc(p.getSourceDoc()).docVersion(p.getDocVersion())
                .build();
        }
        throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
    }

    private List<Facility> findFacilities(List<SupportProgram> programs, District searchDistrict) {
        Set<String> types = new TreeSet<>();
        programs.forEach(p -> types.addAll(p.getFacilityTypes()));
        if (types.isEmpty()) {
            return List.of();
        }
        return facilityRepository.findByFacilityTypeIn(types).stream()
            .sorted(Comparator
                .comparing((Facility f) -> f.getDistrict() != searchDistrict)
                .thenComparing(Facility::getDistrict)
                .thenComparing(Facility::getName))
            .toList();
    }

    private ResultItem toItem(SupportProgram p, CareTerm requested) {
        return ResultItem.builder()
            .resourceId(p.getId()).kind(ResourceKind.PROGRAM).name(p.getName()).summary(p.getSummary())
            .reasonText(ResourceSearchRules.programReason(requested)).officialUrl(p.getOfficialUrl())
            .build();
    }

    private ResultItem toItem(Facility f) {
        return ResultItem.builder()
            .resourceId(f.getId()).kind(ResourceKind.FACILITY).name(f.getName()).district(f.getDistrict())
            .phone(f.getPhone())
            .reasonText(ResourceSearchRules.facilityReason(f.getDistrict(), f.getFacilityType()))
            .officialUrl(f.getOfficialUrl())
            .build();
    }
}
