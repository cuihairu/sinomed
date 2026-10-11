package com.sinomed.repository;

import com.sinomed.entity.RefundLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface RefundLogRepository extends JpaRepository<RefundLogEntity, Long> {

    /** 一笔结算只能退一次（唯一索引兜底） */
    Optional<RefundLogEntity> findBySettlementId(Long settlementId);

    /** 结算列表批量联「已退款」标记 */
    List<RefundLogEntity> findBySettlementIdIn(Collection<Long> settlementIds);

    /** 日对账取数：退款时间落在 [start, end) 的全部退款流水 */
    List<RefundLogEntity> findByCreateTimeBetween(Date start, Date end);
}
