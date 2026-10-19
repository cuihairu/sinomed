package com.sinomed.repository;

import com.sinomed.entity.WxMaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WxMaterialRepository extends JpaRepository<WxMaterialEntity, Long> {

    Optional<WxMaterialEntity> findByAccountIdAndMediaId(Long accountId, String mediaId);

    List<WxMaterialEntity> findAllByAccountId(Long accountId);
}
