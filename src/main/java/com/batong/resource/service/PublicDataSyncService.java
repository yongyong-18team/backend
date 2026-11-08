package com.batong.resource.service;

import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.infra.publicdata.FacilitySeedReader;
import com.batong.infra.publicdata.FacilitySeedRow;
import com.batong.resource.domain.Facility;
import com.batong.resource.dto.PublicDataDtos.SyncResponse;
import com.batong.resource.repository.FacilityRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN-01 공공데이터 적재·갱신. 수기 정리한 시드 CSV를 읽어 (source, external_id) 기준으로 upsert한다.
 * 사용자 요청 중에는 외부 API를 부르지 않고 이 DB 값만 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataSyncService {

    static final String SEED_PATH = "publicdata/facility_seed_yongin.csv";

    private final FacilityRepository facilityRepository;

    /** 전체가 한 트랜잭션이라 중간에 실패하면 아무것도 바뀌지 않는다 */
    @Transactional
    public SyncResponse sync() {
        List<FacilitySeedRow> rows = readSeed();

        int created = 0;
        int updated = 0;
        for (FacilitySeedRow row : rows) {
            Facility existing = facilityRepository.findBySourceAndExternalId(row.source(), row.externalId()).orElse(null);
            if (existing == null) {
                facilityRepository.save(Facility.create(row.source(), row.externalId(), row.name(), row.phone(),
                    row.officialUrl(), row.facilityType(), row.district(), row.address(), row.lat(), row.lng(),
                    row.services(), row.fetchedAt()));
                created++;
            } else {
                existing.refresh(row.name(), row.phone(), row.officialUrl(), row.facilityType(), row.district(),
                    row.address(), row.lat(), row.lng(), row.services(), row.fetchedAt());
                updated++;
            }
        }
        log.info("공공데이터 적재 완료: total={}, created={}, updated={}", rows.size(), created, updated);
        return new SyncResponse(rows.size(), created, updated);
    }

    private List<FacilitySeedRow> readSeed() {
        try (Reader reader = new InputStreamReader(new ClassPathResource(SEED_PATH).getInputStream(), StandardCharsets.UTF_8)) {
            return FacilitySeedReader.read(reader);
        } catch (IOException | IllegalArgumentException e) {
            log.error("공공데이터 시드를 읽지 못했습니다: {}", e.getMessage());
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }
    }
}
