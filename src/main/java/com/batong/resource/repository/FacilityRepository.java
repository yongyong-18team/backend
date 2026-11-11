package com.batong.resource.repository;

import com.batong.global.enums.FacilitySource;
import com.batong.resource.domain.Facility;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    Optional<Facility> findBySourceAndExternalId(FacilitySource source, String externalId);

    /** RES-02: 제도에 매핑된 시설 유형의 시설. 정렬은 서비스에서 한다 */
    List<Facility> findByFacilityTypeIn(Collection<String> facilityTypes);
}
