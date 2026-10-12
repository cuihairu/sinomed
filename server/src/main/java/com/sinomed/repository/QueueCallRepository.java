package com.sinomed.repository;

import com.sinomed.entity.QueueCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueueCallRepository extends JpaRepository<QueueCallEntity, Long> {
    /**
     * 平板游标拉取：since 之后、且屏 id 为空（全部屏）或等于本屏的记录，按 id 升序。
     */
    List<QueueCallEntity> findByIdGreaterThanAndScreenIdIsNullOrderByIdAsc(Long since);

    List<QueueCallEntity> findByIdGreaterThanAndScreenIdOrderByIdAsc(Long since, Long screenId);

    List<QueueCallEntity> findTop20ByOrderByIdDesc();

    /** 大于给定 id 的叫号（id 升序，测试与审计用） */
    List<QueueCallEntity> findByIdGreaterThanOrderByIdAsc(Long id);
}
