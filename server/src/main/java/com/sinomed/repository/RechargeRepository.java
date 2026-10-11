package com.sinomed.repository;

import com.sinomed.entity.RechargeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface RechargeRepository extends JpaRepository<RechargeEntity, Long> {
    Page<RechargeEntity> findByUserId(Long userId, Pageable pageable);

    /** 储值余额 = 流水合计（充值为正、储值支付扣减为负），无流水为 0 */
    @Query("select coalesce(sum(r.money), 0) from RechargeEntity r where r.userId = :userId")
    long sumMoneyByUserId(@Param("userId") Long userId);

    /** 日对账取数：充值时间落在 [start, end) 的全部流水（含负数扣减与退款补回） */
    List<RechargeEntity> findByCreateTimeBetween(Date start, Date end);
}
