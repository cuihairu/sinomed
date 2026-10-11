package com.sinomed.repository;

import com.sinomed.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {

    /** 单订单明细（按行序） */
    List<OrderItemEntity> findByOrderIdOrderByIdAsc(Long orderId);

    /** 批量取明细（订单分页联行用） */
    List<OrderItemEntity> findByOrderIdIn(Collection<Long> orderIds);

    /** 删单时清行 */
    void deleteByOrderId(Long orderId);
}
