package com.sinomed.repository;

import com.sinomed.entity.WxMaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface WxMaterialRepository extends JpaRepository<WxMaterialEntity, Long>,
        JpaSpecificationExecutor<WxMaterialEntity> {

    Optional<WxMaterialEntity> findByAccountIdAndMediaId(Long accountId, String mediaId);

    List<WxMaterialEntity> findAllByAccountId(Long accountId);
}
