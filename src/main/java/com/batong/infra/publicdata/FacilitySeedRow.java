package com.batong.infra.publicdata;

import com.batong.global.enums.District;
import com.batong.global.enums.FacilitySource;
import java.math.BigDecimal;
import java.time.Instant;

/** 시드 CSV 한 줄을 타입에 맞게 바꾼 값. needs_check, note 열은 사람이 검수할 때만 쓰므로 읽지 않는다 */
public record FacilitySeedRow(
    FacilitySource source,
    String externalId,
    String name,
    String phone,
    String officialUrl,
    String facilityType,
    District district,
    String address,
    BigDecimal lat,
    BigDecimal lng,
    String services,
    Instant fetchedAt) {
}
