package com.batong.resource.repository;

import com.batong.global.enums.FacilitySource;
import com.batong.resource.domain.Facility;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    Optional<Facility> findBySourceAndExternalId(FacilitySource source, String externalId);
}
