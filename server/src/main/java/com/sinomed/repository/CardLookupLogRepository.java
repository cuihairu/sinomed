package com.sinomed.repository;

import com.sinomed.entity.CardLookupLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardLookupLogRepository extends JpaRepository<CardLookupLogEntity, Long> {

    /** 审计流水按时间倒序（后台查审计用） */
    List<CardLookupLogEntity> findByOrderByIdDesc();
}
