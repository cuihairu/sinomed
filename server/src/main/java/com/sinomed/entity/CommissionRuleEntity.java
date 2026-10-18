package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 提成规则（美容业态 c，docs/design/salon-extension.md §2-c）：P0 固定比例「项目×岗位」。
 * 岗位取 staffs.title（与排班医师名册同一岗位口径）；医师/技师同表，不建并行实体。
 * P1 再上规则引擎（阶梯/多人分佣）。
 */
@Data
@Entity
@Table(name = "`commission_rules`", uniqueConstraints = {
        @UniqueConstraint(name = "uk_commission_rule_item_title", columnNames = {"item_id", "title"})
}, indexes = {
        @Index(name = "idx_commission_rule_item_id", columnList = "item_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CommissionRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 卡项 id（非空，items.id） */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 岗位（非空，staffs.title 精确匹配） */
    @Column(name = "title", nullable = false)
    private String title;

    /** 提成比例（整数百分比 0–100；金额 = 行小计 × ratio / 100，向下取整） */
    @Column(name = "ratio", nullable = false)
    private Integer ratio;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
