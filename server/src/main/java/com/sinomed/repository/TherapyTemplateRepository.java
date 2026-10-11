package com.sinomed.repository;

import com.sinomed.entity.TherapyTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TherapyTemplateRepository extends JpaRepository<TherapyTemplateEntity, Long>, JpaSpecificationExecutor<TherapyTemplateEntity> {

    Optional<TherapyTemplateEntity> findByName(String name);

    List<TherapyTemplateEntity> findByEnabledOrderByNameAsc(Integer enabled);
}
