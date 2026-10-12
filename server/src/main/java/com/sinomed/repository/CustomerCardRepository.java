package com.sinomed.repository;

import com.sinomed.entity.CustomerCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerCardRepository extends JpaRepository<CustomerCardEntity, Long> {

    /** 顾客持卡列表（新卡在前） */
    List<CustomerCardEntity> findByCustomerIdOrderByIdDesc(Long customerId);

    /** 可抵扣卡：有效且有余次，早发的先扣 */
    List<CustomerCardEntity> findByCustomerIdAndItemIdAndStatusAndRemainingTimesGreaterThanOrderByIdAsc(
            Long customerId, Long itemId, Integer status, Integer remainingTimes);

    /** 批量取多名顾客的持卡（疗程标记等聚合场景，一次拉齐避免逐顾客查询） */
    List<CustomerCardEntity> findByCustomerIdIn(List<Long> customerIds);
}
