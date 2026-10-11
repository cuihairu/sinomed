package com.sinomed.repository;

import com.sinomed.entity.PrescriptionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PrescriptionRepository extends JpaRepository<PrescriptionEntity, Long>,
        JpaSpecificationExecutor<PrescriptionEntity> {
    Page<PrescriptionEntity> findByCustomerId(Long customerId, Pageable pageable);
}
