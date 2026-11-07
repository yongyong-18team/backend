package com.batong.guide.repository;

import com.batong.global.enums.GuideSection;
import com.batong.guide.domain.GuideEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuideEntryRepository extends JpaRepository<GuideEntry, Long> {

    List<GuideEntry> findBySubjectIdOrderByIdAsc(Long subjectId);

    List<GuideEntry> findBySubjectIdAndSection(Long subjectId, GuideSection section);

    Optional<GuideEntry> findByIdAndSubjectId(Long id, Long subjectId);
}
