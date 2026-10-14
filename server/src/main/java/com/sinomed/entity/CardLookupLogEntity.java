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
 * 手机号查卡审计日志：每次查卡尝试（含拒绝）都落一条——查了谁的卡（phone/customerId）、
 * 谁查的（operator：openid 或手机号）、什么模式（mode）、结果（result）、时间（createTime）。
 * 口径见 docs/design/kiosk.md 与 2026-10-14 裁定单：openid 与短信两条路径都走审计。
 */
@Data
@Entity
@Table(name = "card_lookup_logs", indexes = {
        @Index(name = "idx_card_lookup_log_phone", columnList = "phone"),
        @Index(name = "idx_card_lookup_log_operator", columnList = "operator")
})
@EntityListeners(AuditingEntityListener.class)
public class CardLookupLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** 被查卡项所属顾客手机号（审计：查了谁的卡；openid 路径可为空） */
    @Column(name = "phone")
    private String phone;

    /** 查询者身份（审计：谁查的）：openid 路径=openid，短信路径=手机号 */
    @Column(name = "operator")
    private String operator;

    /** 生效模式：off / openid_only / sms_verified */
    @Column(name = "mode")
    private String mode;

    /** 命中的顾客 id（未命中为空） */
    @Column(name = "customer_id")
    private Long customerId;

    /** 结果：ok / disabled / missing_openid / no_binding / missing_code / bad_code / expired_code / used_code / no_customer */
    @Column(name = "result")
    private String result;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;
}
