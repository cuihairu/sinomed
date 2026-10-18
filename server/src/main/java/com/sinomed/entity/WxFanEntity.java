package com.sinomed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 公众号粉丝（wx_fans，设计稿 §4.2/§4.7）：openid 是**公众号体系** openid，
 * 与 customers.openid（小程序 openid）不同体系值不同，经 customer_id 桥接顾客档案；
 * 取关保留行（subscribe=0）供回归分析，不自动解绑。
 */
@Data
@Entity
@DynamicInsert
@Table(name = "wx_fans",
        uniqueConstraints = @UniqueConstraint(name = "uk_wx_fan_account_openid", columnNames = {"account_id", "openid"}),
        indexes = {
                @Index(name = "idx_wx_fan_customer", columnList = "customer_id"),
                @Index(name = "idx_wx_fan_unionid", columnList = "unionid")
        })
@EntityListeners(AuditingEntityListener.class)
public class WxFanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** 公众号 openid（同账号内唯一） */
    @Column(name = "openid", nullable = false)
    private String openid;

    /** 开放平台 unionid（可空；跨端自动关联留第三期） */
    @Column(name = "unionid")
    private String unionid;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "head_img_url")
    private String headImgUrl;

    /** 1 已关注 / 0 已取关（取关保留行） */
    @Column(name = "subscribe", nullable = false, columnDefinition = "integer default 0")
    private Integer subscribe;

    @Column(name = "subscribe_time")
    private Date subscribeTime;

    @Column(name = "unsubscribe_time")
    private Date unsubscribeTime;

    /** 绑定的顾客档案 id（可空；第一期经 H5 双因子绑定，第五交付物） */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "remark")
    private String remark;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
