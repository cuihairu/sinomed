package com.sinomed.repository;

import com.sinomed.entity.WxMessageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WxMessageLogRepository extends JpaRepository<WxMessageLogEntity, Long>, JpaSpecificationExecutor<WxMessageLogEntity> {

    boolean existsByDedupeKey(String dedupeKey);
}
