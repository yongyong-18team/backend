package com.batong.resource.dto;

import com.batong.global.enums.CareTerm;
import com.batong.global.enums.District;
import com.batong.global.enums.FacilitySource;
import com.batong.global.enums.ProgramType;
import com.batong.global.enums.ResourceKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

public final class ResourceDtos {

    private ResourceDtos() {
    }

    // RES-02
    public record SearchResponse(Condition condition, String disclaimer, List<ResultItem> results) {
    }

    // 명세 예시에 "dong": null 이 그대로 나오므로 null도 내려준다
    public record Condition(CareTerm careTerm, LocalDate neededDate, District district, String dong) {
    }

    // 제도(PROGRAM)와 시설(FACILITY)이 한 목록에 섞여 나오고, 종류별로 없는 필드는 뺀다
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResultItem(Long resourceId, ResourceKind kind, String name, String summary,
                             District district, String phone, String reasonText, String officialUrl) {
    }

    // RES-01 (시설/제도 모양이 달라서 없는 필드는 뺀다)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DetailResponse(Long resourceId, ResourceKind kind, String name, String phone, String officialUrl,
                                 // 시설
                                 String facilityType, District district, String address, BigDecimal lat,
                                 BigDecimal lng, String services, FacilitySource source, Instant fetchedAt,
                                 // 제도
                                 ProgramType programType, CareTerm careTerm, String summary,
                                 String eligibilitySummary, String procedure, String sourceDoc, String docVersion) {
    }
}
