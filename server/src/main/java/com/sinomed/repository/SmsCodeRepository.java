package com.sinomed.repository;

import com.sinomed.entity.SmsCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SmsCodeRepository extends JpaRepository<SmsCodeEntity, Long> {

    /** 取该手机号最近一条发码记录（校验时比对 code/过期/一次性） */
    Optional<SmsCodeEntity> findFirstByPhoneOrderByIdDesc(String phone);
}
