package com.sinomed.repository;

import com.sinomed.entity.WxAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WxAccountRepository extends JpaRepository<WxAccountEntity, Long> {
}
