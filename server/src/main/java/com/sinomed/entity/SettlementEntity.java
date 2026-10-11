package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Data
@Entity
@Table(name = "`settlements`", indexes = {
        @Index(name = "idx_settlement_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class SettlementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * 结算的订单。退费后订单回退可重收，一单可有多张结算单（原 order_id 唯一索引
     * 已移除，老库由 SettlementIndexMigration 启动时 DROP，见 reconciliation.md）；
     * 防重复收款由 settle() 校验「最近一张结算单未退款」兜底。
     */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 顾客 id（冗余自订单，便于按顾客对账） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 支付方式：1 储值 / 2 微信 / 3 支付宝 / 4 现金 */
    @Column(name = "pay_type", nullable = false)
    private Integer payType;

    /** 实收金额（元，取订单价格快照） */
    @Column(name = "money", nullable = false)
    private Integer money;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
