package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 订单明细行：一笔订单可含多个卡项与数量（原型 admin-orders「艾灸调理 ×1、腹部推拿 ×1」）。
 * name/price 是下单时刻快照，卡项改名/调价不影响历史单据。
 */
@Data
@Entity
@Table(name = "order_item", indexes = {
        @Index(name = "idx_order_item_order_id", columnList = "order_id")
})
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 卡项名快照 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 单价快照（元） */
    @Column(name = "price")
    private Integer price;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    /** 小计 = 单价 × 数量（元） */
    @Column(name = "subtotal")
    private Integer subtotal;
}
