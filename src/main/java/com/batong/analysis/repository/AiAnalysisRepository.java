package com.batong.analysis.repository;

import com.batong.analysis.domain.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, AiAnalysis.Key> {

    Optional<AiAnalysis> findTopByDiaryIdOrderByAnalysisNoDesc(Long diaryId);
}
