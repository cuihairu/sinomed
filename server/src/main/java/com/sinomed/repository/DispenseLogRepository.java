package com.sinomed.repository;

import com.sinomed.entity.DispenseLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispenseLogRepository extends JpaRepository<DispenseLogEntity, Long> {

    List<DispenseLogEntity> findByPrescriptionIdOrderByIdDesc(Long prescriptionId);
}
