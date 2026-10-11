package com.sinomed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 顾客持卡（次卡）：顾客 × 卡项的次数持有。发卡即全量次数，抵扣减 remaining_times；
 * status：1 有效 / 0 停用。valid_until 可空=长期有效，过期卡不参与抵扣（口径见 docs/server/data-model.md）。
 */
@Data
@Entity
@Table(name = "`customer_cards`", indexes = {
        @Index(name = "idx_card_customer", columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CustomerCardEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 卡项（items 表，名称带「次卡」的服务项目） */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "total_times", nullable = false)
    private Integer totalTimes;

    @Column(name = "remaining_times", nullable = false)
    private Integer remainingTimes;

    /** 1 有效 / 0 停用。注意不写字段初始化器（与其他实体同口径） */
    @Column(name = "status", nullable = false, columnDefinition = "integer default 1")
    private Integer status;

    /** 发卡来源订单（可空，前台手工发卡为空） */
    @Column(name = "source_order_id")
    private Long sourceOrderId;

    /** 有效期至（可空=长期有效），过期卡不参与抵扣 */
    @Column(name = "valid_until")
    private Date validUntil;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
