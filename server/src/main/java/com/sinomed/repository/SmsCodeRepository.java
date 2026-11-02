package com.sinomed.repository;

import com.sinomed.entity.SmsCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface SmsCodeRepository extends JpaRepository<SmsCodeEntity, Long> {

    /** 取该手机号最近一条发码记录（校验时比对 code/过期/一次性） */
    Optional<SmsCodeEntity> findFirstByPhoneOrderByIdDesc(String phone);

    /**
     * 原子递增错试计数并在达上限时同步烧码（置 used=1）：并发错试不丢增量、不绕过上限。
     * 单条 UPDATE 内以 coalesce(attempts,0)+1 决定是否达上限，读写自洽（读-改-写无竞态）。
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "update sms_codes set attempts = coalesce(attempts, 0) + 1, "
            + "used = case when coalesce(attempts, 0) + 1 >= :max then 1 else used end "
            + "where id = :id", nativeQuery = true)
    int incrementAttempts(@Param("id") Long id, @Param("max") int max);
}
