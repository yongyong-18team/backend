package com.batong.share.repository;

import com.batong.share.domain.Share;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepository extends JpaRepository<Share, Long> {
}
