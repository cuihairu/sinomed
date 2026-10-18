package com.sinomed.repository;

import com.sinomed.entity.WxFanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WxFanRepository extends JpaRepository<WxFanEntity, Long> {

    Optional<WxFanEntity> findByAccountIdAndOpenid(Long accountId, String openid);
}
