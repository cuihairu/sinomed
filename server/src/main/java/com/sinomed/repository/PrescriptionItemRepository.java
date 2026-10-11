package com.sinomed.repository;

import com.sinomed.entity.PrescriptionItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItemEntity, Long> {
    List<PrescriptionItemEntity> findByPrescriptionIdOrderBySortAsc(Long prescriptionId);

    List<PrescriptionItemEntity> findByPrescriptionIdInOrderBySortAsc(List<Long> prescriptionIds);

    long countByPrescriptionId(Long prescriptionId);

    void deleteByPrescriptionId(Long prescriptionId);
}
