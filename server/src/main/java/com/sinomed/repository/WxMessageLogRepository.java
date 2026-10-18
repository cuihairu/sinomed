package com.sinomed.repository;

import com.sinomed.entity.WxMessageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WxMessageLogRepository extends JpaRepository<WxMessageLogEntity, Long> {

    boolean existsByDedupeKey(String dedupeKey);
}
