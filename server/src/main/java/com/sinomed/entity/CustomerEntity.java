package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Data
@Entity
@DynamicInsert
@Table(name = "`customers`",indexes = {
        @Index(name = "idx_customer_name",columnList = "name"),
        @Index(name = "idx_customer_phone",columnList = "phone"),
        @Index(name = "idx_customer_level",columnList = "level"),
        @Index(name = "idx_customer_birthday",columnList = "birthday"),
        @Index(name = "idx_customer_openid",columnList = "openid")
})
@EntityListeners(AuditingEntityListener.class)
public class CustomerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",nullable = false)
    private Long id;
    @Column(name = "name",nullable = false)
    private String name;
    @Column(name = "age")
    private Integer age;
    @Column(name = "level")
    private Integer level;
    @Column(name = "phone",unique = true)
    private String phone;
    /** 小程序 openid（登录态身份绑定，可空）；phone-lookup 的 openid 路径按它查卡，裸手机号查卡不做（2026-10-14 裁定） */
    @Column(name = "openid",unique = true)
    private String openid;
    @Column(name = "gender")
    private Integer gender;//0 女 1 男
    @Column(name = "address")
    private String address;
    @Column(name = "birthday")
    private Date birthday;

    // @DynamicInsert：insert 省略 null 列、落库走列默认（beauty_consent→0）。
    //  同 ItemEntity 纪律：种子路径（测试夹具、直建实体）不必逐字段 set。
    /** 美容敏感信息单独同意（美容业态 d，PIPL 口径，docs/design/salon-extension.md §4）：
     *  1 已同意 / 0 未同意（缺省）；随同意接口记录时刻与经办员工 */
    @Column(name = "beauty_consent", nullable = false, columnDefinition = "integer default 0")
    private Integer beautyConsent;

    /** 同意时刻（同意接口写入） */
    @Column(name = "beauty_consent_time")
    private Date beautyConsentTime;

    /** 经办员工 id（同意接口写入） */
    @Column(name = "beauty_consent_staff_id")
    private Long beautyConsentStaffId;
    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;
    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;
}
