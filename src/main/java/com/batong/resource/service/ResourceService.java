package com.batong.resource.service;

import com.batong.global.enums.CareTerm;
import com.batong.global.enums.District;
import com.batong.global.enums.FacilitySource;
import com.batong.global.enums.ProgramType;
import com.batong.global.enums.ResourceKind;
import com.batong.resource.dto.ResourceDtos.Condition;
import com.batong.resource.dto.ResourceDtos.DetailResponse;
import com.batong.resource.dto.ResourceDtos.ResultItem;
import com.batong.resource.dto.ResourceDtos.SearchResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResourceService {

    private static final String DISCLAIMER = "실제 신청 자격과 이용 가능 여부는 해당 기관에 확인이 필요합니다.";

    // 스텁: API 명세 RES-02 예시. condition은 요청 값을 그대로 돌려준다. 실제 구현은 TRD 9-3 규칙
    public SearchResponse search(Long subjectId, CareTerm careTerm, LocalDate neededDate,
                                 District district, String dong, ResourceKind kind) {
        return new SearchResponse(
            new Condition(careTerm, neededDate, district, dong),
            DISCLAIMER,
            List.of(
                ResultItem.builder()
                    .resourceId(1001L).kind(ResourceKind.PROGRAM).name("발달장애인 긴급돌봄")
                    .summary("보호자 입원 등으로 일시적 돌봄이 필요할 때 이용하는 제도")
                    .reasonText("단기 돌봄과 관련된 제도예요.").officialUrl("https://example.com")
                    .build(),
                ResultItem.builder()
                    .resourceId(2034L).kind(ResourceKind.FACILITY).name("○○ 단기거주시설")
                    .district(District.SUJI).phone("031-000-0000")
                    .reasonText("수지구에 있는 단기거주시설이에요.").officialUrl("https://example.com")
                    .build()));
    }

    // 스텁: API 명세 RES-01 예시. 2000번 이상이면 시설, 아니면 제도 예시를 준다
    public DetailResponse getDetail(Long resourceId) {
        if (resourceId >= 2000) {
            return DetailResponse.builder()
                .resourceId(2034L).kind(ResourceKind.FACILITY).name("○○ 단기거주시설")
                .phone("031-000-0000").officialUrl("https://example.com")
                .facilityType("단기거주시설").district(District.SUJI).address("경기도 용인시 수지구 ○○로 1")
                .lat(new BigDecimal("37.320000")).lng(new BigDecimal("127.090000"))
                .services("단기 돌봄").source(FacilitySource.YONGIN_API)
                .fetchedAt(Instant.parse("2026-11-03T03:00:00Z"))
                .build();
        }
        return DetailResponse.builder()
            .resourceId(1001L).kind(ResourceKind.PROGRAM).name("발달장애인 긴급돌봄")
            .phone("031-000-0000").officialUrl("https://example.com")
            .programType(ProgramType.EMERGENCY_CARE).careTerm(CareTerm.SHORT)
            .summary("보호자 입원 등으로 일시적 돌봄이 필요할 때 이용하는 제도")
            .eligibilitySummary("발달장애인 및 그 보호자")
            .procedure("제공기관에 전화 문의 후 신청")
            .sourceDoc("사업안내 (스텁)").docVersion("2026")
            .build();
    }
}
