package com.batong.subject.repository;

import com.batong.subject.domain.CareSubject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareSubjectRepository extends JpaRepository<CareSubject, Long> {
}
