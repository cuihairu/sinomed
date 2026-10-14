package com.sinomed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 短信验证码（phone-lookup 短信路径的双因子凭证）：发码落库、5 分钟有效、一次性。
 * 本系统无真实短信网关，发码为服务端桩（代码只落库与日志，不对外泄露）——与 web 端假验证码同口径。
 */
@Data
@Entity
@Table(name = "sms_codes")
@EntityListeners(AuditingEntityListener.class)
public class SmsCodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "code", nullable = false)
    private String code;

    /** 过期时间（发码后 5 分钟） */
    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

    /** 0 未用 / 1 已用（一次性） */
    @Column(name = "used", nullable = false, columnDefinition = "integer default 0")
    private Integer used;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;
}
