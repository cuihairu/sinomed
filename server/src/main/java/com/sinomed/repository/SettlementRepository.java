package com.sinomed.repository;

import com.sinomed.entity.SettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<SettlementEntity, Long> {
    /** 订单最近一张结算单（退费重收后一单多张，取新记录在前判断是否已收款） */
    Optional<SettlementEntity> findFirstByOrderIdOrderByIdDesc(Long orderId);

    List<SettlementEntity> findByOrderIdIn(Collection<Long> orderIds);

    /** 月度收费报表取数：结算时间落在 [start, end) 的全部结算单 */
    List<SettlementEntity> findByCreateTimeBetween(Date start, Date end);
}
