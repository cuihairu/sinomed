package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 提成记录（美容业态 c）：结算时按 order_items 明细行 × 规则比例生成，insert-only 记账。
 * 业绩归属锚点 orders.staff_id；itemName/subtotal/ratio 为结算时刻快照。
 * 退单冲销：写等额负向记录（amount 取负、remark「退单冲销」），净额归零，不删原记录。
 * 提成按业绩（行小计）非实收——次卡抵扣实收 0 也照记（技师不做白工）。
 */
@Data
@Entity
@Table(name = "`commission_records`", indexes = {
        @Index(name = "idx_commission_record_order_id", columnList = "order_id"),
        @Index(name = "idx_commission_record_settlement_id", columnList = "settlement_id"),
        @Index(name = "idx_commission_record_staff_id", columnList = "staff_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CommissionRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 订单 id（非空） */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 生成它的结算单 id（非空；冲销记录同结算单） */
    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    /** 订单明细行 id（非空；存量单无明细行不记账） */
    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    /** 员工 id（非空，orders.staff_id） */
    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    /** 卡项 id */
    @Column(name = "item_id")
    private Long itemId;

    /** 卡项名快照 */
    @Column(name = "item_name")
    private String itemName;

    /** 行小计快照（元） */
    @Column(name = "subtotal")
    private Integer subtotal;

    /** 比例快照（整数百分比） */
    @Column(name = "ratio")
    private Integer ratio;

    /** 提成金额（元；退单冲销为负） */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** 备注（退单冲销记「退单冲销 #结算id」） */
    @Column(name = "remark")
    private String remark;

    /** insert-only 记账，只记创建时间 */
    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;
}
