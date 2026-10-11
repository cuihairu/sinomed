package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 退款流水：一笔结算只能退一次（settlement_id 唯一索引兜底），退款 = 退整单、原路退回。
 * 口径见 docs/design/reconciliation.md：储值补回正数流水、次卡作废核销回补余次、
 * 微信/支付宝/现金为演示口径仅落退款记录；退款后订单 2 已完成 → 1 已接单，可重收。
 */
@Data
@Entity
@Table(name = "`refund_logs`", indexes = {
        @Index(name = "idx_refund_log_settlement_id", columnList = "settlement_id", unique = true),
        @Index(name = "idx_refund_log_order_id", columnList = "order_id")
})
@EntityListeners(AuditingEntityListener.class)
public class RefundLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 被退的结算单（一单一结算，唯一） */
    @Column(name = "settlement_id", nullable = false, unique = true)
    private Long settlementId;

    /** 订单 id（冗余自结算单，回退订单状态用） */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 退款金额（元，= 原结算实收；次卡抵扣为 0） */
    @Column(name = "money", nullable = false)
    private Integer money;

    /** 原支付方式冗余：1 储值 / 2 微信 / 3 支付宝 / 4 现金 / 5 次卡抵扣 */
    @Column(name = "pay_type", nullable = false)
    private Integer payType;

    /** 操作人 staff id（可空） */
    @Column(name = "staff_id")
    private Long staffId;

    /** 退款理由（≤100 字，必填——退款必须能答「为什么」） */
    @Column(name = "reason", nullable = false)
    private String reason;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
