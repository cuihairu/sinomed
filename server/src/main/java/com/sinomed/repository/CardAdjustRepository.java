package com.sinomed.repository;

import com.sinomed.entity.CardAdjustEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardAdjustRepository extends JpaRepository<CardAdjustEntity, Long> {

    /** 调整流水（新记录在前） */
    List<CardAdjustEntity> findByCardIdOrderByIdDesc(Long cardId);
}
