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
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 粉丝绑定审计（wx_binding_logs，设计稿 §4.8）：沿用 card_lookup_logs 口径，每次尝试（含拒绝）落一条。
 * 不共用 card_lookup_logs——那是「查卡」语义，这里是「绑定」语义，混表会让 result 枚举膨胀且查询意图不清。
 */
@Data
@Entity
@DynamicInsert
@Table(name = "wx_binding_logs", indexes = {
        @Index(name = "idx_wx_binding_fan", columnList = "fan_openid"),
        @Index(name = "idx_wx_binding_phone", columnList = "phone")
})
@EntityListeners(AuditingEntityListener.class)
public class WxBindingLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    /** 发起绑定的粉丝 openid（缺参时为 null） */
    @Column(name = "fan_openid")
    private String fanOpenid;

    /** 被绑定顾客手机号（审计：绑了谁） */
    @Column(name = "phone")
    private String phone;

    /** 命中的顾客 id（未命中为空） */
    @Column(name = "customer_id")
    private Long customerId;

    /** ok / missing_code / bad_code / expired_code / used_code / no_customer / bound_other /
     *  conflict（设计稿 §4.8）+ 本批补：disabled（模块关）/ account_missing / missing_openid /
     *  unbind / no_fan / not_bound（解绑路径审计值） */
    @Column(name = "result", columnDefinition = "varchar(32) default 'ok'")
    private String result;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;
}
