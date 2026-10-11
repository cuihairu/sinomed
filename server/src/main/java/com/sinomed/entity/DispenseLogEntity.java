package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 发药流水：单向追加（药房确认发药，或代煎/膏方领取自动带出）。
 * 口径见 docs/design/dispensing.md：发药只确认「药交出去了」，不扣库存、不涉退款。
 */
@Data
@Entity
@Table(name = "`dispense_logs`", indexes = {
        @Index(name = "idx_dispense_log_prescription_id", columnList = "prescription_id")
})
@EntityListeners(AuditingEntityListener.class)
public class DispenseLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 处方 id */
    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    /** 动作：1 发药（本稿只有一种） */
    @Column(name = "action", nullable = false)
    private Integer action;

    /** 操作人 staff id（自动带出为空；可空） */
    @Column(name = "staff_id")
    private Long staffId;

    /** 备注：自动带出时写明来源（代煎领取自动发药/膏方领取自动发药）；手动可空，≤100 字 */
    @Column(name = "note")
    private String note;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
