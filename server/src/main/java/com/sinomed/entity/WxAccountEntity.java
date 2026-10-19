package com.sinomed.entity;

import com.sinomed.config.WxSecretAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 公众号账号（wx_accounts，设计稿 docs/design/wechat-official-account.md §4.1）。
 * 第一期单账号：回调验签 token 走 env（sinomed.official-account.token），本表驱动
 * account_type + verified 的能力矩阵与 enabled 账号开关；app_secret/aes_key 第二期落库加密。
 */
@Data
@Entity
@DynamicInsert
@Table(name = "wx_accounts", indexes = {
        @Index(name = "idx_wx_account_enabled", columnList = "enabled")
})
@EntityListeners(AuditingEntityListener.class)
public class WxAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** AppID（唯一；第一期不参与验签，仅标识） */
    @Column(name = "app_id", unique = true)
    private String appId;

    /** AppSecret（第二期 AES 落库；第一期恒空） */
    // 加密落库（设计稿 §7 凭据不入库明文，AES-GCM 可逆，密钥 env WX_OA_CREDENTIAL_KEY）
    @Convert(converter = WxSecretAttributeConverter.class)
    @Column(name = "app_secret")
    private String appSecret;

    @Column(name = "account_name")
    private String accountName;

    /** service 服务号 / subscription 订阅号（能力矩阵两字段之一） */
    @Column(name = "account_type", columnDefinition = "varchar(16) default 'service'")
    private String accountType;

    /** 微信认证：1 已认证 / 0 未认证（能力矩阵两字段之二） */
    @Column(name = "verified", nullable = false, columnDefinition = "integer default 0")
    private Integer verified;

    /** 消息推送 Token（第一期走 env，本列第二期启用） */
    @Column(name = "token")
    private String token;

    /** EncodingAESKey（43 字符；第二期加密落库） */
    @Convert(converter = WxSecretAttributeConverter.class)
    @Column(name = "aes_key")
    private String aesKey;

    /** plaintext 明文 / compatible 兼容 / safe 安全（第一期仅 plaintext 实施） */
    @Column(name = "encrypt_mode", columnDefinition = "varchar(16) default 'plaintext'")
    private String encryptMode;

    /** 账号开关：1 启用 / 0 停用（停用后回调拒处理回 success） */
    @Column(name = "enabled", nullable = false, columnDefinition = "integer default 1")
    private Integer enabled;

    @Column(name = "remark")
    private String remark;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
