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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 持卡调整流水（疗程卡营销动作留痕）：type 1 赠次 / 2 转卡。
 * 赠次：total/remaining 各 +times；转卡：customer_id 换人（有效期/余次照旧）。
 * reason 必填 ≤100 字；staff_id 为操作人（取当前登录账号对应员工，查无留空）。
 */
@Data
@Entity
@Table(name = "`card_adjusts`", indexes = {
        @Index(name = "idx_card_adjust_card", columnList = "card_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CardAdjustEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    /** 1 赠次 / 2 转卡 */
    @Column(name = "type", nullable = false)
    private Integer type;

    /** 赠次 N（1-99）；转卡记 0 */
    @Column(name = "times", nullable = false)
    private Integer times;

    /** 转卡后归属顾客（type=2 必填；赠次为空） */
    @Column(name = "customer_id")
    private Long customerId;

    /** 调整理由（必填 ≤100 字） */
    @Column(name = "reason", nullable = false)
    private String reason;

    /** 操作人（员工，可空） */
    @Column(name = "staff_id")
    private Long staffId;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;
}
